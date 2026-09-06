<?php

/**
 * 速率限制（APCu 优先，数据库降级）
 *
 * - 优先使用 APCu 内存计数器，适合百人以上并发
 * - APCu 不可用时降级为数据库方案
 * - 数据库表通过 migrate.php 预先创建，请求内不再执行 DDL
 *
 * rateLimit('login', 5, 60) — 同一 IP 每 60 秒最多 5 次
 */

/**
 * 检查并记录速率限制
 *
 * @param string $key          限流标识（如 'login', 'reset_password'）
 * @param int    $maxAttempts  窗口内最大请求数
 * @param int    $windowSec    时间窗口（秒）
 * @return bool true=允许, false=超过限制
 */
function rateLimit(string $key, int $maxAttempts, int $windowSec): bool {
  $ip = $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1';

  if (function_exists('apcu_enabled') && apcu_enabled()) {
    return rateLimitApcu($ip, $key, $maxAttempts, $windowSec);
  }

  return rateLimitDb($ip, $key, $maxAttempts, $windowSec);
}

/**
 * APCu 内存计数器（高并发首选）
 */
function rateLimitApcu(string $ip, string $key, int $maxAttempts, int $windowSec): bool {
  // 计数与窗口起点分开存储：计数用纯整数，配合 apcu_inc 原子递增；
  // 窗口起点单独存时间戳，用于判断窗口是否过期。
  // 修复：旧实现把 "count|windowStart" 拼成字符串，apcu_inc 无法对字符串自增，导致限流形同虚设。
  $countKey = "rl:{$ip}:{$key}:count";
  $startKey = "rl:{$ip}:{$key}:start";
  $now = time();
  $windowStart = $now - $windowSec;

  $storedStart = apcu_fetch($startKey);

  // 首次请求或窗口已过期：重置窗口起点并计数为 1
  if ($storedStart === false || (int)$storedStart < $windowStart) {
    apcu_store($startKey, $now, $windowSec);
    apcu_store($countKey, 1, $windowSec);
    return true;
  }

  // 原子递增计数；apcu_inc 返回自增后的新值
  $newCount = apcu_inc($countKey, 1, $success, $windowSec);
  if ($success === false) {
    // 计数键异常被清理：重置为 1 并放行本次（限流故障时偏向放行，避免阻塞业务）
    apcu_store($countKey, 1, $windowSec);
    return true;
  }

  return $newCount <= $maxAttempts;
}

/**
 * 数据库降级方案（APCu 不可用时使用）
 */
function rateLimitDb(string $ip, string $key, int $maxAttempts, int $windowSec): bool {
  $now = now_ms();
  $windowStart = $now - ($windowSec * 1000);

  $pdo = db();

  // 用事务包裹“清理-计数-插入”，降低并发绕过风险；限流故障时放行避免阻塞业务
  $pdo->beginTransaction();
  try {
    // 清理所有过期记录（不限定 key），避免 rate_limits 表无限膨胀
    $stmt = $pdo->prepare("DELETE FROM rate_limits WHERE hit_at < ?");
    $stmt->execute([$windowStart]);

    // 统计窗口内请求数
    $stmt = $pdo->prepare("SELECT COUNT(*) AS cnt FROM rate_limits WHERE ip = ? AND `key` = ? AND hit_at >= ?");
    $stmt->execute([$ip, $key, $windowStart]);
    $row = $stmt->fetch();
    $count = (int)($row['cnt'] ?? 0);

    if ($count >= $maxAttempts) {
      $pdo->commit();
      return false;
    }

    // 记录本次请求
    $stmt = $pdo->prepare("INSERT INTO rate_limits (ip, `key`, hit_at) VALUES (?, ?, ?)");
    $stmt->execute([$ip, $key, $now]);

    $pdo->commit();
    return true;
  } catch (Exception $e) {
    if ($pdo->inTransaction()) {
      $pdo->rollBack();
    }
    logError('rate_limit db', $e);
    return true;
  }
}

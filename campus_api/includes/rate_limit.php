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
  $cacheKey = "rl:{$ip}:{$key}";
  $now = time();
  $windowStart = $now - $windowSec;

  // 使用 APCu key + TTL 实现滑动窗口
  // 格式: "count|windowStart"
  $current = apcu_fetch($cacheKey);
  if ($current === false) {
    apcu_store($cacheKey, "1|{$now}", $windowSec);
    return true;
  }

  $parts = explode('|', $current);
  $count = (int)($parts[0] ?? 1);
  $storedWindowStart = (int)($parts[1] ?? $now);

  // 如果窗口已过期，重置计数
  if ($storedWindowStart < $windowStart) {
    apcu_store($cacheKey, "1|{$now}", $windowSec);
    return true;
  }

  if ($count >= $maxAttempts) {
    return false;
  }

  // 原子递增
  apcu_inc($cacheKey, 1, $success, $windowSec);
  return true;
}

/**
 * 数据库降级方案（APCu 不可用时使用）
 */
function rateLimitDb(string $ip, string $key, int $maxAttempts, int $windowSec): bool {
  $now = now_ms();
  $windowStart = $now - ($windowSec * 1000);

  $pdo = db();

  // 清理过期记录
  $stmt = $pdo->prepare("DELETE FROM rate_limits WHERE ip = ? AND `key` = ? AND hit_at < ?");
  $stmt->execute([$ip, $key, $windowStart]);

  // 统计窗口内请求数
  $stmt = $pdo->prepare("SELECT COUNT(*) AS cnt FROM rate_limits WHERE ip = ? AND `key` = ? AND hit_at >= ?");
  $stmt->execute([$ip, $key, $windowStart]);
  $row = $stmt->fetch();
  $count = (int)($row['cnt'] ?? 0);

  if ($count >= $maxAttempts) {
    return false;
  }

  // 记录本次请求
  $stmt = $pdo->prepare("INSERT INTO rate_limits (ip, `key`, hit_at) VALUES (?, ?, ?)");
  $stmt->execute([$ip, $key, $now]);

  return true;
}

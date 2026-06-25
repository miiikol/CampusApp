<?php

/**
 * 基于数据库的 IP 速率限制（无需 Redis）
 *
 * rateLimit('login', 5, 60) — 同一 IP 每 60 秒最多 5 次
 */

function ensureRateLimitTable(): void {
  db()->exec("
    CREATE TABLE IF NOT EXISTS rate_limits (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      ip VARCHAR(64) NOT NULL,
      `key` VARCHAR(64) NOT NULL,
      hit_at BIGINT NOT NULL,
      INDEX idx_ip_key (ip, `key`, hit_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  ");
}

/**
 * 检查并记录速率限制
 *
 * @param string $key          限流标识（如 'login', 'reset_password'）
 * @param int    $maxAttempts  窗口内最大请求数
 * @param int    $windowSec    时间窗口（秒）
 * @return bool true=允许, false=超过限制
 */
function rateLimit(string $key, int $maxAttempts, int $windowSec): bool {
  ensureRateLimitTable();

  $ip = $_SERVER['REMOTE_ADDR'] ?? '127.0.0.1';
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

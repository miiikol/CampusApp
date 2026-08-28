<?php

/**
 * 接口缓存层（APCu 优先，无可降级为空操作）
 *
 * 用途：
 *   - 对高频读取接口（资讯列表、二手市场、失物招领）做短期缓存
 *   - 写操作（发布、评论、收藏等）主动清除相关缓存
 *
 * 使用方式：
 *   $data = cacheGet('/news:page=1&size=20');
 *   if ($data !== null) respond(200, $data);
 *   // ... 查询数据库 ...
 *   cacheSet('/news:page=1&size=20', $result, 60); // 缓存 60 秒
 */

/**
 * 缓存是否可用
 */
function cacheAvailable(): bool {
  return function_exists('apcu_enabled') && apcu_enabled();
}

/**
 * 读取缓存，不存在返回 null
 * @return mixed
 */
function cacheGet(string $key) {
  if (!cacheAvailable()) return null;

  $entry = apcu_fetch('c:' . $key);
  if ($entry === false) return null;

  // 格式: [data, expiresAt]
  if (!is_array($entry) || count($entry) !== 2) return null;

  $data = $entry[0];
  $expiresAt = (int)$entry[1];

  if ($expiresAt > 0 && time() > $expiresAt) {
    apcu_delete('c:' . $key);
    return null;
  }

  return $data;
}

/**
 * 写入缓存
 *
 * @param string $key       缓存键
 * @param mixed  $data      缓存数据（必须可序列化）
 * @param int    $ttlSec    有效期（秒），0 表示永不过期
 */
function cacheSet(string $key, $data, int $ttlSec = 60): void {
  if (!cacheAvailable()) return;

  $expiresAt = $ttlSec > 0 ? time() + $ttlSec : 0;
  apcu_store('c:' . $key, [$data, $expiresAt], $ttlSec > 0 ? $ttlSec : 0);
}

/**
 * 删除缓存
 */
function cacheDelete(string $key): void {
  if (!cacheAvailable()) return;
  apcu_delete('c:' . $key);
}

/**
 * 按前缀批量删除（用于写操作后清除相关缓存）
 */
function cacheDeleteByPrefix(string $prefix): void {
  if (!cacheAvailable()) return;

  $iterator = new APCUIterator('/^c:' . preg_quote($prefix, '/') . '/');
  foreach ($iterator as $entry) {
    apcu_delete($entry['key']);
  }
}

/**
 * 构建缓存键（自动包含分页参数）
 */
function cacheKey(string $prefix, array $params = []): string {
  $key = $prefix;
  if (!empty($params)) {
    ksort($params);
    $key .= ':' . http_build_query($params);
  }
  return $key;
}

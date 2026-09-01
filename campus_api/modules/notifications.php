<?php

/**
 * 通知模块：通知列表与全部已读
 */

// 通知列表：按 sinceId 增量拉取，过滤历史占位通知
if ($method === 'GET' && $path === '/notifications') {
  $userId = trim((string)($_GET['userId'] ?? ''));

  // 强制 Token 鉴权
  $auth = authenticate();
  if ($auth['userId'] !== $userId) {
    respond(403, ['message' => '无权查看他人的通知']);
  }
  $sinceId = isset($_GET['sinceId']) ? (int)$_GET['sinceId'] : 0;
  $limit = isset($_GET['limit']) ? (int)$_GET['limit'] : 50;
  if ($userId === '') {
    respond(400, ['message' => 'userId 不能为空']);
  }
  if ($limit <= 0) $limit = 50;
  if ($limit > 200) $limit = 200;

  $stmt = db()->prepare("
    SELECT id, user_id, type, title, content, related_type, related_id, is_read, created_at
    FROM notifications
    WHERE user_id = ? AND id > ?
      AND NOT (type = 'COMMENT_LIKE' AND content LIKE '点赞来自用户：%')
    ORDER BY id ASC
    LIMIT ?
  ");
  $stmt->execute([$userId, $sinceId, $limit]);
  $rows = $stmt->fetchAll();
  $out = array_map(function($r) {
    return [
      'id' => (string)$r['id'],
      'userId' => (string)$r['user_id'],
      'type' => (string)$r['type'],
      'title' => (string)$r['title'],
      'content' => (string)$r['content'],
      'relatedType' => $r['related_type'] !== null ? (string)$r['related_type'] : null,
      'relatedId' => $r['related_id'] !== null ? (string)$r['related_id'] : null,
      'isRead' => ((int)$r['is_read']) === 1,
      'createdAt' => (int)$r['created_at']
    ];
  }, $rows);
  respond(200, $out);
}

// 全部已读：将当前用户所有未读通知标记为已读
if ($method === 'POST' && $path === '/notifications/read-all') {
  $body = jsonBody();
  $userId = trim((string)($body['userId'] ?? ''));

  // 强制 Token 鉴权
  $auth = authenticate();
  if ($auth['userId'] !== $userId) {
    respond(403, ['message' => '无权操作']);
  }
  if ($userId === '') {
    respond(400, ['message' => 'userId 不能为空']);
  }
  $stmt = db()->prepare("UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0");
  $stmt->execute([$userId]);
  respond(200, ['message' => 'ok']);
}

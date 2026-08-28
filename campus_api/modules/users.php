<?php

if ($method === 'POST' && preg_match('#^/users/([^/]+)/profile$#', $path, $matches)) {
  $routeUserId = (string)$matches[1];

  // 强制 Token 鉴权
  $auth = authenticate();
  if ($auth['userId'] !== $routeUserId) {
    respond(403, ['message' => '无权操作此用户']);
  }

  if (isBuiltinAdminId($routeUserId)) {
    respond(400, ['message' => '管理员账号不支持修改资料']);
  }

  $body = jsonBody();
  $hasUsername = array_key_exists('username', $body);
  $hasAvatar = array_key_exists('avatarUrl', $body);

  if (!$hasUsername && !$hasAvatar) {
    respond(400, ['message' => 'username/avatarUrl 至少提供一个']);
  }

  $stmt0 = db()->prepare('SELECT id, student_id, username, role, avatar_url FROM users WHERE id = ? LIMIT 1');
  $stmt0->execute([$routeUserId]);
  $existing = $stmt0->fetch();
  if (!$existing) {
    respond(404, ['message' => '用户不存在']);
  }

  $updates = [];
  $params = [];
  if ($hasUsername) {
    $username = trim((string)($body['username'] ?? ''));
    if ($username === '') respond(400, ['message' => '昵称不能为空']);
    if (mb_strlen($username) > 20) respond(400, ['message' => '昵称过长']);
    $updates[] = 'username = ?';
    $params[] = $username;
  }

  if ($hasAvatar) {
    $avatarRaw = trim((string)($body['avatarUrl'] ?? ''));
    $avatarUrl = $avatarRaw === '' ? null : $avatarRaw;
    if ($avatarUrl !== null && mb_strlen($avatarUrl) > 500) respond(400, ['message' => '头像地址过长']);
    $updates[] = 'avatar_url = ?';
    $params[] = $avatarUrl;
  }

  $params[] = $routeUserId;
  $stmt1 = db()->prepare('UPDATE users SET ' . implode(', ', $updates) . ' WHERE id = ?');
  $stmt1->execute($params);

  $stmt2 = db()->prepare('SELECT id, student_id, username, role, avatar_url FROM users WHERE id = ? LIMIT 1');
  $stmt2->execute([$routeUserId]);
  $u = $stmt2->fetch();
  if (!$u) respond(500, ['message' => '服务器错误']);

  respond(200, [
    'id' => (string)$u['id'],
    'username' => (string)$u['username'],
    'studentId' => (string)$u['student_id'],
    'avatarUrl' => $u['avatar_url'] !== null ? (string)$u['avatar_url'] : null,
    'token' => null,
    'role' => $u['role'] ?: 'student'
  ]);
}

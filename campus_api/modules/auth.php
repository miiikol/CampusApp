<?php

if ($method === 'POST' && $path === '/auth/login') {
  if (!rateLimit('login', 10, 60)) {
    respond(429, ['message' => '请求过于频繁，请稍后再试']);
  }

  ensureDemoContentSeeded();
  $body = jsonBody();
  $studentId = trim($body['studentId'] ?? '');
  $password  = (string)($body['password'] ?? '');

  if ($studentId === '' || $password === '') {
    respond(400, ['message' => 'studentId/password 不能为空']);
  }

  if (isBuiltinAdminCredential($studentId, $password)) {
    $token = jwt_generate(TEST_ADMIN_ID, 'admin');
    respond(200, [
      'id' => TEST_ADMIN_ID,
      'username' => TEST_ADMIN_NAME,
      'studentId' => TEST_ADMIN_STUDENT_ID,
      'avatarUrl' => null,
      'token' => $token,
      'role' => 'admin'
    ]);
  }

  $stmt = db()->prepare('SELECT id, student_id, username, password_hash, role, avatar_url FROM users WHERE student_id = ? LIMIT 1');
  $stmt->execute([$studentId]);
  $user = $stmt->fetch();

  if (!$user || !password_verify($password, $user['password_hash'])) {
    respond(401, ['message' => '学号或密码错误']);
  }

  $role = normalizeRole($user['role'] ?? 'student');
  $token = jwt_generate((string)$user['id'], $role);
  // 兼容旧 client：也写入 auth_tokens
  $expiresAt = now_ms() + 7 * 24 * 60 * 60 * 1000;
  $stmt2 = db()->prepare('INSERT INTO auth_tokens (token, user_id, expires_at, created_at) VALUES (?, ?, ?, ?)');
  $stmt2->execute([$token, $user['id'], $expiresAt, now_ms()]);

  respond(200, [
    'id' => (string)$user['id'],
    'username' => $user['username'],
    'studentId' => $user['student_id'],
    'avatarUrl' => $user['avatar_url'],
    'token' => $token,
    'role' => $role
  ]);
}

if ($method === 'POST' && $path === '/auth/forgot-password') {
  respond(200, ['message' => '请在客户端填写学号、姓名和身份证号后直接重置密码']);
}

if ($method === 'POST' && $path === '/auth/reset-password') {
  if (!rateLimit('reset_password', 5, 300)) {
    respond(429, ['message' => '请求过于频繁，请稍后再试']);
  }

  ensureDemoContentSeeded();
  $body = jsonBody();
  $studentId = trim($body['studentId'] ?? '');
  $fullName = normalizeName($body['fullName'] ?? '');
  $idCardNo = normalizeIdCard($body['idCardNo'] ?? '');
  $newPassword = (string)($body['newPassword'] ?? '');

  if ($studentId === '' || $fullName === '' || $idCardNo === '' || $newPassword === '') {
    respond(400, ['message' => 'studentId/fullName/idCardNo/newPassword 不能为空']);
  }

  if (strlen($newPassword) < 6) {
    respond(400, ['message' => '密码长度至少 6 位']);
  }

  if (!preg_match('/^\d{17}[\dX]$/', $idCardNo)) {
    respond(400, ['message' => '身份证号格式不正确']);
  }

  $stmt = db()->prepare('SELECT id, full_name, id_card_no FROM users WHERE student_id = ? LIMIT 1');
  $stmt->execute([$studentId]);
  $user = $stmt->fetch();
  if (!$user) {
    respond(401, ['message' => '身份信息不匹配']);
  }

  $dbName = normalizeName($user['full_name'] ?? '');
  $dbIdCard = normalizeIdCard($user['id_card_no'] ?? '');
  if ($dbName !== $fullName || $dbIdCard !== $idCardNo) {
    respond(401, ['message' => '身份信息不匹配']);
  }

  $pdo = db();
  $pdo->beginTransaction();
  try {
    $newHash = password_hash($newPassword, PASSWORD_DEFAULT);
    $stmt3 = $pdo->prepare('UPDATE users SET password_hash = ? WHERE id = ?');
    $stmt3->execute([$newHash, (string)$user['id']]);

    $pdo->commit();
  } catch (Exception $e) {
    $pdo->rollBack();
    logError('auth reset-password', $e);
    respond(500, ['message' => '服务器错误']);
  }

  respond(200, ['message' => '密码已重置，请使用新密码登录']);
}

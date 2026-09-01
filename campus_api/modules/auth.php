<?php

/**
 * 认证模块：登录、忘记密码、重置密码
 */

// 登录：校验学号密码（含内置管理员），成功后签发 JWT
if ($method === 'POST' && $path === '/auth/login') {
  // 登录限流：同一 IP 每分钟最多 10 次
  if (!rateLimit('login', 10, 60)) {
    respond(429, ['message' => '请求过于频繁，请稍后再试']);
  }

  // 仅在种子数据模块已加载时播种演示内容，避免 SESSION_SEED=0 时调用未定义函数
  if (function_exists('ensureDemoContentSeeded')) {
    ensureDemoContentSeeded();
  }
  $body = jsonBody();
  $studentId = trim($body['studentId'] ?? '');
  $password  = (string)($body['password'] ?? '');

  if ($studentId === '' || $password === '') {
    respond(400, ['message' => 'studentId/password 不能为空']);
  }

  // 内置管理员账号直接签发 Token，无需查库
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

  // 用 password_verify 校验哈希，避免明文比较
  if (!$user || !password_verify($password, $user['password_hash'])) {
    respond(401, ['message' => '学号或密码错误']);
  }

  $role = normalizeRole($user['role'] ?? 'student');
  $token = jwt_generate((string)$user['id'], $role);

  respond(200, [
    'id' => (string)$user['id'],
    'username' => $user['username'],
    'studentId' => $user['student_id'],
    'avatarUrl' => $user['avatar_url'],
    'token' => $token,
    'role' => $role
  ]);
}

// 忘记密码入口：提示客户端直接走重置流程
if ($method === 'POST' && $path === '/auth/forgot-password') {
  respond(200, ['message' => '请在客户端填写学号、姓名和身份证号后直接重置密码']);
}

// 重置密码：校验身份三要素（学号/姓名/身份证），通过后更新密码哈希
if ($method === 'POST' && $path === '/auth/reset-password') {
  // 重置密码限流：同一 IP 每 5 分钟最多 5 次
  if (!rateLimit('reset_password', 5, 300)) {
    respond(429, ['message' => '请求过于频繁，请稍后再试']);
  }

  // 仅在种子数据模块已加载时播种演示内容，避免 SESSION_SEED=0 时调用未定义函数
  if (function_exists('ensureDemoContentSeeded')) {
    ensureDemoContentSeeded();
  }
  $body = jsonBody();
  $studentId = trim($body['studentId'] ?? '');
  $fullName = normalizeName($body['fullName'] ?? '');
  $idCardNo = normalizeIdCard($body['idCardNo'] ?? '');
  $newPassword = (string)($body['newPassword'] ?? '');

  if ($studentId === '' || $fullName === '' || $idCardNo === '' || $newPassword === '') {
    respond(400, ['message' => 'studentId/fullName/idCardNo/newPassword 不能为空']);
  }

  if (strlen($newPassword) < 8) {
    respond(400, ['message' => '密码长度至少 8 位']);
  }
  if (!preg_match('/[A-Za-z]/', $newPassword) || !preg_match('/\d/', $newPassword)) {
    respond(400, ['message' => '密码必须同时包含字母和数字']);
  }

  // 身份证号格式校验：17 位数字 + 1 位数字或 X
  if (!preg_match('/^\d{17}[\dX]$/', $idCardNo)) {
    respond(400, ['message' => '身份证号格式不正确']);
  }

  $stmt = db()->prepare('SELECT id, full_name, id_card_hash FROM users WHERE student_id = ? LIMIT 1');
  $stmt->execute([$studentId]);
  $user = $stmt->fetch();
  if (!$user) {
    respond(401, ['message' => '身份信息不匹配']);
  }

  // 姓名与身份证必须同时一致，身份证哈希缺失时拒绝重置，防止身份要素降级被绕过
  $dbName = normalizeName($user['full_name'] ?? '');
  $dbCardHash = (string)($user['id_card_hash'] ?? '');
  if ($dbName !== $fullName || $dbCardHash === '' || $dbCardHash !== idCardHash($idCardNo)) {
    respond(401, ['message' => '身份信息不匹配']);
  }

  // 事务内重新哈希密码并更新
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

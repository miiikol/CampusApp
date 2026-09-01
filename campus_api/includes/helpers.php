<?php

/**
 * 通用工具函数
 *
 * 时间戳、错误日志、请求响应、鉴权辅助、分页、身份证哈希等公共能力。
 */

/**
 * 当前毫秒级时间戳
 */
function now_ms() {
  return (int) floor(microtime(true) * 1000);
}

/**
 * 记录错误日志（含自动轮转 + 7 天保留）
 *
 * 策略：
 *  - 按日期分文件，单文件超过 5MB 自动归档
 *  - 超过 7 天的错误日志自动删除
 */
function logError(string $message, ?Exception $e = null, array $context = []): void {
  $logDir = __DIR__ . DIRECTORY_SEPARATOR . '..' . DIRECTORY_SEPARATOR . 'logs';
  if (!is_dir($logDir)) {
    @mkdir($logDir, 0755, true);
  }

  $ts = date('Y-m-d H:i:s');
  $line = "[$ts] $message";
  if ($e !== null) {
    $line .= ' | ' . get_class($e) . ': ' . $e->getMessage()
           . ' in ' . $e->getFile() . ':' . $e->getLine();
  }
  if (!empty($context)) {
    $line .= ' | ' . json_encode($context, JSON_UNESCAPED_UNICODE);
  }
  $line .= PHP_EOL;

  $date = date('Y-m-d');
  $logFile = $logDir . DIRECTORY_SEPARATOR . 'error-' . $date . '.log';

  // 轮转：文件超过 5MB
  if (file_exists($logFile) && filesize($logFile) > 5 * 1024 * 1024) {
    $suffix = 1;
    do {
      $rotatedFile = $logDir . DIRECTORY_SEPARATOR . "error-{$date}-{$suffix}.log";
      $suffix++;
    } while (file_exists($rotatedFile) && $suffix < 100);
    @rename($logFile, $rotatedFile);
  }

  @file_put_contents($logFile, $line, FILE_APPEND | LOCK_EX);

  // 清理 7 天前日志（每次请求最多删除 10 个旧文件，避免耗时过长）
  cleanupOldLogs($logDir);

  // 同时输出到 PHP error_log
  error_log($line);
}

function cleanupOldLogs(string $logDir): void {
  $cutoff = time() - 7 * 86400;
  $count = 0;
  $items = @scandir($logDir);
  if ($items === false) return;

  foreach ($items as $item) {
    if ($count >= 10) break;
    $path = $logDir . DIRECTORY_SEPARATOR . $item;
    if (is_file($path) && pathinfo($item, PATHINFO_EXTENSION) === 'log') {
      $mtime = @filemtime($path);
      if ($mtime !== false && $mtime < $cutoff) {
        @unlink($path);
        $count++;
      }
    }
  }
}

/**
 * 解析请求体 JSON（php://input），非法 JSON 时返回空数组
 */
function jsonBody() {
  $raw = file_get_contents('php://input');
  if (!$raw) return [];
  $data = json_decode($raw, true);
  return is_array($data) ? $data : [];
}

/**
 * 输出 JSON 响应并终止请求
 */
function respond($code, $data) {
  http_response_code($code);
  echo json_encode($data, JSON_UNESCAPED_UNICODE);
  exit;
}

/**
 * 生成随机令牌（32 字节十六进制）
 */
function newToken() {
  return bin2hex(random_bytes(32));
}

/**
 * 规范化姓名（去首尾空白）
 */
function normalizeName($name) {
  return trim((string)$name);
}

/**
 * 规范化身份证号（转大写并去除空格）
 */
function normalizeIdCard($idCardNo) {
  $v = strtoupper(trim((string)$idCardNo));
  return str_replace(' ', '', $v);
}

// 内置管理员测试账号（仅用于演示/开发）
const TEST_ADMIN_ID = 'admin-demo';
const TEST_ADMIN_STUDENT_ID = '20269999';
const TEST_ADMIN_NAME = '管理员测试账号';

/**
 * 判断是否为内置管理员登录凭据。
 * 密码通过环境变量 ADMIN_PASSWORD 配置，避免明文写死源码；
 * 未配置时回退到默认演示密码（生产环境务必在 .env 中覆盖）。
 */
function isBuiltinAdminCredential($studentId, $password) {
  $adminPassword = env('ADMIN_PASSWORD', 'admin123456');
  return hash_equals(TEST_ADMIN_STUDENT_ID, (string)$studentId)
    && hash_equals($adminPassword, (string)$password);
}

/**
 * 判断是否为内置管理员 ID
 */
function isBuiltinAdminId($userId) {
  return trim((string)$userId) === TEST_ADMIN_ID;
}

/**
 * 规范化角色名（统一为 admin / student）
 */
function normalizeRole($role) {
  $role = strtolower(trim((string)$role));
  if ($role === 'admin' || $role === 'administrator' || $role === '管理员') return 'admin';
  return 'student';
}

/**
 * 根据用户 ID 获取其角色（内置管理员直接返回 admin）
 */
function getUserRoleById($userId) {
  $uid = trim((string)$userId);
  if ($uid === '') return 'student';
  if (isBuiltinAdminId($uid)) return 'admin';
  $stmt = db()->prepare('SELECT role FROM users WHERE id = ? LIMIT 1');
  $stmt->execute([$uid]);
  $row = $stmt->fetch();
  if (!$row) return 'student';
  return normalizeRole($row['role'] ?? 'student');
}

/**
 * 根据用户 ID 获取用户名（查不到返回空字符串）
 */
function getUsernameById($userId) {
  $uid = trim((string)$userId);
  if ($uid === '') return '';
  if (isBuiltinAdminId($uid)) return TEST_ADMIN_NAME;
  try {
    $stmt = db()->prepare('SELECT username FROM users WHERE id = ? LIMIT 1');
    $stmt->execute([$uid]);
    $row = $stmt->fetch();
    if ($row) {
      $name = trim((string)($row['username'] ?? ''));
      if ($name !== '') return $name;
    }
  } catch (Exception $e) {
    logError('getUsernameById failed', $e);
  }
  return '';
}

/**
 * 判断用户是否为管理员
 */
function isAdminUserId($userId) {
  return getUserRoleById($userId) === 'admin';
}

/**
 * 解析分页参数
 * @return array{ limit: int, offset: int, page: int, pageSize: int }
 */
function parsePagination(int $defaultSize = 20, int $maxSize = 200): array {
  $page = max(1, (int)($_GET['page'] ?? 1));
  // 限制最大页码，避免超大 offset 溢出为浮点数导致 SQL 异常
  $page = min($page, 100000);
  $pageSize = max(1, (int)($_GET['pageSize'] ?? $defaultSize));
  if ($pageSize > $maxSize) $pageSize = $maxSize;
  return [
    'limit'    => $pageSize,
    'offset'   => ($page - 1) * $pageSize,
    'page'     => $page,
    'pageSize' => $pageSize,
  ];
}

/**
 * 哈希身份证号（仅用于匹配校验，不可反推出原文）
 */
function idCardHash(string $idCardNo): string {
  return hash_hmac('sha256', strtoupper($idCardNo), env('JWT_SECRET', ''));
}

<?php

function now_ms() {
  return (int) floor(microtime(true) * 1000);
}

/**
 * 记录错误日志
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

  $logFile = $logDir . DIRECTORY_SEPARATOR . 'error-' . date('Y-m-d') . '.log';
  @file_put_contents($logFile, $line, FILE_APPEND | LOCK_EX);

  // 同时输出到 PHP error_log
  error_log($line);
}

function jsonBody() {
  $raw = file_get_contents('php://input');
  if (!$raw) return [];
  $data = json_decode($raw, true);
  return is_array($data) ? $data : [];
}

function respond($code, $data) {
  http_response_code($code);
  echo json_encode($data, JSON_UNESCAPED_UNICODE);
  exit;
}

function newToken() {
  return bin2hex(random_bytes(32));
}

function normalizeName($name) {
  return trim((string)$name);
}

function normalizeIdCard($idCardNo) {
  $v = strtoupper(trim((string)$idCardNo));
  return str_replace(' ', '', $v);
}

const TEST_ADMIN_ID = 'admin-demo';
const TEST_ADMIN_STUDENT_ID = '20269999';
const TEST_ADMIN_PASSWORD = 'admin123456';
const TEST_ADMIN_NAME = '管理员测试账号';

function isBuiltinAdminCredential($studentId, $password) {
  return $studentId === TEST_ADMIN_STUDENT_ID && $password === TEST_ADMIN_PASSWORD;
}

function isBuiltinAdminId($userId) {
  return trim((string)$userId) === TEST_ADMIN_ID;
}

function normalizeRole($role) {
  $role = strtolower(trim((string)$role));
  if ($role === 'admin' || $role === 'administrator' || $role === '管理员') return 'admin';
  return 'student';
}

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

function isAdminUserId($userId) {
  return getUserRoleById($userId) === 'admin';
}

/**
 * 解析分页参数
 * @return array{ limit: int, offset: int, page: int, pageSize: int }
 */
function parsePagination(int $defaultSize = 20, int $maxSize = 200): array {
  $page = max(1, (int)($_GET['page'] ?? 1));
  $pageSize = max(1, (int)($_GET['pageSize'] ?? $defaultSize));
  if ($pageSize > $maxSize) $pageSize = $maxSize;
  return [
    'limit'    => $pageSize,
    'offset'   => ($page - 1) * $pageSize,
    'page'     => $page,
    'pageSize' => $pageSize,
  ];
}

<?php
header('Content-Type: application/json; charset=utf-8');

// 加载环境变量（必须在其他模块之前）
require_once __DIR__ . '/config/env.php';
loadEnv(__DIR__);

// CORS 跨域处理
$corsOrigin = env('CORS_ALLOWED_ORIGIN', '');
if ($corsOrigin === '*') {
  header('Access-Control-Allow-Origin: *');
} elseif ($corsOrigin !== '') {
  $requestOrigin = $_SERVER['HTTP_ORIGIN'] ?? '';
  $allowed = array_map('trim', explode(',', $corsOrigin));
  if (in_array($requestOrigin, $allowed, true)) {
    header('Access-Control-Allow-Origin: ' . $requestOrigin);
    header('Access-Control-Allow-Credentials: true');
  }
}
header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
header('Access-Control-Allow-Headers: Content-Type, Authorization, X-Requested-With');
header('Access-Control-Max-Age: 86400');

// 预检请求直接返回
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
  http_response_code(204);
  exit;
}

// 调试模式：PHP 错误显示
if (env('APP_DEBUG', 'false') === 'true') {
  ini_set('display_errors', '1');
  error_reporting(E_ALL);
} else {
  ini_set('display_errors', '0');
  error_reporting(0);
}

// 请求耗时统计 + 日志
$GLOBALS['_request_start'] = microtime(true);
register_shutdown_function(function () {
  $elapsed = round((microtime(true) - $GLOBALS['_request_start']) * 1000, 2);
  $method = $_SERVER['REQUEST_METHOD'] ?? '?';
  $uri = $_SERVER['REQUEST_URI'] ?? '?';
  $status = http_response_code();

  // 仅记录慢请求、错误和非 GET 请求
  if ($elapsed > 1000 || $status >= 400 || $method !== 'GET') {
    logError("$method $uri -> $status ({$elapsed}ms)");
  }
});

$method = $_SERVER['REQUEST_METHOD'];
$uri = parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);

// 路径解析：支持 /campus_api/v1/xxx 和 /campus_api/xxx（向下兼容）
if (preg_match('#^/campus_api/v(\d+)#', $uri, $vMatch)) {
  $apiVersion = (int)$vMatch[1];
  $path = preg_replace('#^/campus_api/v\d+#', '', $uri);
  header('X-API-Version: ' . $apiVersion);
} else {
  $apiVersion = 1;
  $path = preg_replace('#^/campus_api#', '', $uri);
}
$path = rtrim($path, '/');
if ($path === '') $path = '/';

require_once __DIR__ . '/config/database.php';
require_once __DIR__ . '/includes/helpers.php';
require_once __DIR__ . '/includes/jwt.php';
require_once __DIR__ . '/includes/auth.php';
require_once __DIR__ . '/includes/rate_limit.php';
require_once __DIR__ . '/includes/storage.php';
require_once __DIR__ . '/includes/db_schema.php';
require_once __DIR__ . '/includes/seed_data.php';

if ($method === 'GET' && $path === '/') {
  respond(200, ['ok' => true, 'service' => 'campus_api']);
}

$modules = [
  'auth',
  'users',
  'upload',
  'courses',
  'news',
  'market',
  'lostfound',
  'notifications',
  'admin',
];

foreach ($modules as $module) {
  require __DIR__ . '/modules/' . $module . '.php';
}

respond(404, ['message' => 'Not Found', 'path' => $path, 'method' => $method]);

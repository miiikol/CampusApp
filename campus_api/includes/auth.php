<?php

/**
 * 鉴权中间件
 *
 * authenticate()       - 必须提供有效 Token，否则返回 401
 * authenticateOptional()- Token 可选，有则解析，无则返回 null
 */

function extractBearerToken(): ?string {
  $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
  if ($header === '') {
    // 兼容 Apache 某些配置
    $header = $_SERVER['REDIRECT_HTTP_AUTHORIZATION'] ?? '';
    if ($header === '') return null;
  }
  if (stripos($header, 'Bearer ') === 0) {
    return trim(substr($header, 7));
  }
  return null;
}

/**
 * 必须鉴权：从 Authorization Header 提取 Bearer Token，解析后返回 payload
 * 如果 Token 无效或缺失，直接响应 401 并终止
 *
 * @return array { sub: userId, role: string }
 */
function authenticate(): array {
  $token = extractBearerToken();
  if ($token === null) {
    respond(401, ['message' => '未提供认证令牌']);
  }

  $payload = jwt_decode($token);
  if ($payload === null) {
    respond(401, ['message' => '令牌无效或已过期']);
  }

  $userId = $payload['sub'] ?? '';
  if ($userId === '') {
    respond(401, ['message' => '令牌格式错误']);
  }

  return [
    'userId' => (string)$userId,
    'role'   => (string)($payload['role'] ?? 'student'),
  ];
}

/**
 * 可选鉴权：有 Token 则解析，无 Token 返回 null（用于公开接口）
 */
function authenticateOptional(): ?array {
  $token = extractBearerToken();
  if ($token === null) return null;

  $payload = jwt_decode($token);
  if ($payload === null) return null;

  $userId = $payload['sub'] ?? '';
  if ($userId === '') return null;

  return [
    'userId' => (string)$userId,
    'role'   => (string)($payload['role'] ?? 'student'),
  ];
}

/**
 * 鉴权 + 校验 userId 一致性：请求参数中的 userId 必须与 Token 主体一致
 */
function authenticateWithUser(string $requestUserId): array {
  $auth = authenticate();
  if ($auth['userId'] !== $requestUserId) {
    respond(403, ['message' => '无权操作此用户']);
  }
  return $auth;
}

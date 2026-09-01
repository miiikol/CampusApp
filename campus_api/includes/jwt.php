<?php

/**
 * JWT 工具（HMAC-SHA256，无外部依赖）
 */

/**
 * 编码 JWT：生成 header.payload.signature 三段式 Token
 */
function jwt_encode(array $payload): string {
  $secret = env('JWT_SECRET', '');
  $header = ['alg' => 'HS256', 'typ' => 'JWT'];

  $segments = [];
  // Header 与 Payload 段
  $segments[] = base64url(json_encode($header));
  $segments[] = base64url(json_encode($payload));

  $signingInput = implode('.', $segments);
  // 使用 HS256 计算签名
  $signature = hash_hmac('sha256', $signingInput, $secret, true);
  $segments[] = base64url($signature);

  return implode('.', $segments);
}

/**
 * 解码并校验 JWT：验证签名与过期时间，失败返回 null
 */
function jwt_decode(string $token): ?array {
  $secret = env('JWT_SECRET', '');
  if ($secret === '') return null;

  $parts = explode('.', $token);
  if (count($parts) !== 3) return null;

  [$headerB64, $payloadB64, $signatureB64] = $parts;

  $signature = base64url_decode($signatureB64);
  if ($signature === false) return null;

  $signingInput = "$headerB64.$payloadB64";
  $expected = hash_hmac('sha256', $signingInput, $secret, true);

  // 恒定时间比较签名，防止时序攻击
  if (!hash_equals($expected, $signature)) return null;

  $payload = json_decode(base64url_decode($payloadB64), true);
  if (!is_array($payload)) return null;

  // 检查过期
  if (isset($payload['exp']) && $payload['exp'] < time()) return null;

  return $payload;
}

/**
 * 签发登录 Token（含 sub、role、签发与过期时间）
 */
function jwt_generate(string $userId, string $role = 'student'): string {
  $expireHours = (int)env('JWT_EXPIRE_HOURS', '168');
  return jwt_encode([
    'sub' => $userId,
    'role' => $role,
    'iat' => time(),
    'exp' => time() + $expireHours * 3600,
  ]);
}

/**
 * Base64Url 编码（去掉填充，替换 +/ 为 -_）
 */
function base64url(string $data): string {
  return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}
/**
 * Base64Url 解码（补齐填充并还原 -_）
 */
function base64url_decode(string $data) {
  $remainder = strlen($data) % 4;
  if ($remainder) $data .= str_repeat('=', 4 - $remainder);
  $decoded = base64_decode(strtr($data, '-_', '+/'));
  return $decoded !== false ? $decoded : false;
}

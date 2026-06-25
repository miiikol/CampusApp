<?php

/**
 * JWT 工具（HMAC-SHA256，无外部依赖）
 */

function jwt_encode(array $payload): string {
  $secret = env('JWT_SECRET', '');
  $header = ['alg' => 'HS256', 'typ' => 'JWT'];

  $segments = [];
  $segments[] = base64url(json_encode($header));
  $segments[] = base64url(json_encode($payload));

  $signingInput = implode('.', $segments);
  $signature = hash_hmac('sha256', $signingInput, $secret, true);
  $segments[] = base64url($signature);

  return implode('.', $segments);
}

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

  if (!hash_equals($expected, $signature)) return null;

  $payload = json_decode(base64url_decode($payloadB64), true);
  if (!is_array($payload)) return null;

  // 检查过期
  if (isset($payload['exp']) && $payload['exp'] < time()) return null;

  return $payload;
}

function jwt_generate(string $userId, string $role = 'student'): string {
  $expireHours = (int)env('JWT_EXPIRE_HOURS', '168');
  return jwt_encode([
    'sub' => $userId,
    'role' => $role,
    'iat' => time(),
    'exp' => time() + $expireHours * 3600,
  ]);
}

function base64url(string $data): string {
  return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
}
function base64url_decode(string $data) {
  $remainder = strlen($data) % 4;
  if ($remainder) $data .= str_repeat('=', 4 - $remainder);
  $decoded = base64_decode(strtr($data, '-_', '+/'));
  return $decoded !== false ? $decoded : false;
}

<?php

if ($method === 'POST' && $path === '/upload/image') {
  // 鉴权（兼容旧客户端：有 Token 则校验，无 Token 允许但不安全）
  $auth = authenticateOptional();
  if ($auth === null) {
    // 调试模式可放宽；生产环境应强制要求
    if (env('APP_DEBUG', 'false') !== 'true') {
      respond(401, ['message' => '请先登录']);
    }
  }

  $tmpPath = validateUploadedFile();
  $originalName = (string)($_FILES['file']['name'] ?? '');
  $url = uploadFile($tmpPath, $originalName);

  respond(200, ['url' => $url]);
}

<?php

if ($method === 'POST' && $path === '/upload/image') {
  // 强制鉴权：所有上传操作必须提供有效 Token
  authenticate();

  $tmpPath = validateUploadedFile();
  $originalName = (string)($_FILES['file']['name'] ?? '');
  $url = uploadFile($tmpPath, $originalName);

  respond(200, ['url' => $url]);
}

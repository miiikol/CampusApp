<?php

/**
 * 上传模块：图片上传
 */

// 上传图片：鉴权 + 文件校验 + 本地存储，返回访问 URL
if ($method === 'POST' && $path === '/upload/image') {
  // 强制鉴权：所有上传操作必须提供有效 Token
  authenticate();

  $tmpPath = validateUploadedFile();
  $originalName = (string)($_FILES['file']['name'] ?? '');
  $url = uploadFile($tmpPath, $originalName);

  respond(200, ['url' => $url]);
}

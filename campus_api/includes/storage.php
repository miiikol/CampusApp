<?php

/**
 * 文件存储抽象层
 *
 * 通过 .env 中的 STORAGE_DRIVER 切换存储后端：
 *   local  — 本地磁盘存储（适合开发）
 *   s3     — AWS S3 兼容存储（阿里云OSS、腾讯云COS等）
 *
 * 上传接口：uploadFile($tmpPath, $originalName) → URL
 */

function storageDriver(): string {
  return strtolower(env('STORAGE_DRIVER', 'local'));
}

/**
 * 上传文件到存储，返回访问 URL
 */
function uploadFile(string $tmpPath, string $originalName): string {
  $driver = storageDriver();

  if ($driver === 's3') {
    return uploadToS3($tmpPath, $originalName);
  }

  // 默认 local
  return uploadToLocal($tmpPath, $originalName);
}

/**
 * 本地存储
 */
function uploadToLocal(string $tmpPath, string $originalName): string {
  $ext = normalizeExt($originalName);
  $filename = bin2hex(random_bytes(16)) . '.' . $ext;

  $uploadDir = __DIR__ . DIRECTORY_SEPARATOR . '..' . DIRECTORY_SEPARATOR . 'uploads';
  if (!is_dir($uploadDir)) {
    @mkdir($uploadDir, 0777, true);
  }
  if (!is_dir($uploadDir)) {
    respond(500, ['message' => '服务器错误：无法创建上传目录']);
  }

  $targetPath = $uploadDir . DIRECTORY_SEPARATOR . $filename;
  if (!move_uploaded_file($tmpPath, $targetPath)) {
    respond(500, ['message' => '文件保存失败']);
  }

  return buildPublicUrl('/uploads/' . $filename);
}

/**
 * S3 / OSS 兼容存储
 *
 * 部署到云服务器后替换此处的 SDK 调用：
 *   阿里云 OSS: composer require aliyuncs/oss-sdk-php
 *   腾讯云 COS: composer require qcloud/cos-sdk-v5
 *   AWS S3:     composer require aws/aws-sdk-php
 */
function uploadToS3(string $tmpPath, string $originalName): string {
  $bucket = env('S3_BUCKET', '');
  $region = env('S3_REGION', '');
  $endpoint = env('S3_ENDPOINT', ''); // OSS/COS 自定义 endpoint

  if ($bucket === '' || $region === '') {
    respond(500, ['message' => 'S3 存储未正确配置']);
  }

  $ext = normalizeExt($originalName);
  $key = 'uploads/' . date('Y/m/') . bin2hex(random_bytes(16)) . '.' . $ext;

  // 占位：替换为实际 SDK 调用
  //
  // 示例（AWS S3 SDK）：
  // $s3 = new Aws\S3\S3Client(['region' => $region, 'version' => 'latest']);
  // $s3->putObject(['Bucket' => $bucket, 'Key' => $key, 'SourceFile' => $tmpPath]);
  //
  // 示例（阿里云 OSS SDK）：
  // $client = new OSS\OssClient(env('S3_ACCESS_KEY'), env('S3_SECRET_KEY'), $endpoint);
  // $client->uploadFile($bucket, $key, $tmpPath);

  // 生产部署时取消下面的注释并安装对应 SDK
  respond(500, ['message' => 'S3 存储驱动需要安装 SDK 后启用']);
  return ''; // unreachable
}

/**
 * 构造公开访问 URL
 */
function buildPublicUrl(string $path): string {
  $appUrl = rtrim(env('APP_URL', ''), '/');
  if ($appUrl !== '') {
    return $appUrl . $path;
  }

  $scheme = (!empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off') ? 'https' : 'http';
  $host = (string)($_SERVER['HTTP_HOST'] ?? 'localhost');
  $scriptName = (string)($_SERVER['SCRIPT_NAME'] ?? '/campus_api/index.php');
  $basePath = rtrim(str_replace('\\', '/', dirname($scriptName)), '/');
  return $scheme . '://' . $host . $basePath . $path;
}

/**
 * 标准化文件扩展名
 */
function normalizeExt(string $filename): string {
  $ext = strtolower(pathinfo($filename, PATHINFO_EXTENSION));
  if ($ext === 'jpeg') $ext = 'jpg';
  return $ext;
}

/**
 * 验证上传文件
 * @return string tmp_path
 */
function validateUploadedFile(): string {
  if (!isset($_FILES['file'])) {
    respond(400, ['message' => 'file 不能为空']);
  }

  $file = $_FILES['file'];
  if (!is_array($file) || ($file['error'] ?? UPLOAD_ERR_NO_FILE) !== UPLOAD_ERR_OK) {
    respond(400, ['message' => '上传失败']);
  }

  $size = (int)($file['size'] ?? 0);
  if ($size <= 0) {
    respond(400, ['message' => '空文件']);
  }
  if ($size > 5 * 1024 * 1024) {
    respond(400, ['message' => '图片不能超过 5MB']);
  }

  $tmpName = (string)($file['tmp_name'] ?? '');
  if ($tmpName === '' || !is_uploaded_file($tmpName)) {
    respond(400, ['message' => '无效文件']);
  }

  $originalName = (string)($file['name'] ?? '');
  $ext = normalizeExt($originalName);
  $allowed = ['jpg', 'png', 'webp'];
  if (!in_array($ext, $allowed, true)) {
    respond(400, ['message' => '仅支持 JPG/PNG/WEBP']);
  }

  return $tmpName;
}

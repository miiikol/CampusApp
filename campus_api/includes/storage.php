<?php

/**
 * 文件存储（本地磁盘）
 *
 * 上传流程：
 *   1. validateUploadedFile() — 校验大小/类型/魔数
 *   2. uploadFile($tmpPath, $originalName) — 保存并返回访问URL
 */

/**
 * 上传文件到本地存储，返回访问 URL
 */
function uploadFile(string $tmpPath, string $originalName): string {
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

  // 上传后设置权限，防止直接执行
  @chmod($targetPath, 0644);

  return buildPublicUrl('/uploads/' . $filename);
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
 * 白名单允许的扩展名
 */
function allowedExtensions(): array {
  return ['jpg', 'jpeg', 'png', 'webp'];
}

/**
 * 魔数校验 — 通过文件头字节确保文件类型真实可靠
 */
function validateFileMagic(string $tmpPath, string $ext): void {
  $handle = @fopen($tmpPath, 'rb');
  if ($handle === false) {
    respond(400, ['message' => '无法读取文件']);
  }

  $magic = fread($handle, 12);
  fclose($handle);

  $bytes = strtoupper(bin2hex(substr($magic, 0, 4)));

  $validMagic = false;
  switch ($ext) {
    case 'jpg':
    case 'jpeg':
      // JPEG: FF D8 FF (E0/E1/E2/DB)
      $validMagic = (substr($bytes, 0, 4) === 'FFD8');
      break;
    case 'png':
      // PNG: 89 50 4E 47
      $validMagic = ($bytes === '89504E47');
      break;
    case 'webp':
      // WebP: 52 49 46 46 ... 57 45 42 50
      $rif = strtoupper(bin2hex(substr($magic, 0, 4)));
      $webp = strtoupper(bin2hex(substr($magic, 8, 4)));
      $validMagic = ($rif === '52494646' && $webp === '57454250');
      break;
  }

  if (!$validMagic) {
    respond(400, ['message' => '不允许的文件类型（文件头校验失败）']);
  }
}

/**
 * 验证上传文件（含大小、类型、魔数校验）
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
  $allowed = allowedExtensions();
  if (!in_array($ext, $allowed, true)) {
    respond(400, ['message' => '仅支持 JPG/PNG/WEBP']);
  }

  // 魔数校验：防止扩展名伪装的恶意文件
  validateFileMagic($tmpName, $ext);

  return $tmpName;
}

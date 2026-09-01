<?php

/**
 * 环境变量
 *
 * 从 .env 文件加载配置到环境变量，并提供 env() 便捷读取。
 */

/**
 * 加载 .env 文件到环境变量（不覆盖已存在的环境变量）
 */
function loadEnv(string $envDir): void {
  $envFile = $envDir . DIRECTORY_SEPARATOR . '.env';
  if (!file_exists($envFile)) return;

  $lines = file($envFile, FILE_IGNORE_NEW_LINES | FILE_SKIP_EMPTY_LINES);
  if ($lines === false) return;

  foreach ($lines as $line) {
    $line = trim($line);
    // 跳过注释
    if ($line === '' || $line[0] === '#') continue;

    $eqPos = strpos($line, '=');
    if ($eqPos === false) continue;

    $key = trim(substr($line, 0, $eqPos));
    $value = trim(substr($line, $eqPos + 1));

    // 去掉引号
    if (
      (strlen($value) >= 2) &&
      (($value[0] === '"' && $value[-1] === '"') || ($value[0] === "'" && $value[-1] === "'"))
    ) {
      $value = substr($value, 1, -1);
    }

    // 不覆盖已设置的环境变量（系统变量优先）
    if (getenv($key) === false) {
      putenv("$key=$value");
      $_ENV[$key] = $value;
    }
  }
}

/**
 * 获取环境变量，支持默认值
 */
function env(string $key, string $default = ''): string {
  $val = getenv($key);
  return $val !== false ? (string)$val : $default;
}

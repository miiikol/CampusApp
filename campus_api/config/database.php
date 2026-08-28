<?php

function db() {
  static $pdo = null;
  if ($pdo) return $pdo;

  $host    = env('DB_HOST', '127.0.0.1');
  $db      = env('DB_NAME', 'campus_api');
  $user    = env('DB_USER', 'root');
  $pass    = env('DB_PASS', '');
  $charset = env('DB_CHARSET', 'utf8mb4');

  $dsn = "mysql:host=$host;dbname=$db;charset=$charset";

  // 生产环境启用持久连接，减少频繁 TCP 握手开销
  $options = [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
  ];

  if (env('DB_PERSISTENT', 'true') === 'true') {
    $options[PDO::ATTR_PERSISTENT] = true;
  }

  $pdo = new PDO($dsn, $user, $pass, $options);
  return $pdo;
}

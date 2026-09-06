<?php

declare(strict_types=1);

/**
 * 速率限制单元测试（纯 PHP，无需 PHPUnit / Composer）
 *
 * 运行方式：
 *   php tests/rate_limit_test.php
 *
 * 覆盖：
 * 1. APCu 路径：用内存假实现验证修复后的原子计数、窗口重置、按 key 隔离；
 * 2. 数据库降级路径：用 SQLite 内存库验证窗口内计数与超限拦截。
 */

error_reporting(E_ALL);
ini_set('display_errors', '1');

// ---------- 依赖桩 ----------
// rate_limit.php 不自行 require 依赖，这里补上测试用的轻量实现。
if (!function_exists('now_ms')) {
    function now_ms(): int
    {
        return (int) floor(microtime(true) * 1000);
    }
}

if (!function_exists('logError')) {
    function logError(string $message, ?Exception $e = null, array $context = []): void
    {
        // 测试环境不写日志
    }
}

// 假 APCu：仅当真实扩展不存在时注入，保证 CLI 下可确定性测试。
if (!function_exists('apcu_enabled')) {
    $GLOBALS['__test_apcu'] = [];

    function apcu_enabled(): bool
    {
        return true;
    }

    function apcu_fetch(string $key)
    {
        return $GLOBALS['__test_apcu'][$key] ?? false;
    }

    function apcu_store(string $key, $value, int $ttl = 0): bool
    {
        $GLOBALS['__test_apcu'][$key] = $value;
        return true;
    }

    function apcu_inc(string $key, int $step = 1, &$success = null, int $ttl = 0)
    {
        if (!isset($GLOBALS['__test_apcu'][$key])) {
            $success = false;
            return false;
        }
        $GLOBALS['__test_apcu'][$key] += $step;
        $success = true;
        return $GLOBALS['__test_apcu'][$key];
    }

    function apcu_delete(string $key): bool
    {
        unset($GLOBALS['__test_apcu'][$key]);
        return true;
    }
}

require __DIR__ . '/../includes/rate_limit.php';

// ---------- 断言与统计 ----------
$GLOBALS['__pass'] = 0;
$GLOBALS['__fail'] = 0;

function assertTrue($cond, string $msg): void
{
    if ($cond) {
        $GLOBALS['__pass']++;
        echo "  [PASS] $msg\n";
    } else {
        $GLOBALS['__fail']++;
        echo "  [FAIL] $msg\n";
    }
}

function apcuClear(string $ip, string $key): void
{
    apcu_delete("rl:{$ip}:{$key}:count");
    apcu_delete("rl:{$ip}:{$key}:start");
}

// ---------- 1. APCu 路径 ----------
echo "== APCu 限流 ==\n";
$ip = '10.0.0.1';
apcuClear($ip, 'login');

for ($i = 1; $i <= 5; $i++) {
    assertTrue(rateLimitApcu($ip, 'login', 5, 60), "APCu 第 {$i} 次请求应放行");
}
assertTrue(!rateLimitApcu($ip, 'login', 5, 60), 'APCu 第 6 次请求应被拦截');

// 手动把窗口起点改到过去，模拟窗口过期后应重置计数并放行
apcu_store("rl:{$ip}:login:start", time() - 61, 60);
apcu_store("rl:{$ip}:login:count", 99, 60);
assertTrue(rateLimitApcu($ip, 'login', 5, 60), 'APCu 窗口过期后应重置计数并放行');

// 不同 key 之间计数隔离
apcuClear($ip, 'reset_password');
for ($i = 1; $i <= 3; $i++) {
    rateLimitApcu($ip, 'reset_password', 3, 60);
}
assertTrue(!rateLimitApcu($ip, 'reset_password', 3, 60), 'APCu 不同 key 独立计数');

// ---------- 2. DB 降级路径（SQLite 内存库） ----------
echo "== DB 降级限流 ==\n";
// 覆盖全局 db() 返回内存 SQLite，避免依赖真实 MySQL。
// SQLite 兼容 MySQL 风格的反引号标识符，rate_limit.php 中的 SQL 可直接执行。
function db(): PDO
{
    static $pdo = null;
    if ($pdo === null) {
        $pdo = new PDO('sqlite::memory:');
        $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
        $pdo->setAttribute(PDO::ATTR_DEFAULT_FETCH_MODE, PDO::FETCH_ASSOC);
        $pdo->exec('CREATE TABLE rate_limits (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            ip TEXT NOT NULL,
            `key` TEXT NOT NULL,
            hit_at INTEGER NOT NULL
        )');
    }
    return $pdo;
}

$ip2 = '10.0.0.2';
for ($i = 1; $i <= 5; $i++) {
    assertTrue(rateLimitDb($ip2, 'login', 5, 60), "DB 第 {$i} 次请求应放行");
}
assertTrue(!rateLimitDb($ip2, 'login', 5, 60), 'DB 第 6 次请求应被拦截');

// ---------- 汇总 ----------
echo "\n结果: {$GLOBALS['__pass']} 通过, {$GLOBALS['__fail']} 失败\n";
exit($GLOBALS['__fail'] > 0 ? 1 : 0);

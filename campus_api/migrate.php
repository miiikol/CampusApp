<?php
/**
 * 数据库迁移脚本
 *
 * 生产环境部署第一步：php migrate.php
 * 该脚本会按顺序创建/更新所有表结构，确保数据库处于最新状态。
 */

require_once __DIR__ . '/config/env.php';
loadEnv(__DIR__);
require_once __DIR__ . '/config/database.php';
require_once __DIR__ . '/includes/helpers.php';
// 提供 ensureAppMetaTable/getMetaValue/setMetaValue 等函数，供种子数据播种调用
require_once __DIR__ . '/includes/db_schema.php';

echo "=== Campus API 数据库迁移 ===\n";
echo "数据库: " . env('DB_NAME', 'campus_api') . "\n\n";

$pdo = db();
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

// ================================
// 1. 用户与认证
// ================================
echo "[1/13] users ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(64) PRIMARY KEY,
    student_id VARCHAR(32) NOT NULL UNIQUE,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(16) NOT NULL DEFAULT 'student',
    avatar_url VARCHAR(500) NULL,
    full_name VARCHAR(100) NULL,
    id_card_hash VARCHAR(64) NULL,
    created_at BIGINT NOT NULL,
    INDEX idx_student_id (student_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");
safeAlter($pdo, "ALTER TABLE users ADD COLUMN full_name VARCHAR(100) NULL");
safeAlter($pdo, "ALTER TABLE users ADD COLUMN id_card_hash VARCHAR(64) NULL");

echo "[2/13] auth_tokens ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS auth_tokens (
    token VARCHAR(128) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    INDEX idx_user (user_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

// ================================
// 2. 课程
// ================================
echo "[3/13] courses ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS courses (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    room VARCHAR(100) NOT NULL,
    teacher VARCHAR(100) NOT NULL,
    day_of_week INT NOT NULL,
    start_section INT NOT NULL,
    end_section INT NOT NULL,
    week_range VARCHAR(64) NOT NULL
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

echo "[4/13] course_assignments ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS course_assignments (
    user_id VARCHAR(64) NOT NULL,
    course_id BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY(user_id, course_id),
    INDEX idx_course (course_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

echo "[5/13] user_course_customizations ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS user_course_customizations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    source_course_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(100) NOT NULL,
    room VARCHAR(100) NOT NULL,
    teacher VARCHAR(100) NOT NULL,
    day_of_week INT NOT NULL,
    start_section INT NOT NULL,
    end_section INT NOT NULL,
    week_range VARCHAR(64) NOT NULL,
    color INT NOT NULL DEFAULT -1,
    only_week INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    UNIQUE KEY uniq_user_source_week (user_id, source_course_id, only_week),
    INDEX idx_user_updated (user_id, updated_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

// ================================
// 3. 资讯与评论
// ================================
echo "[6/13] news ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS news (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    summary VARCHAR(255) NULL,
    content TEXT NOT NULL,
    image_url VARCHAR(500) NULL,
    publish_date BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    INDEX idx_publish (publish_date, type)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

echo "[7/13] news_comments ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS news_comments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    news_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    username VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    parent_comment_id BIGINT NULL,
    reply_to_user_id VARCHAR(64) NULL,
    reply_to_username VARCHAR(100) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    publish_time BIGINT NOT NULL,
    banned_by VARCHAR(64) NULL,
    ban_reason VARCHAR(255) NULL,
    ban_time BIGINT NULL,
    INDEX idx_news_publish (news_id, publish_time),
    INDEX idx_news_parent (news_id, parent_comment_id),
    INDEX idx_status (status)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");
safeAlter($pdo, "ALTER TABLE news_comments ADD COLUMN parent_comment_id BIGINT NULL");
safeAlter($pdo, "ALTER TABLE news_comments ADD COLUMN reply_to_user_id VARCHAR(64) NULL");
safeAlter($pdo, "ALTER TABLE news_comments ADD COLUMN reply_to_username VARCHAR(100) NULL");

echo "[8/13] news_favorites ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS news_favorites (
    news_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY(news_id, user_id),
    INDEX idx_news_favorites_user (user_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

echo "[9/13] news_likes + news_comment_likes ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS news_likes (
    news_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY(news_id, user_id),
    INDEX idx_user (user_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");
$pdo->exec("
  CREATE TABLE IF NOT EXISTS news_comment_likes (
    comment_id BIGINT NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY(comment_id, user_id),
    INDEX idx_user (user_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

// ================================
// 4. 二手市场 & 失物招领
// ================================
echo "[10/13] market_items + market_favorites ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS market_items (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    seller_id VARCHAR(64) NOT NULL,
    image_url VARCHAR(500) NULL,
    publish_time BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    INDEX idx_status_publish (status, publish_time)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");
$pdo->exec("
  CREATE TABLE IF NOT EXISTS market_favorites (
    item_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY(item_id, user_id),
    INDEX idx_market_favorites_user (user_id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

echo "[11/13] lost_found_items ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS lost_found_items (
    id VARCHAR(64) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    location VARCHAR(100) NOT NULL,
    type VARCHAR(32) NOT NULL,
    image_url VARCHAR(500) NULL,
    contact_info VARCHAR(255) NULL,
    publish_time BIGINT NOT NULL,
    latitude DOUBLE NULL,
    longitude DOUBLE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    INDEX idx_status_publish (status, publish_time)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

// ================================
// 5. 通知 & 应用元数据
// ================================
echo "[12/13] notifications + app_meta + rate_limits ...\n";
$pdo->exec("
  CREATE TABLE IF NOT EXISTS notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content VARCHAR(255) NOT NULL,
    related_type VARCHAR(32) NULL,
    related_id VARCHAR(64) NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    INDEX idx_user_read (user_id, is_read, id),
    INDEX idx_user_id (user_id, id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");
$pdo->exec("
  CREATE TABLE IF NOT EXISTS app_meta (
    k VARCHAR(64) PRIMARY KEY,
    v VARCHAR(255) NOT NULL,
    updated_at BIGINT NOT NULL
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");
$pdo->exec("
  CREATE TABLE IF NOT EXISTS rate_limits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ip VARCHAR(64) NOT NULL,
    `key` VARCHAR(64) NOT NULL,
    hit_at BIGINT NOT NULL,
    INDEX idx_ip_key (ip, `key`, hit_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
");

// ================================
// 6. 演示数据
// ================================
echo "[13/13] 演示数据 ...\n";
require_once __DIR__ . '/includes/seed_data.php';
ensureDemoContentSeeded();

echo "\n✅ 数据库迁移完成！\n";

// ================================
// 辅助函数
// ================================
/**
 * 安全执行 ALTER：重复执行触发“列已存在”异常时视为正常，跳过即可
 */
function safeAlter(PDO $pdo, string $sql): void {
  try {
    $pdo->exec($sql);
    echo "  ✓ 迁移: $sql\n";
  } catch (Exception $e) {
    // ALTER 重复执行触发异常是正常情况
    $msg = $e->getMessage();
    if (stripos($msg, 'Duplicate column') !== false || stripos($msg, 'Duplicate key') !== false) {
      echo "  - 跳过(已存在)\n";
    } else {
      echo "  ⚠ 警告: $msg\n";
      logError('migrate safeAlter', $e);
    }
  }
}

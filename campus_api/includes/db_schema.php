<?php

function ensureNewsCommentsTable() {
  db()->exec("
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

  try { db()->exec("ALTER TABLE news_comments ADD COLUMN parent_comment_id BIGINT NULL"); } catch (Exception $e) { logError('migration: news_comments.parent_comment_id', $e); }
  try { db()->exec("ALTER TABLE news_comments ADD COLUMN reply_to_user_id VARCHAR(64) NULL"); } catch (Exception $e) { logError('migration: news_comments.reply_to_user_id', $e); }
  try { db()->exec("ALTER TABLE news_comments ADD COLUMN reply_to_username VARCHAR(100) NULL"); } catch (Exception $e) { logError('migration: news_comments.reply_to_username', $e); }
}

function ensureNewsLikesTables() {
  db()->exec("
    CREATE TABLE IF NOT EXISTS news_likes (
      news_id VARCHAR(64) NOT NULL,
      user_id VARCHAR(64) NOT NULL,
      created_at BIGINT NOT NULL,
      PRIMARY KEY(news_id, user_id),
      INDEX idx_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  ");
  db()->exec("
    CREATE TABLE IF NOT EXISTS news_comment_likes (
      comment_id BIGINT NOT NULL,
      user_id VARCHAR(64) NOT NULL,
      created_at BIGINT NOT NULL,
      PRIMARY KEY(comment_id, user_id),
      INDEX idx_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  ");
}

function ensureFavoritesTables() {
  db()->exec("
    CREATE TABLE IF NOT EXISTS news_favorites (
      news_id VARCHAR(64) NOT NULL,
      user_id VARCHAR(64) NOT NULL,
      created_at BIGINT NOT NULL,
      PRIMARY KEY(news_id, user_id),
      INDEX idx_news_favorites_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  ");
  db()->exec("
    CREATE TABLE IF NOT EXISTS market_favorites (
      item_id VARCHAR(64) NOT NULL,
      user_id VARCHAR(64) NOT NULL,
      created_at BIGINT NOT NULL,
      PRIMARY KEY(item_id, user_id),
      INDEX idx_market_favorites_user (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  ");
}

function ensureNotificationsTable() {
  db()->exec("
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
}

function notifyUser($userId, $type, $title, $content, $relatedType = null, $relatedId = null) {
  ensureNotificationsTable();
  $uid = trim((string)$userId);
  if ($uid === '') return;
  $stmt = db()->prepare("INSERT INTO notifications (user_id, type, title, content, related_type, related_id, is_read, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?)");
  $stmt->execute([$uid, $type, $title, $content, $relatedType, $relatedId, now_ms()]);
}

function ensureAppMetaTable() {
  db()->exec("
    CREATE TABLE IF NOT EXISTS app_meta (
      k VARCHAR(64) PRIMARY KEY,
      v VARCHAR(255) NOT NULL,
      updated_at BIGINT NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  ");
}

function getMetaValue($key) {
  ensureAppMetaTable();
  $k = trim((string)$key);
  if ($k === '') return null;
  $stmt = db()->prepare('SELECT v FROM app_meta WHERE k = ? LIMIT 1');
  $stmt->execute([$k]);
  $row = $stmt->fetch();
  return $row ? (string)$row['v'] : null;
}

function setMetaValue($key, $value) {
  ensureAppMetaTable();
  $k = trim((string)$key);
  if ($k === '') return;
  $v = (string)$value;
  $stmt = db()->prepare("
    INSERT INTO app_meta (k, v, updated_at)
    VALUES (?, ?, ?)
    ON DUPLICATE KEY UPDATE v = VALUES(v), updated_at = VALUES(updated_at)
  ");
  $stmt->execute([$k, $v, now_ms()]);
}

function ensureUsersProfileColumns() {
  try { db()->exec("ALTER TABLE users ADD COLUMN full_name VARCHAR(100) NULL"); } catch (Exception $e) { logError('migration: users.full_name', $e); }
  try { db()->exec("ALTER TABLE users ADD COLUMN id_card_hash VARCHAR(64) NULL"); } catch (Exception $e) { logError('migration: users.id_card_hash', $e); }
}

function ensureCourseAssignmentsTable() {
  db()->exec("
    CREATE TABLE IF NOT EXISTS course_assignments (
      user_id VARCHAR(64) NOT NULL,
      course_id BIGINT NOT NULL,
      created_at BIGINT NOT NULL,
      PRIMARY KEY(user_id, course_id),
      INDEX idx_course (course_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
  ");
}

function ensureUserCourseCustomizationsTable() {
  db()->exec("
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
}

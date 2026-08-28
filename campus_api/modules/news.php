<?php

if ($method === 'GET' && $path === '/news') {

  $pg = parsePagination();
  $viewerId = trim((string)($_GET['userId'] ?? ''));

  // APCu 缓存：列表缓存 30 秒，大幅降低数据库压力
  $cacheKey = cacheKey('/news', ['page' => $pg['page'], 'pageSize' => $pg['pageSize'], 'viewerId' => $viewerId]);
  $cached = cacheGet($cacheKey);
  if ($cached !== null) respond(200, $cached);

  if ($viewerId === '') {
    $stmt = db()->prepare('SELECT id, title, summary, content, image_url, publish_date, type, 0 AS is_favorite FROM news ORDER BY publish_date DESC LIMIT ? OFFSET ?');
    $stmt->execute([$pg['limit'], $pg['offset']]);
    $rows = $stmt->fetchAll();
    $totalStmt = db()->query('SELECT COUNT(*) AS cnt FROM news');
  } else {
    $stmt = db()->prepare("
      SELECT
        n.id, n.title, n.summary, n.content, n.image_url, n.publish_date, n.type,
        CASE WHEN f.user_id IS NULL THEN 0 ELSE 1 END AS is_favorite
      FROM news n
      LEFT JOIN news_favorites f ON f.news_id = n.id AND f.user_id = ?
      ORDER BY n.publish_date DESC
      LIMIT ? OFFSET ?
    ");
    $stmt->execute([$viewerId, $pg['limit'], $pg['offset']]);
    $rows = $stmt->fetchAll();
    $totalStmt = db()->query('SELECT COUNT(*) AS cnt FROM news');
  }
  $total = (int)($totalStmt->fetch()['cnt'] ?? 0);
  $out = array_map(function($r) {
    return [
      'id' => (string)$r['id'],
      'title' => $r['title'],
      'summary' => $r['summary'],
      'content' => $r['content'],
      'imageUrl' => $r['image_url'],
      'publishDate' => (int)$r['publish_date'],
      'type' => $r['type'],
      'isFavorite' => ((int)($r['is_favorite'] ?? 0)) === 1,
    ];
  }, $rows);
  $response = [
    'data' => $out,
    'page' => $pg['page'],
    'pageSize' => $pg['pageSize'],
    'total' => $total,
  ];
  cacheSet($cacheKey, $response, 30);
  respond(200, $response);
}

if ($method === 'POST' && $path === '/news') {
  $body = jsonBody();

  // 强制 Token 鉴权：仅管理员可发布
  $auth = authenticate();
  if ($auth['role'] !== 'admin') {
    respond(403, ['message' => '仅管理员可发布资讯']);
  }

  $title = trim((string)($body['title'] ?? ''));
  $summary = trim((string)($body['summary'] ?? ''));
  $content = trim((string)($body['content'] ?? ''));
  $type = trim((string)($body['type'] ?? ''));
  $imageUrl = isset($body['imageUrl']) ? trim((string)$body['imageUrl']) : null;

  if ($title === '' || $content === '' || $type === '') {
    respond(400, ['message' => 'title/content/type 不能为空']);
  }
  if ($summary === '') {
    $summary = mb_substr($content, 0, 100);
  }
  if ($imageUrl === '' || $imageUrl === null) {
    $imageUrl = null;
  }

  $id = bin2hex(random_bytes(8));
  $publishDate = now_ms();

  $stmt = db()->prepare("INSERT INTO news (id, title, summary, content, image_url, publish_date, type) VALUES (?, ?, ?, ?, ?, ?, ?)");
  $stmt->execute([$id, $title, $summary, $content, $imageUrl, $publishDate, $type]);

  // 写入后清除列表缓存
  cacheDeleteByPrefix('/news:');

  respond(200, [
    'id' => $id,
    'title' => $title,
    'summary' => $summary,
    'content' => $content,
    'imageUrl' => $imageUrl,
    'publishDate' => $publishDate,
    'type' => $type,
    'isFavorite' => false,
  ]);
}

if ($method === 'POST' && preg_match('#^/news/([^/]+)/favorites/toggle$#', $path, $matches)) {

  $newsId = trim((string)$matches[1]);
  $body = jsonBody();
  $userId = trim((string)($body['userId'] ?? ''));

  // 强制 Token 鉴权
  $auth = authenticate();
  if ($auth['userId'] !== $userId) {
    respond(403, ['message' => '无权操作']);
  }
  if ($newsId === '' || $userId === '') {
    respond(400, ['message' => 'newsId/userId 不能为空']);
  }

  $pdo = db();
  $pdo->beginTransaction();
  try {
    $stmt0 = $pdo->prepare("SELECT 1 FROM news_favorites WHERE news_id = ? AND user_id = ? LIMIT 1");
    $stmt0->execute([$newsId, $userId]);
    $exists = $stmt0->fetch() ? true : false;
    if ($exists) {
      $stmt1 = $pdo->prepare("DELETE FROM news_favorites WHERE news_id = ? AND user_id = ?");
      $stmt1->execute([$newsId, $userId]);
      $favorited = false;
    } else {
      $stmt1 = $pdo->prepare("INSERT INTO news_favorites (news_id, user_id, created_at) VALUES (?, ?, ?)");
      $stmt1->execute([$newsId, $userId, now_ms()]);
      $favorited = true;
    }
    $pdo->commit();
    respond(200, ['newsId' => $newsId, 'favorited' => $favorited]);
  } catch (Exception $e) {
    $pdo->rollBack();
    logError('news favorites toggle', $e);
    respond(500, ['message' => '服务器错误']);
  }
}

if ($method === 'GET' && preg_match('#^/news/([^/]+)/comments$#', $path, $matches)) {

  $newsId = (string)$matches[1];
  $viewerId = trim((string)($_GET['userId'] ?? ''));
  $pg = parsePagination(200, 500);
  if ($viewerId === '') {
    $stmt = db()->prepare("
      SELECT
        c.id, c.news_id, c.user_id,
        COALESCE(u.username, c.username) AS username,
        u.avatar_url,
        c.content, c.parent_comment_id, c.reply_to_user_id,
        COALESCE(ru.username, c.reply_to_username) AS reply_to_username,
        c.status, c.publish_time,
        COALESCE(l.cnt, 0) AS like_count,
        0 AS liked_by_me
      FROM news_comments c
      LEFT JOIN users u ON u.id = c.user_id
      LEFT JOIN users ru ON ru.id = c.reply_to_user_id
      LEFT JOIN (
        SELECT comment_id, COUNT(*) AS cnt
        FROM news_comment_likes
        GROUP BY comment_id
      ) l ON l.comment_id = c.id
      WHERE c.news_id = ? AND c.status <> 'BANNED'
      ORDER BY c.publish_time ASC
      LIMIT ? OFFSET ?
    ");
    $stmt->execute([$newsId, $pg['limit'], $pg['offset']]);
  } else {
    $stmt = db()->prepare("
      SELECT
        c.id, c.news_id, c.user_id,
        COALESCE(u.username, c.username) AS username,
        u.avatar_url,
        c.content, c.parent_comment_id, c.reply_to_user_id,
        COALESCE(ru.username, c.reply_to_username) AS reply_to_username,
        c.status, c.publish_time,
        COALESCE(l.cnt, 0) AS like_count,
        CASE WHEN me.user_id IS NULL THEN 0 ELSE 1 END AS liked_by_me
      FROM news_comments c
      LEFT JOIN users u ON u.id = c.user_id
      LEFT JOIN users ru ON ru.id = c.reply_to_user_id
      LEFT JOIN (
        SELECT comment_id, COUNT(*) AS cnt
        FROM news_comment_likes
        GROUP BY comment_id
      ) l ON l.comment_id = c.id
      LEFT JOIN news_comment_likes me ON me.comment_id = c.id AND me.user_id = ?
      WHERE c.news_id = ? AND c.status <> 'BANNED'
      ORDER BY c.publish_time ASC
      LIMIT ? OFFSET ?
    ");
    $stmt->execute([$viewerId, $newsId, $pg['limit'], $pg['offset']]);
  }
  $rows = $stmt->fetchAll();
  $out = array_map(function($r) {
    return [
      'id' => (string)$r['id'],
      'newsId' => (string)$r['news_id'],
      'userId' => (string)$r['user_id'],
      'username' => (string)$r['username'],
      'avatarUrl' => $r['avatar_url'] !== null ? (string)$r['avatar_url'] : null,
      'content' => (string)$r['content'],
      'parentCommentId' => $r['parent_comment_id'] !== null ? (string)$r['parent_comment_id'] : null,
      'replyToUserId' => $r['reply_to_user_id'] !== null ? (string)$r['reply_to_user_id'] : null,
      'replyToUsername' => $r['reply_to_username'] !== null ? (string)$r['reply_to_username'] : null,
      'status' => (string)$r['status'],
      'publishTime' => (int)$r['publish_time'],
      'likeCount' => (int)($r['like_count'] ?? 0),
      'likedByMe' => ((int)($r['liked_by_me'] ?? 0)) === 1,
    ];
  }, $rows);

  $totalStmt = db()->prepare("SELECT COUNT(*) AS cnt FROM news_comments WHERE news_id = ? AND status <> 'BANNED'");
  $totalStmt->execute([$newsId]);
  $total = (int)($totalStmt->fetch()['cnt'] ?? 0);

  respond(200, [
    'data' => $out,
    'page' => $pg['page'],
    'pageSize' => $pg['pageSize'],
    'total' => $total,
  ]);
}

if ($method === 'POST' && preg_match('#^/news/([^/]+)/comments$#', $path, $matches)) {
  if (!rateLimit('comment', 20, 60)) {
    respond(429, ['message' => '评论过于频繁，请稍后再试']);
  }

  $newsId = (string)$matches[1];
  $body = jsonBody();
  $userId = trim((string)($body['userId'] ?? ''));

  // 强制 Token 鉴权
  $auth = authenticate();
  if ($auth['userId'] !== $userId) {
    respond(403, ['message' => '无权操作']);
  }
  $username = trim((string)($body['username'] ?? ''));
  $content = trim((string)($body['content'] ?? ''));
  $parentCommentId = isset($body['parentCommentId']) ? trim((string)$body['parentCommentId']) : '';
  $replyToUserId = isset($body['replyToUserId']) ? trim((string)$body['replyToUserId']) : '';
  $replyToUsername = isset($body['replyToUsername']) ? trim((string)$body['replyToUsername']) : '';

  if ($newsId === '' || $userId === '' || $content === '') {
    respond(400, ['message' => 'newsId/userId/content 不能为空']);
  }
  if (mb_strlen($content) > 300) {
    respond(400, ['message' => '评论字数不能超过 300']);
  }

  try {
    $stmtU = db()->prepare('SELECT username FROM users WHERE id = ? LIMIT 1');
    $stmtU->execute([$userId]);
    $rowU = $stmtU->fetch();
    if ($rowU) {
      $dbUsername = trim((string)($rowU['username'] ?? ''));
      if ($dbUsername !== '') $username = $dbUsername;
    }
  } catch (Exception $e) { logError('comment: resolve username', $e); }
  if ($username === '') {
    respond(400, ['message' => 'username 不能为空']);
  }

  $parentIdValue = null;
  $replyToUserIdValue = null;
  $replyToUsernameValue = null;
  if ($parentCommentId !== '') {
    $stmt0 = db()->prepare("SELECT id, user_id, username, news_id FROM news_comments WHERE id = ? LIMIT 1");
    $stmt0->execute([$parentCommentId]);
    $parent = $stmt0->fetch();
    if (!$parent || (string)$parent['news_id'] !== $newsId) {
      respond(400, ['message' => '回复目标不存在']);
    }
    $parentIdValue = (int)$parent['id'];
    $replyToUserIdValue = $replyToUserId !== '' ? $replyToUserId : (string)$parent['user_id'];
    $replyToUsernameValue = $replyToUsername !== '' ? $replyToUsername : (string)$parent['username'];
  }

  if ($replyToUserIdValue !== null && $replyToUserIdValue !== '') {
    try {
      $stmtRU = db()->prepare('SELECT username FROM users WHERE id = ? LIMIT 1');
      $stmtRU->execute([$replyToUserIdValue]);
      $rowRU = $stmtRU->fetch();
      if ($rowRU) {
        $dbReplyName = trim((string)($rowRU['username'] ?? ''));
        if ($dbReplyName !== '') $replyToUsernameValue = $dbReplyName;
      }
    } catch (Exception $e) { logError('comment: resolve reply username', $e); }
  }

  $publishTime = now_ms();
  $stmt = db()->prepare("INSERT INTO news_comments (news_id, user_id, username, content, parent_comment_id, reply_to_user_id, reply_to_username, status, publish_time) VALUES (?, ?, ?, ?, ?, ?, ?, 'NORMAL', ?)");
  $stmt->execute([$newsId, $userId, $username, $content, $parentIdValue, $replyToUserIdValue, $replyToUsernameValue, $publishTime]);
  $id = db()->lastInsertId();

  if ($parentIdValue !== null) {
    $targetUserId = $replyToUserIdValue;
    if ($targetUserId !== null && $targetUserId !== '' && $targetUserId !== $userId) {
      $title = '有人回复了你';
      $text = $username . ' 回复：' . (mb_strlen($content) > 60 ? mb_substr($content, 0, 60) . '...' : $content);
      notifyUser($targetUserId, 'COMMENT_REPLY', $title, $text, 'NEWS', $newsId);
    }
  }

  $avatarUrl = null;
  try {
    $stmtA = db()->prepare("SELECT avatar_url FROM users WHERE id = ? LIMIT 1");
    $stmtA->execute([$userId]);
    $rowA = $stmtA->fetch();
    if ($rowA && $rowA['avatar_url'] !== null) $avatarUrl = (string)$rowA['avatar_url'];
  } catch (Exception $e) { logError('comment: resolve avatar', $e); }

  $likeCount = 0;
  respond(200, [
    'id' => (string)$id,
    'newsId' => $newsId,
    'userId' => $userId,
    'username' => $username,
    'avatarUrl' => $avatarUrl,
    'content' => $content,
    'parentCommentId' => $parentIdValue !== null ? (string)$parentIdValue : null,
    'replyToUserId' => $replyToUserIdValue !== null ? (string)$replyToUserIdValue : null,
    'replyToUsername' => $replyToUsernameValue !== null ? (string)$replyToUsernameValue : null,
    'status' => 'NORMAL',
    'publishTime' => $publishTime,
    'likeCount' => $likeCount,
    'likedByMe' => false
  ]);
}

if ($method === 'GET' && preg_match('#^/news/([^/]+)/likes$#', $path, $matches)) {

  $newsId = (string)$matches[1];
  $viewerId = trim((string)($_GET['userId'] ?? ''));
  $stmt1 = db()->prepare("SELECT COUNT(*) AS cnt FROM news_likes WHERE news_id = ?");
  $stmt1->execute([$newsId]);
  $row = $stmt1->fetch();
  $count = (int)($row['cnt'] ?? 0);
  $liked = false;
  if ($viewerId !== '') {
    $stmt2 = db()->prepare("SELECT 1 FROM news_likes WHERE news_id = ? AND user_id = ? LIMIT 1");
    $stmt2->execute([$newsId, $viewerId]);
    $liked = $stmt2->fetch() ? true : false;
  }
  respond(200, ['newsId' => $newsId, 'likeCount' => $count, 'likedByMe' => $liked]);
}

if ($method === 'POST' && preg_match('#^/news/([^/]+)/likes/toggle$#', $path, $matches)) {

  $newsId = (string)$matches[1];
  $body = jsonBody();
  $userId = trim((string)($body['userId'] ?? ''));

  // 强制 Token 鉴权
  $auth = authenticate();
  if ($auth['userId'] !== $userId) {
    respond(403, ['message' => '无权操作']);
  }
  if ($newsId === '' || $userId === '') {
    respond(400, ['message' => 'newsId/userId 不能为空']);
  }

  $pdo = db();
  $pdo->beginTransaction();
  try {
    $stmt0 = $pdo->prepare("SELECT 1 FROM news_likes WHERE news_id = ? AND user_id = ? LIMIT 1");
    $stmt0->execute([$newsId, $userId]);
    $exists = $stmt0->fetch() ? true : false;
    if ($exists) {
      $stmt1 = $pdo->prepare("DELETE FROM news_likes WHERE news_id = ? AND user_id = ?");
      $stmt1->execute([$newsId, $userId]);
      $liked = false;
    } else {
      $stmt1 = $pdo->prepare("INSERT INTO news_likes (news_id, user_id, created_at) VALUES (?, ?, ?)");
      $stmt1->execute([$newsId, $userId, now_ms()]);
      $liked = true;
    }
    $stmt2 = $pdo->prepare("SELECT COUNT(*) AS cnt FROM news_likes WHERE news_id = ?");
    $stmt2->execute([$newsId]);
    $row = $stmt2->fetch();
    $count = (int)($row['cnt'] ?? 0);
    $pdo->commit();
    respond(200, ['newsId' => $newsId, 'liked' => $liked, 'likeCount' => $count]);
  } catch (Exception $e) {
    $pdo->rollBack();
    logError('news likes toggle', $e);
    respond(500, ['message' => '服务器错误']);
  }
}

if ($method === 'POST' && preg_match('#^/news/comments/([^/]+)/likes/toggle$#', $path, $matches)) {

  $commentId = trim((string)$matches[1]);
  $body = jsonBody();
  $userId = trim((string)($body['userId'] ?? ''));

  // 强制 Token 鉴权
  $auth = authenticate();
  if ($auth['userId'] !== $userId) {
    respond(403, ['message' => '无权操作']);
  }
  if ($commentId === '' || $userId === '') {
    respond(400, ['message' => 'commentId/userId 不能为空']);
  }

  $stmtC = db()->prepare("SELECT id, user_id, username, content, news_id FROM news_comments WHERE id = ? LIMIT 1");
  $stmtC->execute([$commentId]);
  $comment = $stmtC->fetch();
  if (!$comment) {
    respond(404, ['message' => '评论不存在']);
  }

  $pdo = db();
  $pdo->beginTransaction();
  try {
    $stmt0 = $pdo->prepare("SELECT 1 FROM news_comment_likes WHERE comment_id = ? AND user_id = ? LIMIT 1");
    $stmt0->execute([(int)$commentId, $userId]);
    $exists = $stmt0->fetch() ? true : false;
    if ($exists) {
      $stmt1 = $pdo->prepare("DELETE FROM news_comment_likes WHERE comment_id = ? AND user_id = ?");
      $stmt1->execute([(int)$commentId, $userId]);
      $liked = false;
    } else {
      $stmt1 = $pdo->prepare("INSERT INTO news_comment_likes (comment_id, user_id, created_at) VALUES (?, ?, ?)");
      $stmt1->execute([(int)$commentId, $userId, now_ms()]);
      $liked = true;
    }
    $stmt2 = $pdo->prepare("SELECT COUNT(*) AS cnt FROM news_comment_likes WHERE comment_id = ?");
    $stmt2->execute([(int)$commentId]);
    $row = $stmt2->fetch();
    $count = (int)($row['cnt'] ?? 0);
    $pdo->commit();

    if ($liked) {
      $targetUserId = (string)($comment['user_id'] ?? '');
      if ($targetUserId !== '' && $targetUserId !== $userId) {
        try {
          $stmtD = $pdo->prepare("DELETE FROM notifications WHERE user_id = ? AND type = 'COMMENT_LIKE' AND related_type = 'NEWS_COMMENT' AND related_id = ?");
          $stmtD->execute([$targetUserId, (string)$commentId]);
        } catch (Exception $e) { logError('comment_like: notification dedup', $e); }

        $likerName = getUsernameById($userId);
        if ($likerName === '') $likerName = '未知用户';
        $commentText = trim((string)($comment['content'] ?? ''));
        $snippet = $commentText !== '' ? (mb_strlen($commentText) > 30 ? mb_substr($commentText, 0, 30) . '...' : $commentText) : '';
        $title = '评论获赞';
        $content = $snippet !== '' ? ($likerName . ' 点赞了你的评论：' . $snippet) : ($likerName . ' 点赞了你的评论');
        notifyUser($targetUserId, 'COMMENT_LIKE', $title, $content, 'NEWS_COMMENT', (string)$commentId);
      }
    }

    respond(200, ['commentId' => $commentId, 'liked' => $liked, 'likeCount' => $count]);
  } catch (Exception $e) {
    $pdo->rollBack();
    logError('comment likes toggle', $e);
    respond(500, ['message' => '服务器错误']);
  }
}

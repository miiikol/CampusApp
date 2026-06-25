<?php

if ($method === 'GET' && $path === '/market') {

  $pg = parsePagination();
  $viewerId = trim((string)($_GET['userId'] ?? ''));
  if ($viewerId === '') {
    $stmt = db()->prepare("SELECT id, title, description, price, seller_id, image_url, publish_time, 0 AS is_favorite FROM market_items WHERE status = 'APPROVED' ORDER BY publish_time DESC LIMIT ? OFFSET ?");
    $stmt->execute([$pg['limit'], $pg['offset']]);
    $rows = $stmt->fetchAll();
    $totalStmt = db()->prepare("SELECT COUNT(*) AS cnt FROM market_items WHERE status = 'APPROVED'");
  } else {
    $stmt = db()->prepare("
      SELECT
        m.id, m.title, m.description, m.price, m.seller_id, m.image_url, m.publish_time,
        CASE WHEN f.user_id IS NULL THEN 0 ELSE 1 END AS is_favorite
      FROM market_items m
      LEFT JOIN market_favorites f ON f.item_id = m.id AND f.user_id = ?
      WHERE m.status = 'APPROVED'
      ORDER BY m.publish_time DESC
      LIMIT ? OFFSET ?
    ");
    $stmt->execute([$viewerId, $pg['limit'], $pg['offset']]);
    $rows = $stmt->fetchAll();
    $totalStmt = db()->prepare("SELECT COUNT(*) AS cnt FROM market_items WHERE status = 'APPROVED'");
  }
  $totalStmt->execute();
  $total = (int)($totalStmt->fetch()['cnt'] ?? 0);
  $out = array_map(function($r) {
    return [
      'id' => (string)$r['id'],
      'title' => $r['title'],
      'description' => $r['description'],
      'price' => (float)$r['price'],
      'sellerId' => $r['seller_id'],
      'imageUrl' => $r['image_url'],
      'publishTime' => (int)$r['publish_time'],
      'isFavorite' => ((int)($r['is_favorite'] ?? 0)) === 1,
    ];
  }, $rows);
  respond(200, [
    'data' => $out,
    'page' => $pg['page'],
    'pageSize' => $pg['pageSize'],
    'total' => $total,
  ]);
}

if ($method === 'POST' && preg_match('#^/market/([^/]+)/favorites/toggle$#', $path, $matches)) {

  $itemId = trim((string)$matches[1]);
  $body = jsonBody();
  $userId = trim((string)($body['userId'] ?? ''));

  // Token 鉴权
  $auth = authenticateOptional();
  if ($auth !== null && $auth['userId'] !== $userId) {
    respond(403, ['message' => '无权操作']);
  }
  if ($itemId === '' || $userId === '') {
    respond(400, ['message' => 'itemId/userId 不能为空']);
  }

  $pdo = db();
  $pdo->beginTransaction();
  try {
    $stmt0 = $pdo->prepare("SELECT 1 FROM market_favorites WHERE item_id = ? AND user_id = ? LIMIT 1");
    $stmt0->execute([$itemId, $userId]);
    $exists = $stmt0->fetch() ? true : false;
    if ($exists) {
      $stmt1 = $pdo->prepare("DELETE FROM market_favorites WHERE item_id = ? AND user_id = ?");
      $stmt1->execute([$itemId, $userId]);
      $favorited = false;
    } else {
      $stmt1 = $pdo->prepare("INSERT INTO market_favorites (item_id, user_id, created_at) VALUES (?, ?, ?)");
      $stmt1->execute([$itemId, $userId, now_ms()]);
      $favorited = true;
    }
    $pdo->commit();
    respond(200, ['itemId' => $itemId, 'favorited' => $favorited]);
  } catch (Exception $e) {
    $pdo->rollBack();
    logError('market favorites toggle', $e);
    respond(500, ['message' => '服务器错误']);
  }
}

if ($method === 'POST' && $path === '/market') {
  $body = jsonBody();

  $id = (string)($body['id'] ?? '');
  $title = trim($body['title'] ?? '');
  $description = trim($body['description'] ?? '');
  $price = $body['price'] ?? null;
  $sellerId = trim($body['sellerId'] ?? 'anonymous');
  $imageUrl = $body['imageUrl'] ?? null;
  $publishTime = $body['publishTime'] ?? null;

  // Token 鉴权
  $auth = authenticateOptional();
  if ($auth !== null && $auth['userId'] !== $sellerId) {
    respond(403, ['message' => '无权代他人发布']);
  }

  if ($id === '' || $title === '' || $description === '' || !is_numeric($price) || !is_numeric($publishTime)) {
    respond(400, ['message' => '字段不完整或类型错误']);
  }

  $status = isAdminUserId($sellerId) ? 'APPROVED' : 'PENDING';
  $stmt = db()->prepare("INSERT INTO market_items (id, title, description, price, seller_id, image_url, publish_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
  $stmt->execute([$id, $title, $description, $price, $sellerId, $imageUrl, (int)$publishTime, $status]);

  respond(200, [
    'id' => $id,
    'title' => $title,
    'description' => $description,
    'price' => (float)$price,
    'sellerId' => $sellerId,
    'imageUrl' => $imageUrl,
    'publishTime' => (int)$publishTime,
    'status' => $status,
  ]);
}

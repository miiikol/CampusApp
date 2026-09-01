<?php

/**
 * 二手市场模块：商品列表、发布、收藏切换
 */

// 列表：返回已审核商品，带 30 秒缓存；登录用户附带收藏状态
if ($method === 'GET' && $path === '/market') {

  $pg = parsePagination();
  $viewerId = trim((string)($_GET['userId'] ?? ''));

  // APCu 缓存：列表缓存 30 秒，降低数据库压力
  $cacheKey = cacheKey('/market', ['page' => $pg['page'], 'pageSize' => $pg['pageSize'], 'viewerId' => $viewerId]);
  $cached = cacheGet($cacheKey);
  if ($cached !== null) respond(200, $cached);

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
  $response = [
    'data' => $out,
    'page' => $pg['page'],
    'pageSize' => $pg['pageSize'],
    'total' => $total,
  ];
  cacheSet($cacheKey, $response, 30);
  respond(200, $response);
}

// 收藏/取消收藏：存在则删除，不存在则插入
if ($method === 'POST' && preg_match('#^/market/([^/]+)/favorites/toggle$#', $path, $matches)) {

  $itemId = trim((string)$matches[1]);
  $body = jsonBody();
  $userId = trim((string)($body['userId'] ?? ''));

  // 强制 Token 鉴权
  $auth = authenticate();
  if ($auth['userId'] !== $userId) {
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

// 发布商品：管理员直接通过，普通用户进入待审核
if ($method === 'POST' && $path === '/market') {
  $body = jsonBody();

  $id = (string)($body['id'] ?? '');
  $title = trim($body['title'] ?? '');
  $description = trim($body['description'] ?? '');
  $price = $body['price'] ?? null;
  $sellerId = trim($body['sellerId'] ?? 'anonymous');
  $imageUrl = $body['imageUrl'] ?? null;
  $publishTime = $body['publishTime'] ?? null;

  // 发布必须登录；卖家身份以 Token 为准，忽略客户端传入的 sellerId，防止伪造管理员身份绕过审核
  $auth = authenticate();
  $sellerId = $auth['userId'];

  if ($id === '' || $title === '' || $description === '' || !is_numeric($price) || !is_numeric($publishTime)) {
    respond(400, ['message' => '字段不完整或类型错误']);
  }

  // 价格必须为非负数且不超过合理上限
  $price = (float)$price;
  if ($price < 0 || $price > 100000000) {
    respond(400, ['message' => '价格不合法']);
  }

  // 管理员发布直接通过审核，普通用户待审核
  $status = isAdminUserId($sellerId) ? 'APPROVED' : 'PENDING';
  try {
    $stmt = db()->prepare("INSERT INTO market_items (id, title, description, price, seller_id, image_url, publish_time, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
    $stmt->execute([$id, $title, $description, $price, $sellerId, $imageUrl, (int)$publishTime, $status]);
  } catch (Exception $e) {
    logError('market publish', $e);
    respond(400, ['message' => '商品发布失败，请重试']);
  }

  // 写入后清除列表缓存
  cacheDeleteByPrefix('/market:');

  respond(200, [
    'id' => $id,
    'title' => $title,
    'description' => $description,
    'price' => $price,
    'sellerId' => $sellerId,
    'imageUrl' => $imageUrl,
    'publishTime' => (int)$publishTime,
    'status' => $status,
  ]);
}

<?php

/**
 * 失物招领模块：发布与列表查询
 */

// 列表：返回已审核通过的招领信息，带 30 秒缓存
if ($method === 'GET' && $path === '/lostfound') {
  $pg = parsePagination();

  // APCu 缓存：列表缓存 30 秒，降低数据库压力
  $cacheKey = cacheKey('/lostfound', ['page' => $pg['page'], 'pageSize' => $pg['pageSize']]);
  $cached = cacheGet($cacheKey);
  if ($cached !== null) respond(200, $cached);

  $stmt = db()->prepare("SELECT id, title, description, location, type, image_url, contact_info, publish_time, latitude, longitude FROM lost_found_items WHERE status = 'APPROVED' ORDER BY publish_time DESC LIMIT ? OFFSET ?");
  $stmt->execute([$pg['limit'], $pg['offset']]);
  $rows = $stmt->fetchAll();
  $totalStmt = db()->prepare("SELECT COUNT(*) AS cnt FROM lost_found_items WHERE status = 'APPROVED'");
  $totalStmt->execute();
  $total = (int)($totalStmt->fetch()['cnt'] ?? 0);
  $out = array_map(function($r) {
    return [
      'id' => (string)$r['id'],
      'title' => $r['title'],
      'description' => $r['description'],
      'location' => $r['location'],
      'type' => $r['type'],
      'imageUrl' => $r['image_url'],
      'contactInfo' => $r['contact_info'],
      'publishTime' => (int)$r['publish_time'],
      'latitude' => $r['latitude'] !== null ? (float)$r['latitude'] : null,
      'longitude' => $r['longitude'] !== null ? (float)$r['longitude'] : null,
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

// 发布招领信息：管理员直接通过，普通用户进入待审核
if ($method === 'POST' && $path === '/lostfound') {
  $body = jsonBody();

  $id = (string)($body['id'] ?? '');
  $title = trim($body['title'] ?? '');
  $description = trim($body['description'] ?? '');
  $location = trim($body['location'] ?? '');
  $type = trim($body['type'] ?? '');
  $imageUrl = $body['imageUrl'] ?? null;
  $contactInfo = $body['contactInfo'] ?? null;
  $ownerId = trim($body['ownerId'] ?? '');
  $publishTime = $body['publishTime'] ?? null;
  $latitude = $body['latitude'] ?? null;
  $longitude = $body['longitude'] ?? null;

  // 强制 Token 鉴权：发布者必须与 Token 主体一致
  $auth = authenticate();
  if ($auth['userId'] !== $ownerId) {
    respond(403, ['message' => '无权代他人发布']);
  }

  if ($id === '' || $title === '' || $description === '' || $location === '' || $type === '' || !is_numeric($publishTime)) {
    respond(400, ['message' => '字段不完整或类型错误']);
  }

  // 管理员发布直接通过审核，普通用户进入待审核
  $status = isAdminUserId($ownerId) ? 'APPROVED' : 'PENDING';

  try {
    $stmt = db()->prepare("INSERT INTO lost_found_items (id, title, description, location, type, image_url, contact_info, publish_time, latitude, longitude, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
    $stmt->execute([
      $id, $title, $description, $location, $type,
      $imageUrl, $contactInfo, (int)$publishTime,
      is_numeric($latitude) ? (float)$latitude : null,
      is_numeric($longitude) ? (float)$longitude : null,
      $status,
    ]);
  } catch (Exception $e) {
    logError('lostfound publish', $e);
    respond(400, ['message' => '发布失败，请重试']);
  }

  // 写入后清除列表缓存
  cacheDeleteByPrefix('/lostfound:');

  respond(200, [
    'id' => $id,
    'title' => $title,
    'description' => $description,
    'location' => $location,
    'type' => $type,
    'imageUrl' => $imageUrl,
    'contactInfo' => $contactInfo,
    'publishTime' => (int)$publishTime,
    'latitude' => is_numeric($latitude) ? (float)$latitude : null,
    'longitude' => is_numeric($longitude) ? (float)$longitude : null,
    'status' => $status,
  ]);
}

<?php

if ($method === 'GET' && $path === '/lostfound') {
  $pg = parsePagination();
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
  respond(200, [
    'data' => $out,
    'page' => $pg['page'],
    'pageSize' => $pg['pageSize'],
    'total' => $total,
  ]);
}

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

  // Token 鉴权
  $auth = authenticateOptional();
  if ($auth !== null && $auth['userId'] !== $ownerId) {
    respond(403, ['message' => '无权代他人发布']);
  }

  if ($id === '' || $title === '' || $description === '' || $location === '' || $type === '' || !is_numeric($publishTime)) {
    respond(400, ['message' => '字段不完整或类型错误']);
  }

  if ($ownerId === '') {
    $ownerId = is_string($contactInfo) ? trim($contactInfo) : '';
  }
  $status = isAdminUserId($ownerId) ? 'APPROVED' : 'PENDING';

  $stmt = db()->prepare("INSERT INTO lost_found_items (id, title, description, location, type, image_url, contact_info, publish_time, latitude, longitude, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
  $stmt->execute([
    $id, $title, $description, $location, $type,
    $imageUrl, $contactInfo, (int)$publishTime,
    is_numeric($latitude) ? (float)$latitude : null,
    is_numeric($longitude) ? (float)$longitude : null,
    $status,
  ]);

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

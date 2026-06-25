<?php

if ($method === 'GET' && $path === '/admin/reviews/pending') {
  // Token 鉴权（优先），兼容旧 adminId 参数
  $auth = authenticateOptional();
  $adminId = trim((string)($_GET['adminId'] ?? ''));
  if ($auth !== null) {
    if ($auth['role'] !== 'admin') respond(403, ['message' => '仅管理员可访问']);
  } else if (!isAdminUserId($adminId)) {
    respond(403, ['message' => '仅管理员可访问']);
  }

  $marketRows = db()->query("
    SELECT id, title, description AS summary, seller_id AS submitter_id, publish_time, 'MARKET' AS item_type
    FROM market_items
    WHERE status = 'PENDING'
    ORDER BY publish_time DESC
    LIMIT 200
  ")->fetchAll();
  $lostRows = db()->query("
    SELECT id, title, CONCAT('地点：', location, '；', description) AS summary, contact_info AS submitter_id, publish_time, 'LOSTFOUND' AS item_type
    FROM lost_found_items
    WHERE status = 'PENDING'
    ORDER BY publish_time DESC
    LIMIT 200
  ")->fetchAll();

  $merged = array_merge($marketRows, $lostRows);
  usort($merged, function($a, $b) {
    return (int)$b['publish_time'] <=> (int)$a['publish_time'];
  });
  $merged = array_slice($merged, 0, 300);

  $out = array_map(function($r) {
    return [
      'itemType' => (string)$r['item_type'],
      'itemId' => (string)$r['id'],
      'title' => (string)$r['title'],
      'summary' => (string)$r['summary'],
      'submitterId' => (string)($r['submitter_id'] ?? ''),
      'publishTime' => (int)$r['publish_time']
    ];
  }, $merged);
  respond(200, $out);
}

if ($method === 'POST' && $path === '/admin/reviews/action') {
  $body = jsonBody();
  $adminId = trim((string)($body['adminId'] ?? ''));
  $itemType = strtoupper(trim((string)($body['itemType'] ?? '')));
  $itemId = trim((string)($body['itemId'] ?? ''));
  $action = strtoupper(trim((string)($body['action'] ?? '')));

  // Token 鉴权（优先），兼容旧 adminId 参数
  $auth = authenticateOptional();
  if ($auth !== null) {
    if ($auth['role'] !== 'admin') respond(403, ['message' => '仅管理员可操作']);
  } else if (!isAdminUserId($adminId)) {
    respond(403, ['message' => '仅管理员可操作']);
  }
  if ($itemType === '' || $itemId === '' || ($action !== 'APPROVE' && $action !== 'REJECT')) {
    respond(400, ['message' => 'adminId/itemType/itemId/action 参数错误']);
  }

  $table = null;
  if ($itemType === 'MARKET') $table = 'market_items';
  if ($itemType === 'LOSTFOUND') $table = 'lost_found_items';
  if ($table === null) {
    respond(400, ['message' => '不支持的审核类型']);
  }

  $targetStatus = $action === 'APPROVE' ? 'APPROVED' : 'REJECTED';
  $stmt = db()->prepare("UPDATE {$table} SET status = ? WHERE id = ? AND status = 'PENDING'");
  $stmt->execute([$targetStatus, $itemId]);
  if ($stmt->rowCount() === 0) {
    respond(404, ['message' => '记录不存在或已审核']);
  }
  respond(200, ['message' => '操作成功', 'status' => $targetStatus]);
}

if ($method === 'POST' && preg_match('#^/admin/comments/([^/]+)/ban$#', $path, $matches)) {

  $commentId = trim((string)$matches[1]);
  $body = jsonBody();
  $adminId = trim((string)($body['adminId'] ?? ''));
  $reason = trim((string)($body['reason'] ?? '违规内容'));

  // Token 鉴权（优先），兼容旧 adminId 参数
  $auth = authenticateOptional();
  if ($auth !== null) {
    if ($auth['role'] !== 'admin') respond(403, ['message' => '仅管理员可操作']);
  } else if (!isAdminUserId($adminId)) {
    respond(403, ['message' => '仅管理员可操作']);
  }
  if ($commentId === '') {
    respond(400, ['message' => 'commentId 不能为空']);
  }

  $stmt = db()->prepare("UPDATE news_comments SET status = 'BANNED', banned_by = ?, ban_reason = ?, ban_time = ? WHERE id = ? AND status <> 'BANNED'");
  $stmt->execute([$adminId, $reason, now_ms(), $commentId]);
  if ($stmt->rowCount() === 0) {
    respond(404, ['message' => '评论不存在或已封禁']);
  }
  respond(200, ['message' => '评论已封禁']);
}

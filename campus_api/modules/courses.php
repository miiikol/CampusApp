<?php

function buildCourseOutputRow($id, $name, $room, $teacher, $dayOfWeek, $startSection, $endSection, $weekRange, $color = -1, $isRemote = true, $baseCourseId = 0, $onlyWeek = 0) {
  return [
    'id' => (int)$id,
    'name' => (string)$name,
    'room' => (string)$room,
    'teacher' => (string)$teacher,
    'dayOfWeek' => (int)$dayOfWeek,
    'startSection' => (int)$startSection,
    'endSection' => (int)$endSection,
    'weekRange' => (string)$weekRange,
    'color' => (int)$color,
    'isRemote' => (bool)$isRemote,
    'baseCourseId' => (int)$baseCourseId,
    'onlyWeek' => (int)$onlyWeek,
  ];
}

function buildMergedCoursesForUser($userId) {



  $stmt = db()->prepare("
    SELECT c.id, c.name, c.room, c.teacher, c.day_of_week, c.start_section, c.end_section, c.week_range
    FROM courses c
    INNER JOIN course_assignments a ON a.course_id = c.id
    WHERE a.user_id = ?
    ORDER BY c.day_of_week, c.start_section, c.id
  ");
  $stmt->execute([$userId]);
  $baseRows = $stmt->fetchAll();

  $stmt2 = db()->prepare("
    SELECT id, user_id, source_course_id, name, room, teacher, day_of_week, start_section, end_section, week_range, color, only_week
    FROM user_course_customizations
    WHERE user_id = ?
    ORDER BY id ASC
  ");
  $stmt2->execute([$userId]);
  $customRows = $stmt2->fetchAll();

  $baseLikeRows = [];
  foreach ($baseRows as $row) {
    $courseId = (int)$row['id'];
    $baseLikeRows[$courseId] = buildCourseOutputRow(
      $courseId,
      $row['name'],
      $row['room'],
      $row['teacher'],
      $row['day_of_week'],
      $row['start_section'],
      $row['end_section'],
      $row['week_range'],
      -1,
      true,
      0,
      0
    );
  }

  $allWeekOverrides = [];
  $weekOverrides = [];
  $standaloneWeekRows = [];

  foreach ($customRows as $row) {
    $customId = (int)$row['id'];
    $sourceCourseId = (int)($row['source_course_id'] ?? 0);
    $onlyWeek = (int)($row['only_week'] ?? 0);

    if ($sourceCourseId === 0 && $onlyWeek === 0) {
      $baseLikeRows[$customId] = buildCourseOutputRow(
        $customId,
        $row['name'],
        $row['room'],
        $row['teacher'],
        $row['day_of_week'],
        $row['start_section'],
        $row['end_section'],
        $row['week_range'],
        (int)($row['color'] ?? -1),
        false,
        0,
        0
      );
      continue;
    }

    if ($sourceCourseId === 0) {
      $standaloneWeekRows[] = buildCourseOutputRow(
        $customId,
        $row['name'],
        $row['room'],
        $row['teacher'],
        $row['day_of_week'],
        $row['start_section'],
        $row['end_section'],
        $row['week_range'],
        (int)($row['color'] ?? -1),
        false,
        0,
        $onlyWeek
      );
      continue;
    }

    if ($onlyWeek === 0) {
      $allWeekOverrides[$sourceCourseId] = buildCourseOutputRow(
        $sourceCourseId,
        $row['name'],
        $row['room'],
        $row['teacher'],
        $row['day_of_week'],
        $row['start_section'],
        $row['end_section'],
        $row['week_range'],
        (int)($row['color'] ?? -1),
        false,
        0,
        0
      );
      continue;
    }

    $weekOverrides[] = buildCourseOutputRow(
      $customId,
      $row['name'],
      $row['room'],
      $row['teacher'],
      $row['day_of_week'],
      $row['start_section'],
      $row['end_section'],
      $row['week_range'],
      (int)($row['color'] ?? -1),
      false,
      $sourceCourseId,
      $onlyWeek
    );
  }

  $out = [];
  foreach ($baseLikeRows as $courseId => $row) {
    if (isset($allWeekOverrides[$courseId])) {
      $out[] = $allWeekOverrides[$courseId];
    } else {
      $out[] = $row;
    }
  }
  foreach ($weekOverrides as $row) {
    $out[] = $row;
  }
  foreach ($standaloneWeekRows as $row) {
    $out[] = $row;
  }

  usort($out, function($a, $b) {
    $cmp1 = ((int)$a['dayOfWeek']) <=> ((int)$b['dayOfWeek']);
    if ($cmp1 !== 0) return $cmp1;
    $cmp2 = ((int)$a['startSection']) <=> ((int)$b['startSection']);
    if ($cmp2 !== 0) return $cmp2;
    return ((int)$a['id']) <=> ((int)$b['id']);
  });

  return $out;
}

if ($method === 'GET' && $path === '/courses') {



  $userId = trim((string)($_GET['userId'] ?? ''));
  $studentId = trim((string)($_GET['studentId'] ?? ''));

  // Token 鉴权
  $auth = authenticateOptional();
  if ($auth !== null && $userId !== '') {
    if ($auth['userId'] !== $userId) {
      respond(403, ['message' => '无权查看此用户的课表']);
    }
  }

  if ($userId === '' && $studentId === '') {
    respond(400, ['message' => 'userId/studentId 不能为空']);
  }

  if ($userId === '' && $studentId !== '') {
    $stmtUser = db()->prepare('SELECT id FROM users WHERE student_id = ? LIMIT 1');
    $stmtUser->execute([$studentId]);
    $userRow = $stmtUser->fetch();
    if ($userRow) {
      $userId = (string)$userRow['id'];
    }
  }

  if ($userId === '' || isAdminUserId($userId)) {
    respond(403, ['message' => '当前账号不支持课表查询']);
  }

  respond(200, buildMergedCoursesForUser($userId));
}

if ($method === 'POST' && $path === '/courses/customize') {



  $body = jsonBody();
  $userId = trim((string)($body['userId'] ?? ''));

  // Token 鉴权
  $auth = authenticateOptional();
  if ($auth !== null) {
    if ($userId === '' || $auth['userId'] !== $userId) {
      respond(403, ['message' => '无权操作此用户的课表']);
    }
  }
  $sourceCourseId = isset($body['sourceCourseId']) ? (int)$body['sourceCourseId'] : 0;
  $scope = strtoupper(trim((string)($body['scope'] ?? 'ALL_WEEKS')));
  $week = isset($body['week']) ? (int)$body['week'] : 0;
  $name = trim((string)($body['name'] ?? ''));
  $room = trim((string)($body['room'] ?? ''));
  $teacher = trim((string)($body['teacher'] ?? ''));
  $dayOfWeek = isset($body['dayOfWeek']) ? (int)$body['dayOfWeek'] : 0;
  $startSection = isset($body['startSection']) ? (int)$body['startSection'] : 0;
  $endSection = isset($body['endSection']) ? (int)$body['endSection'] : 0;
  $weekRange = trim((string)($body['weekRange'] ?? ''));
  $color = isset($body['color']) ? (int)$body['color'] : -1;

  if ($userId === '' || $name === '' || $dayOfWeek < 1 || $dayOfWeek > 7 || $startSection < 1 || $startSection > 12 || $endSection < $startSection || $endSection > 12) {
    respond(400, ['message' => '课程参数不完整或格式错误']);
  }
  if ($scope !== 'ALL_WEEKS' && $scope !== 'THIS_WEEK') {
    respond(400, ['message' => 'scope 参数错误']);
  }
  if ($scope === 'THIS_WEEK' && $week <= 0) {
    respond(400, ['message' => 'week 参数错误']);
  }
  if ($weekRange === '') {
    $weekRange = $scope === 'THIS_WEEK' ? (string)$week : '1-16';
  }

  $pdo = db();
  $pdo->beginTransaction();
  try {
    $effectiveOnlyWeek = $scope === 'THIS_WEEK' ? $week : 0;

    $existingCustom = null;
    if ($sourceCourseId > 0) {
      $stmt0 = $pdo->prepare("SELECT id, source_course_id, only_week FROM user_course_customizations WHERE id = ? AND user_id = ? LIMIT 1");
      $stmt0->execute([$sourceCourseId, $userId]);
      $existingCustom = $stmt0->fetch();
    }

    if ($existingCustom) {
      $stmt1 = $pdo->prepare("
        UPDATE user_course_customizations
        SET name = ?, room = ?, teacher = ?, day_of_week = ?, start_section = ?, end_section = ?, week_range = ?, color = ?, only_week = ?, updated_at = ?
        WHERE id = ? AND user_id = ?
      ");
      $stmt1->execute([
        $name, $room, $teacher, $dayOfWeek, $startSection, $endSection, $weekRange, $color,
        $effectiveOnlyWeek, now_ms(), (int)$existingCustom['id'], $userId
      ]);
    } else if ($sourceCourseId > 0) {
      $stmt1 = $pdo->prepare("
        INSERT INTO user_course_customizations
        (user_id, source_course_id, name, room, teacher, day_of_week, start_section, end_section, week_range, color, only_week, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          name = VALUES(name),
          room = VALUES(room),
          teacher = VALUES(teacher),
          day_of_week = VALUES(day_of_week),
          start_section = VALUES(start_section),
          end_section = VALUES(end_section),
          week_range = VALUES(week_range),
          color = VALUES(color),
          updated_at = VALUES(updated_at)
      ");
      $stmt1->execute([
        $userId, $sourceCourseId, $name, $room, $teacher, $dayOfWeek, $startSection, $endSection, $weekRange, $color,
        $effectiveOnlyWeek, now_ms(), now_ms()
      ]);
    } else {
      $stmt1 = $pdo->prepare("
        INSERT INTO user_course_customizations
        (user_id, source_course_id, name, room, teacher, day_of_week, start_section, end_section, week_range, color, only_week, created_at, updated_at)
        VALUES (?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ");
      $stmt1->execute([
        $userId, $name, $room, $teacher, $dayOfWeek, $startSection, $endSection, $weekRange, $color,
        $effectiveOnlyWeek, now_ms(), now_ms()
      ]);
    }

    $pdo->commit();
    respond(200, ['message' => 'ok']);
  } catch (Exception $e) {
    $pdo->rollBack();
    logError('courses customize', $e);
    respond(500, ['message' => '服务器错误']);
  }
}

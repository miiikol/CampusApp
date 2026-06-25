<?php

function ensureDemoUser($studentId, $username, $fullName, $idCardNo, $avatarUrl, $password, $role = 'student') {
  ensureUsersProfileColumns();

  $stmt = db()->prepare('SELECT id, password_hash FROM users WHERE student_id = ? LIMIT 1');
  $stmt->execute([$studentId]);
  $row = $stmt->fetch();

  if ($row) {
    $passwordHash = trim((string)($row['password_hash'] ?? ''));
    if ($passwordHash === '') {
      $stmt2 = db()->prepare('UPDATE users SET username = ?, full_name = ?, id_card_no = ?, avatar_url = ?, role = ?, password_hash = ? WHERE id = ?');
      $stmt2->execute([$username, $fullName, $idCardNo, $avatarUrl, $role, password_hash($password, PASSWORD_DEFAULT), $row['id']]);
    } else {
      $stmt2 = db()->prepare('UPDATE users SET username = ?, full_name = ?, id_card_no = ?, avatar_url = ?, role = ? WHERE id = ?');
      $stmt2->execute([$username, $fullName, $idCardNo, $avatarUrl, $role, $row['id']]);
    }
    return (string)$row['id'];
  }

  $stmt3 = db()->prepare('INSERT INTO users (student_id, username, full_name, id_card_no, password_hash, role, avatar_url) VALUES (?, ?, ?, ?, ?, ?, ?)');
  $stmt3->execute([$studentId, $username, $fullName, $idCardNo, password_hash($password, PASSWORD_DEFAULT), $role, $avatarUrl]);

  $stmt4 = db()->prepare('SELECT id FROM users WHERE student_id = ? LIMIT 1');
  $stmt4->execute([$studentId]);
  $created = $stmt4->fetch();
  return $created ? (string)$created['id'] : '';
}

function ensureDemoUsersSeeded() {
  $students = [
    [
      'studentId' => '20260001',
      'username' => '张明远',
      'fullName' => '张明远',
      'idCardNo' => '110105200402150011',
      'avatarUrl' => null,
      'password' => '123456',
      'role' => 'student',
    ],
    [
      'studentId' => '20260002',
      'username' => '李思涵',
      'fullName' => '李思涵',
      'idCardNo' => '320311200403214526',
      'avatarUrl' => null,
      'password' => '123456',
      'role' => 'student',
    ],
    [
      'studentId' => '20260003',
      'username' => '王泽宇',
      'fullName' => '王泽宇',
      'idCardNo' => '370983200401087213',
      'avatarUrl' => null,
      'password' => '123456',
      'role' => 'student',
    ],
    [
      'studentId' => '20260004',
      'username' => '陈雨桐',
      'fullName' => '陈雨桐',
      'idCardNo' => '430524200404196428',
      'avatarUrl' => null,
      'password' => '123456',
      'role' => 'student',
    ],
  ];

  $out = [];
  foreach ($students as $student) {
    $userId = ensureDemoUser(
      $student['studentId'],
      $student['username'],
      $student['fullName'],
      $student['idCardNo'],
      $student['avatarUrl'],
      $student['password'],
      $student['role']
    );
    if ($userId !== '') {
      $out[$student['studentId']] = [
        'id' => $userId,
        'username' => $student['username'],
        'fullName' => $student['fullName'],
      ];
    }
  }
  return $out;
}

function upsertDemoCourse($course) {
  $stmt = db()->prepare('SELECT id FROM courses WHERE id = ? LIMIT 1');
  $stmt->execute([$course['id']]);
  $row = $stmt->fetch();

  if ($row) {
    $stmt2 = db()->prepare('UPDATE courses SET name = ?, room = ?, teacher = ?, day_of_week = ?, start_section = ?, end_section = ?, week_range = ? WHERE id = ?');
    $stmt2->execute([
      $course['name'],
      $course['room'],
      $course['teacher'],
      $course['dayOfWeek'],
      $course['startSection'],
      $course['endSection'],
      $course['weekRange'],
      $course['id']
    ]);
    return;
  }

  $stmt3 = db()->prepare('INSERT INTO courses (id, name, room, teacher, day_of_week, start_section, end_section, week_range) VALUES (?, ?, ?, ?, ?, ?, ?, ?)');
  $stmt3->execute([
    $course['id'],
    $course['name'],
    $course['room'],
    $course['teacher'],
    $course['dayOfWeek'],
    $course['startSection'],
    $course['endSection'],
    $course['weekRange']
  ]);
}

function ensureDemoCoursesSeeded($usersByStudentId) {
  ensureCourseAssignmentsTable();

  $courses = [
    ['id' => 26000101, 'name' => '高等数学A(下)', 'room' => '知行楼A-201', 'teacher' => '刘建国', 'dayOfWeek' => 1, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '1-16', 'studentIds' => ['20260001']],
    ['id' => 26000102, 'name' => 'Java程序设计', 'room' => '信工楼B-403', 'teacher' => '周颖', 'dayOfWeek' => 1, 'startSection' => 3, 'endSection' => 4, 'weekRange' => '1-16', 'studentIds' => ['20260001']],
    ['id' => 26000103, 'name' => '数据结构', 'room' => '信工楼C-205', 'teacher' => '孙立峰', 'dayOfWeek' => 2, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '1-16', 'studentIds' => ['20260001']],
    ['id' => 26000104, 'name' => '大学英语III', 'room' => '外语楼302', 'teacher' => '何晓婷', 'dayOfWeek' => 3, 'startSection' => 5, 'endSection' => 6, 'weekRange' => '1-16', 'studentIds' => ['20260001']],
    ['id' => 26000105, 'name' => 'Android移动应用开发', 'room' => '实验中心2-501', 'teacher' => '蒋晨', 'dayOfWeek' => 4, 'startSection' => 3, 'endSection' => 5, 'weekRange' => '1-16', 'studentIds' => ['20260001']],
    ['id' => 26000106, 'name' => '形势与政策', 'room' => '公共教学楼108', 'teacher' => '王敏', 'dayOfWeek' => 5, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '2-15', 'studentIds' => ['20260001']],

    ['id' => 26000201, 'name' => '数据库原理', 'room' => '信工楼A-305', 'teacher' => '姜雪', 'dayOfWeek' => 1, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '1-16', 'studentIds' => ['20260002']],
    ['id' => 26000202, 'name' => '计算机网络', 'room' => '信工楼B-204', 'teacher' => '邓海涛', 'dayOfWeek' => 2, 'startSection' => 3, 'endSection' => 4, 'weekRange' => '1-16', 'studentIds' => ['20260002']],
    ['id' => 26000203, 'name' => '操作系统', 'room' => '信工楼B-408', 'teacher' => '郑薇', 'dayOfWeek' => 3, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '1-16', 'studentIds' => ['20260002']],
    ['id' => 26000204, 'name' => 'Web前端开发实践', 'room' => '实验中心1-306', 'teacher' => '高宁', 'dayOfWeek' => 4, 'startSection' => 5, 'endSection' => 6, 'weekRange' => '1-16', 'studentIds' => ['20260002']],
    ['id' => 26000205, 'name' => '概率论与数理统计', 'room' => '理科楼204', 'teacher' => '赵宏伟', 'dayOfWeek' => 5, 'startSection' => 3, 'endSection' => 4, 'weekRange' => '1-16', 'studentIds' => ['20260002']],
    ['id' => 26000206, 'name' => '大学生职业发展与就业指导', 'room' => '公共教学楼205', 'teacher' => '段欣', 'dayOfWeek' => 5, 'startSection' => 7, 'endSection' => 8, 'weekRange' => '1-8', 'studentIds' => ['20260002']],

    ['id' => 26000301, 'name' => 'Python数据分析', 'room' => '实验中心3-402', 'teacher' => '吴彤', 'dayOfWeek' => 1, 'startSection' => 3, 'endSection' => 4, 'weekRange' => '1-16', 'studentIds' => ['20260003']],
    ['id' => 26000302, 'name' => '人工智能导论', 'room' => '信工楼C-501', 'teacher' => '陈嘉铭', 'dayOfWeek' => 2, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '1-16', 'studentIds' => ['20260003']],
    ['id' => 26000303, 'name' => '软件工程', 'room' => '信工楼A-406', 'teacher' => '付蕾', 'dayOfWeek' => 3, 'startSection' => 3, 'endSection' => 4, 'weekRange' => '1-16', 'studentIds' => ['20260003']],
    ['id' => 26000304, 'name' => '离散数学', 'room' => '理科楼106', 'teacher' => '韩磊', 'dayOfWeek' => 4, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '1-16', 'studentIds' => ['20260003']],
    ['id' => 26000305, 'name' => '机器学习案例分析', 'room' => '实验中心2-604', 'teacher' => '梁璐', 'dayOfWeek' => 4, 'startSection' => 7, 'endSection' => 8, 'weekRange' => '1-12', 'studentIds' => ['20260003']],
    ['id' => 26000306, 'name' => '创新创业基础', 'room' => '综合楼208', 'teacher' => '潘洁', 'dayOfWeek' => 5, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '1-8', 'studentIds' => ['20260003']],

    ['id' => 26000401, 'name' => '软件测试', 'room' => '信工楼B-301', 'teacher' => '罗旭', 'dayOfWeek' => 1, 'startSection' => 5, 'endSection' => 6, 'weekRange' => '1-16', 'studentIds' => ['20260004']],
    ['id' => 26000402, 'name' => '信息系统分析与设计', 'room' => '信工楼A-205', 'teacher' => '史媛', 'dayOfWeek' => 2, 'startSection' => 1, 'endSection' => 3, 'weekRange' => '1-16', 'studentIds' => ['20260004']],
    ['id' => 26000403, 'name' => 'UI交互设计基础', 'room' => '艺术楼401', 'teacher' => '唐琳', 'dayOfWeek' => 3, 'startSection' => 5, 'endSection' => 6, 'weekRange' => '1-16', 'studentIds' => ['20260004']],
    ['id' => 26000404, 'name' => '移动产品策划', 'room' => '实验中心1-202', 'teacher' => '徐洋', 'dayOfWeek' => 4, 'startSection' => 3, 'endSection' => 4, 'weekRange' => '1-16', 'studentIds' => ['20260004']],
    ['id' => 26000405, 'name' => '项目管理', 'room' => '综合楼312', 'teacher' => '冯岚', 'dayOfWeek' => 5, 'startSection' => 1, 'endSection' => 2, 'weekRange' => '1-16', 'studentIds' => ['20260004']],
    ['id' => 26000406, 'name' => '数字媒体技术概论', 'room' => '艺术楼205', 'teacher' => '彭悦', 'dayOfWeek' => 5, 'startSection' => 5, 'endSection' => 6, 'weekRange' => '1-12', 'studentIds' => ['20260004']],
  ];

  $demoCourseIds = array_map(function($course) {
    return (int)$course['id'];
  }, $courses);

  if (!empty($demoCourseIds)) {
    $placeholders = implode(',', array_fill(0, count($demoCourseIds), '?'));
    $stmtClear = db()->prepare("DELETE FROM course_assignments WHERE course_id IN ($placeholders)");
    $stmtClear->execute($demoCourseIds);
  }

  foreach ($courses as $course) {
    upsertDemoCourse($course);
    foreach ($course['studentIds'] as $studentId) {
      $user = $usersByStudentId[$studentId] ?? null;
      if (!$user) continue;
      $stmt = db()->prepare('INSERT INTO course_assignments (user_id, course_id, created_at) VALUES (?, ?, ?)');
      $stmt->execute([(string)$user['id'], (int)$course['id'], now_ms()]);
    }
  }
}

function upsertDemoNews($news) {
  $stmt = db()->prepare('SELECT id FROM news WHERE id = ? LIMIT 1');
  $stmt->execute([$news['id']]);
  $row = $stmt->fetch();

  if ($row) {
    $stmt2 = db()->prepare('UPDATE news SET title = ?, summary = ?, content = ?, image_url = ?, publish_date = ?, type = ? WHERE id = ?');
    $stmt2->execute([
      $news['title'],
      $news['summary'],
      $news['content'],
      $news['imageUrl'],
      $news['publishDate'],
      $news['type'],
      $news['id']
    ]);
    return;
  }

  $stmt3 = db()->prepare('INSERT INTO news (id, title, summary, content, image_url, publish_date, type) VALUES (?, ?, ?, ?, ?, ?, ?)');
  $stmt3->execute([
    $news['id'],
    $news['title'],
    $news['summary'],
    $news['content'],
    $news['imageUrl'],
    $news['publishDate'],
    $news['type']
  ]);
}

function ensureDemoNewsSeeded() {
  $newsList = [
    [
      'id' => 1001,
      'title' => '图书馆24小时自习区下周起试运行，新增120个夜间学习席位',
      'summary' => '学校图书馆将于5月13日起试运行24小时自习区，位于图书馆东侧一层，支持刷校园卡进出，并配备饮水、插座和安保值守。',
      'content' => "为更好地服务备战四六级、考研和毕业设计的同学，图书馆决定自5月13日起试运行24小时自习区。\n\n试运行区域位于图书馆东侧一层，共开放120个座位，配置独立照明、插座、饮水点和夜间巡查安保。23:00后仍需保持安静，不得外放音频、占座过夜或携带刺激性气味食品进入。\n\n图书馆方面表示，试运行两周后将根据预约率、卫生情况和同学反馈决定是否扩大开放范围。后续还计划接入座位预约和余位实时查询功能。",
      'imageUrl' => null,
      'publishDate' => 1778263200000,
      'type' => '校园服务',
    ],
    [
      'id' => 1002,
      'title' => '信息工程学院公布2026届毕业设计中期检查安排',
      'summary' => '中期检查将于5月18日至5月20日进行，检查内容包括系统演示、文档规范、功能完成度与答辩准备情况。',
      'content' => "信息工程学院发布《关于开展2026届本科毕业设计中期检查的通知》。本次检查对象为2026届全体毕业设计学生，检查时间为5月18日至5月20日。\n\n检查重点包括：课题任务完成进度、系统核心功能实现情况、数据库与接口设计、论文初稿完成度、演示材料准备情况。学院要求学生在检查前完成至少70%的核心功能，并准备3至5分钟的现场演示。\n\n指导教师将重点关注项目真实性、数据完整性、界面交互逻辑以及后续优化计划。学院提醒同学们注意材料命名规范，按班级统一提交检查表和阶段性总结。",
      'imageUrl' => null,
      'publishDate' => 1778176800000,
      'type' => '教学通知',
    ],
    [
      'id' => 1003,
      'title' => '学校举办春季实习双选会，78家企业提供超1600个岗位',
      'summary' => '春季实习双选会将于本周六在体育馆举行，涵盖软件开发、产品运营、测试、数据分析和行政管理等多个方向。',
      'content' => "2026年春季校园实习双选会将于本周六9:00至15:00在学校体育馆举行，共有78家企事业单位参会，提供岗位超过1600个。\n\n本次双选会聚焦信息技术、智能制造、新媒体、电商运营、教育培训等领域。其中，软件开发、测试工程师、产品助理和数据分析类岗位需求明显增长。招生就业处建议同学们提前准备1页纸简历，并着装整洁，现场可同步参加企业宣讲和简历初筛。\n\n体育馆入口将设置咨询台、简历打印点和政策答疑专区，学院辅导员也会在现场提供求职指导。",
      'imageUrl' => null,
      'publishDate' => 1778090400000,
      'type' => '就业信息',
    ],
    [
      'id' => 1004,
      'title' => '“移动应用创新实践周”报名启动，面向全校招募开发与设计同学',
      'summary' => '活动为期5天，包含产品策划、接口联调、UI评审和路演展示，优秀作品可推荐参加省级创新创业项目。',
      'content' => "由创新创业学院与信息工程学院联合举办的「移动应用创新实践周」现已开放报名。活动将于5月20日至5月24日举行，面向全校本科生和研究生开放。\n\n本次实践周采用组队形式进行，鼓励产品、设计、前端、Android、后端同学跨专业协作。活动期间将安排企业导师进行需求拆解、项目管理、原型评审和Demo路演辅导。优秀团队将获得校级证书，并优先推荐申报大学生创新创业训练计划项目。\n\n报名截止时间为5月16日18:00，参赛学生需提交团队成员信息、项目方向和初步功能设想。",
      'imageUrl' => null,
      'publishDate' => 1778004000000,
      'type' => '学术活动',
    ],
    [
      'id' => 1005,
      'title' => '校园网本周末凌晨分区域维护，宿舍与实验楼网络将短时中断',
      'summary' => '网络与信息中心将于周六凌晨0:30至4:30进行核心交换设备维护，部分宿舍区和实验楼网络预计短时不可用。',
      'content' => "网络与信息中心发布通知，为提升校园网络稳定性，计划于本周六0:30至4:30对核心交换设备与认证平台进行例行维护。\n\n维护期间，南苑、北苑部分宿舍楼及实验中心1号楼、2号楼网络可能出现间歇性中断，校园门户、选课系统和部分校内服务访问速度可能受到影响。请有线上提交作业、远程面试或服务器维护需求的师生提前做好安排。\n\n如维护提前结束，相关服务将即时恢复。若维护后仍出现无法认证或掉线频繁的问题，可通过网络中心值班电话报修。",
      'imageUrl' => null,
      'publishDate' => 1777917600000,
      'type' => '后勤公告',
    ],
  ];

  foreach ($newsList as $news) {
    upsertDemoNews($news);
  }
}

function ensureDemoCommentsForNews($newsId, $comments) {
  ensureNewsCommentsTable();
  $stmt = db()->prepare('SELECT COUNT(*) AS cnt FROM news_comments WHERE news_id = ?');
  $stmt->execute([(string)$newsId]);
  $row = $stmt->fetch();
  if (((int)($row['cnt'] ?? 0)) > 0) {
    return;
  }

  $inserted = [];
  foreach ($comments as $comment) {
    $parentIdValue = null;
    $replyToUserId = null;
    $replyToUsername = null;
    if (!empty($comment['parentKey']) && isset($inserted[$comment['parentKey']])) {
      $parent = $inserted[$comment['parentKey']];
      $parentIdValue = $parent['id'];
      $replyToUserId = $parent['userId'];
      $replyToUsername = $parent['username'];
    }

    $stmt2 = db()->prepare("INSERT INTO news_comments (news_id, user_id, username, content, parent_comment_id, reply_to_user_id, reply_to_username, status, publish_time) VALUES (?, ?, ?, ?, ?, ?, ?, 'NORMAL', ?)");
    $stmt2->execute([
      (string)$newsId,
      (string)$comment['userId'],
      (string)$comment['username'],
      (string)$comment['content'],
      $parentIdValue,
      $replyToUserId,
      $replyToUsername,
      (int)$comment['publishTime']
    ]);

    $inserted[$comment['key']] = [
      'id' => (int)db()->lastInsertId(),
      'userId' => (string)$comment['userId'],
      'username' => (string)$comment['username'],
    ];
  }
}

function ensureDemoNewsCommentsSeeded($usersByStudentId) {
  ensureNewsCommentsTable();
  ensureNewsLikesTables();

  $u1 = $usersByStudentId['20260001'] ?? null;
  $u2 = $usersByStudentId['20260002'] ?? null;
  $u3 = $usersByStudentId['20260003'] ?? null;
  $u4 = $usersByStudentId['20260004'] ?? null;

  if (!$u1 || !$u2 || !$u3 || !$u4) return;

  ensureDemoCommentsForNews('1001', [
    [
      'key' => 'c1',
      'userId' => $u1['id'],
      'username' => $u1['username'],
      'content' => '这个自习区来得太及时了，我们宿舍晚上总有人打游戏，最近赶毕设正愁没地方安静写代码。',
      'publishTime' => 1778266800000,
    ],
    [
      'key' => 'c2',
      'userId' => $u2['id'],
      'username' => $u2['username'],
      'content' => '想问一下会不会后面接入座位预约，不然考试周可能还是比较难抢到位置。',
      'publishTime' => 1778267700000,
    ],
    [
      'key' => 'c3',
      'userId' => $u3['id'],
      'username' => $u3['username'],
      'content' => '看通知说后续会根据试运行情况接入余位查询，我感觉如果开放预约就更方便了。',
      'publishTime' => 1778268600000,
      'parentKey' => 'c2',
    ],
    [
      'key' => 'c4',
      'userId' => $u4['id'],
      'username' => $u4['username'],
      'content' => '希望多装一点插座，上学期图书馆靠墙的位置经常插满，做设计稿的时候电脑特别费电。',
      'publishTime' => 1778269800000,
    ],
  ]);

  ensureDemoCommentsForNews('1002', [
    [
      'key' => 'c1',
      'userId' => $u1['id'],
      'username' => $u1['username'],
      'content' => '中期检查重点看功能完成度，这次我准备把登录、课表、资讯和评论这些核心模块都跑通再去展示。',
      'publishTime' => 1778180400000,
    ],
    [
      'key' => 'c2',
      'userId' => $u4['id'],
      'username' => $u4['username'],
      'content' => '我们老师还强调了数据不能太空，要有真实一点的内容和交互细节，感觉这个要求挺关键的。',
      'publishTime' => 1778181600000,
    ],
    [
      'key' => 'c3',
      'userId' => $u2['id'],
      'username' => $u2['username'],
      'content' => '对，我学长答辩的时候老师就问了"如果换一个学生登录，为什么所有数据都一样"，所以最好把账号差异做出来。',
      'publishTime' => 1778182500000,
      'parentKey' => 'c2',
    ],
    [
      'key' => 'c4',
      'userId' => $u3['id'],
      'username' => $u3['username'],
      'content' => '建议大家把演示流程提前排练一下，尤其是弱网环境或者接口第一次初始化的时候，最好也考虑进去。',
      'publishTime' => 1778183400000,
    ],
  ]);

  ensureDemoCommentsForNews('1004', [
    [
      'key' => 'c1',
      'userId' => $u3['id'],
      'username' => $u3['username'],
      'content' => '这个活动适合练完整项目流程，尤其是接口联调和路演展示，对做移动端方向的同学帮助挺大。',
      'publishTime' => 1778007600000,
    ],
    [
      'key' => 'c2',
      'userId' => $u1['id'],
      'username' => $u1['username'],
      'content' => '如果有 Android 和后端同学组队的话，确实可以把一个小型校园项目从原型做到可演示版本。',
      'publishTime' => 1778008500000,
      'parentKey' => 'c1',
    ],
    [
      'key' => 'c3',
      'userId' => $u2['id'],
      'username' => $u2['username'],
      'content' => '我更关心企业导师会不会真的看代码质量，如果会的话，感觉这次活动含金量就挺高。',
      'publishTime' => 1778009400000,
    ],
  ]);
}

function ensureDemoContentSeeded() {
  ensureAppMetaTable();
  $flag = getMetaValue('demo_seeded');
  if ($flag === '1') return;

  $pdo = db();
  $pdo->beginTransaction();
  try {
    $usersByStudentId = ensureDemoUsersSeeded();
    ensureDemoCoursesSeeded($usersByStudentId);
    ensureDemoNewsSeeded();
    ensureDemoNewsCommentsSeeded($usersByStudentId);
    setMetaValue('demo_seeded', '1');
    $pdo->commit();
  } catch (Exception $e) {
    logError('seed_data failed', $e);
    try { $pdo->rollBack(); } catch (Exception $e2) { logError('seed_data rollback failed', $e2); }
  }
}

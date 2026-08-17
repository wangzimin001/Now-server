-- 为本机 Jimmy 账号补充可验收的社交关系与动态样例；每条写入均可安全重跑。

INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00007', 'demo_grace', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Grace 格蕾丝', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_grace');

INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00008', 'demo_harper', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Harper 哈珀', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_harper');

INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00009', 'demo_iris', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Iris 艾瑞丝', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_iris');

-- 当前账号仅在尚未设置过自定义头像时使用本地演示头像，避免覆盖用户后续上传的照片。
UPDATE app_user
SET avatar_url = '/demo-media/avatars/jimmy.png'
WHERE username = 'jimmy'
  AND (avatar_url IS NULL OR avatar_url = '');

UPDATE app_user
SET avatar_url = CASE username
    WHEN 'demo_grace' THEN '/demo-media/avatars/grace.png'
    WHEN 'demo_harper' THEN '/demo-media/avatars/harper.png'
    WHEN 'demo_iris' THEN '/demo-media/avatars/iris.png'
    ELSE avatar_url
END
WHERE username IN ('demo_grace', 'demo_harper', 'demo_iris');

-- Grace 和 Harper 是好友；Annie 与 Grace 使用备注，Ben、Coco 和 Harper 保持原名，覆盖两种展示状态。
INSERT INTO friendship (user_id, friend_user_id, created_at)
SELECT me.id, friend.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 8 DAY)
FROM app_user me
JOIN app_user friend ON friend.username IN ('demo_grace', 'demo_harper')
WHERE me.username = 'jimmy'
  AND NOT EXISTS (
      SELECT 1 FROM friendship relation
      WHERE relation.user_id = me.id AND relation.friend_user_id = friend.id
  );

INSERT INTO friendship (user_id, friend_user_id, created_at)
SELECT friend.id, me.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 8 DAY)
FROM app_user me
JOIN app_user friend ON friend.username IN ('demo_grace', 'demo_harper')
WHERE me.username = 'jimmy'
  AND NOT EXISTS (
      SELECT 1 FROM friendship relation
      WHERE relation.user_id = friend.id AND relation.friend_user_id = me.id
  );

UPDATE friendship relation
JOIN app_user me ON me.id = relation.user_id AND me.username = 'jimmy'
JOIN app_user friend ON friend.id = relation.friend_user_id
SET relation.remark = CASE friend.username
    WHEN 'demo_annie' THEN '安妮 · 推力搭子'
    WHEN 'demo_grace' THEN 'Grace · 教练'
    ELSE relation.remark
END
WHERE friend.username IN ('demo_annie', 'demo_grace');

-- Iris 发出的申请与既有 Dylan、Evan 申请共同构成待处理申请列表。
INSERT INTO friend_request (
    requester_user_id, recipient_user_id, pair_low_user_id, pair_high_user_id,
    request_message, status, created_at, updated_at
)
SELECT requester.id, me.id, LEAST(requester.id, me.id), GREATEST(requester.id, me.id),
       '刚看完你的硬拉记录，想一起交流训练安排。', 'PENDING',
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 35 MINUTE), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 35 MINUTE)
FROM app_user requester
JOIN app_user me ON me.username = 'jimmy'
WHERE requester.username = 'demo_iris'
  AND NOT EXISTS (
      SELECT 1 FROM friend_request request
      WHERE request.pair_low_user_id = LEAST(requester.id, me.id)
        AND request.pair_high_user_id = GREATEST(requester.id, me.id)
  );

-- 为 Grace 建立可被朋友圈训练分享和好友资料页读取的已完成训练。
INSERT INTO workout_session (
    plan_id, name_snapshot, started_at, ended_at, duration_minutes,
    total_volume_kg, status, owner_user_id, client_record_id
)
SELECT NULL, '下肢力量训练', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 HOUR),
       DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 7 HOUR), INTERVAL 48 MINUTE),
       48, 3120.00, 'COMPLETED', demo_user.id, 'social-demo-grace-workout'
FROM app_user demo_user
WHERE demo_user.username = 'demo_grace'
  AND NOT EXISTS (
      SELECT 1 FROM workout_session session
      WHERE session.owner_user_id = demo_user.id
        AND session.client_record_id = 'social-demo-grace-workout'
  );

INSERT INTO session_exercise (session_id, exercise_id, exercise_name_snapshot, exercise_order)
SELECT session.id, exercise.id, exercise.name, 1
FROM workout_session session
JOIN exercise exercise ON exercise.id = 1
WHERE session.client_record_id = 'social-demo-grace-workout'
  AND NOT EXISTS (
      SELECT 1 FROM session_exercise item WHERE item.session_id = session.id
  );

INSERT INTO set_record (
    session_exercise_id, set_number, set_type, weight_kg, repetitions,
    rest_duration_seconds, status, completed_at
)
SELECT item.id, 1, 'STANDARD', 65.00, 8, 90, 'COMPLETED', session.ended_at
FROM session_exercise item
JOIN workout_session session ON session.id = item.session_id
WHERE session.client_record_id = 'social-demo-grace-workout'
  AND NOT EXISTS (
      SELECT 1 FROM set_record record WHERE record.session_exercise_id = item.id
  );

-- 朋友圈覆盖纯文字、训练截图、视频和训练分享四类内容。
INSERT INTO social_post (author_user_id, content, created_at)
SELECT author.id, '今天不追重量，把动作做稳就是进步。', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 HOUR)
FROM app_user author
WHERE author.username = 'demo_grace'
  AND NOT EXISTS (
      SELECT 1 FROM social_post post
      WHERE post.author_user_id = author.id
        AND post.content = '今天不追重量，把动作做稳就是进步。'
  );

INSERT INTO social_post (author_user_id, content, created_at)
SELECT author.id, '把今天的训练记录留在这里，下一次继续超越自己。', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 HOUR)
FROM app_user author
WHERE author.username = 'demo_grace'
  AND NOT EXISTS (
      SELECT 1 FROM social_post post
      WHERE post.author_user_id = author.id
        AND post.content = '把今天的训练记录留在这里，下一次继续超越自己。'
  );

INSERT INTO social_post (author_user_id, content, workout_session_id, created_at)
SELECT author.id, '下肢训练完成，深蹲的节奏比上周更稳定。', session.id,
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 MINUTE)
FROM app_user author
JOIN workout_session session ON session.owner_user_id = author.id
    AND session.client_record_id = 'social-demo-grace-workout'
WHERE author.username = 'demo_grace'
  AND NOT EXISTS (
      SELECT 1 FROM social_post post
      WHERE post.author_user_id = author.id
        AND post.content = '下肢训练完成，深蹲的节奏比上周更稳定。'
  );

INSERT INTO social_post (author_user_id, content, created_at)
SELECT author.id, '背部训练的最后一组，动作没散就值得记录。', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 30 MINUTE)
FROM app_user author
WHERE author.username = 'demo_harper'
  AND NOT EXISTS (
      SELECT 1 FROM social_post post
      WHERE post.author_user_id = author.id
        AND post.content = '背部训练的最后一组，动作没散就值得记录。'
  );

INSERT INTO social_attachment (
    owner_user_id, attachment_type, original_name, stored_name,
    mime_type, size_bytes, public_url, poster_url
)
SELECT owner.id, 'IMAGE', 'grace-training-summary.png', 'demo-grace-training-summary.png',
       'image/png', 26667, '/demo-media/moments/grace-training-summary.png', NULL
FROM app_user owner
WHERE owner.username = 'demo_grace'
  AND NOT EXISTS (
      SELECT 1 FROM social_attachment attachment
      WHERE attachment.stored_name = 'demo-grace-training-summary.png'
  );

INSERT INTO social_attachment (
    owner_user_id, attachment_type, original_name, stored_name,
    mime_type, size_bytes, public_url, poster_url
)
SELECT owner.id, 'VIDEO', 'harper-back-training.mp4', 'demo-harper-back-training.mp4',
       'video/mp4', 310409, '/demo-media/moments/harper-back-training.mp4',
       '/demo-media/moments/harper-back-training-poster.jpg'
FROM app_user owner
WHERE owner.username = 'demo_harper'
  AND NOT EXISTS (
      SELECT 1 FROM social_attachment attachment
      WHERE attachment.stored_name = 'demo-harper-back-training.mp4'
  );

INSERT IGNORE INTO social_post_attachment (post_id, attachment_id, display_order)
SELECT post.id, attachment.id, 0
FROM social_post post
JOIN app_user author ON author.id = post.author_user_id AND author.username = 'demo_grace'
JOIN social_attachment attachment ON attachment.stored_name = 'demo-grace-training-summary.png'
WHERE post.content = '把今天的训练记录留在这里，下一次继续超越自己。';

INSERT IGNORE INTO social_post_attachment (post_id, attachment_id, display_order)
SELECT post.id, attachment.id, 0
FROM social_post post
JOIN app_user author ON author.id = post.author_user_id AND author.username = 'demo_harper'
JOIN social_attachment attachment ON attachment.stored_name = 'demo-harper-back-training.mp4'
WHERE post.content = '背部训练的最后一组，动作没散就值得记录。';

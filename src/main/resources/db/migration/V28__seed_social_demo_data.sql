-- 演示账号使用独立不可公开密码的 BCrypt 摘要，仅用于展示社交闭环。
INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00001', 'demo_annie', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Annie 安妮', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_annie');

INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00002', 'demo_ben', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Ben 阿本', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_ben');

INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00003', 'demo_coco', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Coco 可可', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_coco');

INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00004', 'demo_dylan', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Dylan 大林', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_dylan');

INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00005', 'demo_evan', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Evan 伊文', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_evan');

INSERT INTO app_user (public_id, username, password_hash, display_name, enabled)
SELECT 'NDEMO00006', 'demo_fiona', '$2a$12$C8YBxwHiQo7HkmH8u.VOLuv.Jdq8YmYxzWtgRGME5Sl/.x.9/LuRW', 'Fiona 菲欧娜', TRUE
WHERE NOT EXISTS (SELECT 1 FROM app_user WHERE username = 'demo_fiona');

-- 当前开发账号与三位演示账号建立双向好友关系。
INSERT INTO friendship (user_id, friend_user_id, created_at)
SELECT me.id, friend.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 21 DAY)
FROM app_user me JOIN app_user friend ON friend.username = 'demo_annie'
WHERE me.username = 'jimmy'
  AND NOT EXISTS (SELECT 1 FROM friendship f WHERE f.user_id = me.id AND f.friend_user_id = friend.id);

INSERT INTO friendship (user_id, friend_user_id, created_at)
SELECT friend.id, me.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 21 DAY)
FROM app_user me JOIN app_user friend ON friend.username = 'demo_annie'
WHERE me.username = 'jimmy'
  AND NOT EXISTS (SELECT 1 FROM friendship f WHERE f.user_id = friend.id AND f.friend_user_id = me.id);

INSERT INTO friendship (user_id, friend_user_id, created_at)
SELECT me.id, friend.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 14 DAY)
FROM app_user me JOIN app_user friend ON friend.username = 'demo_ben'
WHERE me.username = 'jimmy'
  AND NOT EXISTS (SELECT 1 FROM friendship f WHERE f.user_id = me.id AND f.friend_user_id = friend.id);

INSERT INTO friendship (user_id, friend_user_id, created_at)
SELECT friend.id, me.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 14 DAY)
FROM app_user me JOIN app_user friend ON friend.username = 'demo_ben'
WHERE me.username = 'jimmy'
  AND NOT EXISTS (SELECT 1 FROM friendship f WHERE f.user_id = friend.id AND f.friend_user_id = me.id);

INSERT INTO friendship (user_id, friend_user_id, created_at)
SELECT me.id, friend.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 9 DAY)
FROM app_user me JOIN app_user friend ON friend.username = 'demo_coco'
WHERE me.username = 'jimmy'
  AND NOT EXISTS (SELECT 1 FROM friendship f WHERE f.user_id = me.id AND f.friend_user_id = friend.id);

INSERT INTO friendship (user_id, friend_user_id, created_at)
SELECT friend.id, me.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 9 DAY)
FROM app_user me JOIN app_user friend ON friend.username = 'demo_coco'
WHERE me.username = 'jimmy'
  AND NOT EXISTS (SELECT 1 FROM friendship f WHERE f.user_id = friend.id AND f.friend_user_id = me.id);

-- 两位演示账号向当前开发账号发起待处理申请。
INSERT INTO friend_request (
    requester_user_id, recipient_user_id, pair_low_user_id, pair_high_user_id,
    request_message, status, created_at, updated_at
)
SELECT requester.id, me.id, LEAST(requester.id, me.id), GREATEST(requester.id, me.id),
       '一起练腿吗？', 'PENDING', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 HOUR),
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 HOUR)
FROM app_user requester JOIN app_user me ON me.username = 'jimmy'
WHERE requester.username = 'demo_dylan'
  AND NOT EXISTS (
      SELECT 1 FROM friend_request request
      WHERE request.pair_low_user_id = LEAST(requester.id, me.id)
        AND request.pair_high_user_id = GREATEST(requester.id, me.id)
  );

INSERT INTO friend_request (
    requester_user_id, recipient_user_id, pair_low_user_id, pair_high_user_id,
    request_message, status, created_at, updated_at
)
SELECT requester.id, me.id, LEAST(requester.id, me.id), GREATEST(requester.id, me.id),
       '看到你的训练记录了，交个朋友。', 'PENDING', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY),
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)
FROM app_user requester JOIN app_user me ON me.username = 'jimmy'
WHERE requester.username = 'demo_evan'
  AND NOT EXISTS (
      SELECT 1 FROM friend_request request
      WHERE request.pair_low_user_id = LEAST(requester.id, me.id)
        AND request.pair_high_user_id = GREATEST(requester.id, me.id)
  );

-- 好友最近训练用于验证按活跃时间排序与好友训练摘要。
INSERT INTO workout_session (
    plan_id, name_snapshot, started_at, ended_at, duration_minutes,
    total_volume_kg, status, owner_user_id, client_record_id
)
SELECT NULL, '上肢推力训练', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY),
       DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 DAY), INTERVAL 52 MINUTE),
       52, 3260.00, 'COMPLETED', demo_user.id, 'social-demo-annie-workout'
FROM app_user demo_user
WHERE demo_user.username = 'demo_annie'
  AND NOT EXISTS (
      SELECT 1 FROM workout_session session
      WHERE session.owner_user_id = demo_user.id AND session.client_record_id = 'social-demo-annie-workout'
  );

INSERT INTO workout_session (
    plan_id, name_snapshot, started_at, ended_at, duration_minutes,
    total_volume_kg, status, owner_user_id, client_record_id
)
SELECT NULL, '背部容量训练', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 HOUR),
       DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 HOUR), INTERVAL 46 MINUTE),
       46, 2840.00, 'COMPLETED', demo_user.id, 'social-demo-ben-workout'
FROM app_user demo_user
WHERE demo_user.username = 'demo_ben'
  AND NOT EXISTS (
      SELECT 1 FROM workout_session session
      WHERE session.owner_user_id = demo_user.id AND session.client_record_id = 'social-demo-ben-workout'
  );

INSERT INTO workout_session (
    plan_id, name_snapshot, started_at, ended_at, duration_minutes,
    total_volume_kg, status, owner_user_id, client_record_id
)
SELECT NULL, '全身恢复训练', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY),
       DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 DAY), INTERVAL 38 MINUTE),
       38, 1680.00, 'COMPLETED', demo_user.id, 'social-demo-coco-workout'
FROM app_user demo_user
WHERE demo_user.username = 'demo_coco'
  AND NOT EXISTS (
      SELECT 1 FROM workout_session session
      WHERE session.owner_user_id = demo_user.id AND session.client_record_id = 'social-demo-coco-workout'
  );

INSERT INTO session_exercise (session_id, exercise_id, exercise_name_snapshot, exercise_order)
SELECT session.id, exercise.id, exercise.name, 1
FROM workout_session session
JOIN exercise exercise ON exercise.id = 1
WHERE session.client_record_id = 'social-demo-annie-workout'
  AND NOT EXISTS (SELECT 1 FROM session_exercise item WHERE item.session_id = session.id);

INSERT INTO session_exercise (session_id, exercise_id, exercise_name_snapshot, exercise_order)
SELECT session.id, exercise.id, exercise.name, 1
FROM workout_session session
JOIN exercise exercise ON exercise.id = 2
WHERE session.client_record_id = 'social-demo-ben-workout'
  AND NOT EXISTS (SELECT 1 FROM session_exercise item WHERE item.session_id = session.id);

INSERT INTO session_exercise (session_id, exercise_id, exercise_name_snapshot, exercise_order)
SELECT session.id, exercise.id, exercise.name, 1
FROM workout_session session
JOIN exercise exercise ON exercise.id = 7
WHERE session.client_record_id = 'social-demo-coco-workout'
  AND NOT EXISTS (SELECT 1 FROM session_exercise item WHERE item.session_id = session.id);

INSERT INTO set_record (
    session_exercise_id, set_number, set_type, weight_kg, repetitions,
    rest_duration_seconds, status, completed_at
)
SELECT item.id, 1, 'STANDARD', 50.00, 8, 90, 'COMPLETED', session.ended_at
FROM session_exercise item JOIN workout_session session ON session.id = item.session_id
WHERE session.client_record_id = 'social-demo-annie-workout'
  AND NOT EXISTS (SELECT 1 FROM set_record record WHERE record.session_exercise_id = item.id);

INSERT INTO set_record (
    session_exercise_id, set_number, set_type, weight_kg, repetitions,
    rest_duration_seconds, status, completed_at
)
SELECT item.id, 1, 'STANDARD', 55.00, 10, 75, 'COMPLETED', session.ended_at
FROM session_exercise item JOIN workout_session session ON session.id = item.session_id
WHERE session.client_record_id = 'social-demo-ben-workout'
  AND NOT EXISTS (SELECT 1 FROM set_record record WHERE record.session_exercise_id = item.id);

INSERT INTO set_record (
    session_exercise_id, set_number, set_type, weight_kg, repetitions,
    rest_duration_seconds, status, completed_at
)
SELECT item.id, 1, 'STANDARD', 24.00, 12, 60, 'COMPLETED', session.ended_at
FROM session_exercise item JOIN workout_session session ON session.id = item.session_id
WHERE session.client_record_id = 'social-demo-coco-workout'
  AND NOT EXISTS (SELECT 1 FROM set_record record WHERE record.session_exercise_id = item.id);

-- 为两位好友建立私聊，会话列表和聊天页可直接验证头像字段与未读状态。
INSERT INTO social_conversation (
    conversation_type, direct_low_user_id, direct_high_user_id, created_at, updated_at
)
SELECT 'DIRECT', LEAST(me.id, friend.id), GREATEST(me.id, friend.id),
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY), DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 HOUR)
FROM app_user me JOIN app_user friend ON friend.username = 'demo_annie'
WHERE me.username = 'jimmy'
  AND NOT EXISTS (
      SELECT 1 FROM social_conversation conversation
      WHERE conversation.direct_low_user_id = LEAST(me.id, friend.id)
        AND conversation.direct_high_user_id = GREATEST(me.id, friend.id)
  );

INSERT INTO social_conversation_member (conversation_id, user_id, member_role, joined_at)
SELECT conversation.id, me.id, 'MEMBER', conversation.created_at
FROM social_conversation conversation
JOIN app_user me ON me.id IN (conversation.direct_low_user_id, conversation.direct_high_user_id)
WHERE me.username IN ('jimmy', 'demo_annie')
  AND conversation.direct_low_user_id = LEAST(
      (SELECT id FROM app_user WHERE username = 'jimmy'),
      (SELECT id FROM app_user WHERE username = 'demo_annie')
  )
  AND conversation.direct_high_user_id = GREATEST(
      (SELECT id FROM app_user WHERE username = 'jimmy'),
      (SELECT id FROM app_user WHERE username = 'demo_annie')
  )
  AND NOT EXISTS (
      SELECT 1 FROM social_conversation_member member
      WHERE member.conversation_id = conversation.id AND member.user_id = me.id
  );

INSERT INTO social_message (
    conversation_id, sender_user_id, message_type, message_text, created_at
)
SELECT conversation.id, sender.id, 'TEXT', '明晚练胸吗？我刚把卧推动作节奏调整好了。',
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 HOUR)
FROM social_conversation conversation
JOIN app_user sender ON sender.username = 'demo_annie'
WHERE conversation.direct_low_user_id = LEAST(
      (SELECT id FROM app_user WHERE username = 'jimmy'), sender.id)
  AND conversation.direct_high_user_id = GREATEST(
      (SELECT id FROM app_user WHERE username = 'jimmy'), sender.id)
  AND NOT EXISTS (
      SELECT 1 FROM social_message message
      WHERE message.conversation_id = conversation.id
        AND message.message_text = '明晚练胸吗？我刚把卧推动作节奏调整好了。'
  );

INSERT INTO social_message (
    conversation_id, sender_user_id, message_type, message_text, created_at
)
SELECT conversation.id, sender.id, 'TEXT', '可以，先用轻重量热身两组。',
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 HOUR)
FROM social_conversation conversation
JOIN app_user sender ON sender.username = 'jimmy'
WHERE conversation.direct_low_user_id = LEAST(
      sender.id, (SELECT id FROM app_user WHERE username = 'demo_annie'))
  AND conversation.direct_high_user_id = GREATEST(
      sender.id, (SELECT id FROM app_user WHERE username = 'demo_annie'))
  AND NOT EXISTS (
      SELECT 1 FROM social_message message
      WHERE message.conversation_id = conversation.id
        AND message.message_text = '可以，先用轻重量热身两组。'
  );

-- 好友动态会出现在当前账号时间线，非好友 Fiona 的互动也会完整展示。
INSERT INTO social_post (author_user_id, content, workout_session_id, created_at)
SELECT author.id, '今天卧推状态不错，最后一组仍然保持了稳定停顿。', session.id,
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 4 HOUR)
FROM app_user author
JOIN workout_session session ON session.owner_user_id = author.id
    AND session.client_record_id = 'social-demo-annie-workout'
WHERE author.username = 'demo_annie'
  AND NOT EXISTS (
      SELECT 1 FROM social_post post
      WHERE post.author_user_id = author.id
        AND post.content = '今天卧推状态不错，最后一组仍然保持了稳定停顿。'
  );

INSERT INTO social_post (author_user_id, content, created_at)
SELECT author.id, '拉背日完成。先控制肩胛，再考虑增加重量。',
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 DAY)
FROM app_user author
WHERE author.username = 'demo_ben'
  AND NOT EXISTS (
      SELECT 1 FROM social_post post
      WHERE post.author_user_id = author.id
        AND post.content = '拉背日完成。先控制肩胛，再考虑增加重量。'
  );

INSERT INTO social_post (author_user_id, content, workout_session_id, created_at)
SELECT author.id, '恢复训练也要认真做，今天把每一组都留了两次余力。', session.id,
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY)
FROM app_user author
JOIN workout_session session ON session.owner_user_id = author.id
    AND session.client_record_id = 'social-demo-coco-workout'
WHERE author.username = 'demo_coco'
  AND NOT EXISTS (
      SELECT 1 FROM social_post post
      WHERE post.author_user_id = author.id
        AND post.content = '恢复训练也要认真做，今天把每一组都留了两次余力。'
  );

INSERT INTO social_post_like (post_id, user_id, created_at)
SELECT post.id, demo_user.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 HOUR)
FROM social_post post JOIN app_user demo_user ON demo_user.username = 'jimmy'
WHERE post.content = '今天卧推状态不错，最后一组仍然保持了稳定停顿。'
  AND NOT EXISTS (
      SELECT 1 FROM social_post_like item WHERE item.post_id = post.id AND item.user_id = demo_user.id
  );

INSERT INTO social_post_like (post_id, user_id, created_at)
SELECT post.id, demo_user.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 HOUR)
FROM social_post post JOIN app_user demo_user ON demo_user.username = 'demo_ben'
WHERE post.content = '今天卧推状态不错，最后一组仍然保持了稳定停顿。'
  AND NOT EXISTS (
      SELECT 1 FROM social_post_like item WHERE item.post_id = post.id AND item.user_id = demo_user.id
  );

INSERT INTO social_post_like (post_id, user_id, created_at)
SELECT post.id, demo_user.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 1 HOUR)
FROM social_post post JOIN app_user demo_user ON demo_user.username = 'demo_fiona'
WHERE post.content = '今天卧推状态不错，最后一组仍然保持了稳定停顿。'
  AND NOT EXISTS (
      SELECT 1 FROM social_post_like item WHERE item.post_id = post.id AND item.user_id = demo_user.id
  );

INSERT INTO social_post_like (post_id, user_id, created_at)
SELECT post.id, demo_user.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 18 HOUR)
FROM social_post post JOIN app_user demo_user ON demo_user.username = 'demo_coco'
WHERE post.content = '拉背日完成。先控制肩胛，再考虑增加重量。'
  AND NOT EXISTS (
      SELECT 1 FROM social_post_like item WHERE item.post_id = post.id AND item.user_id = demo_user.id
  );

INSERT INTO social_post_comment (post_id, author_user_id, content, created_at)
SELECT post.id, author.id, '动作很稳，下次可以继续保持这个节奏。',
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 150 MINUTE)
FROM social_post post JOIN app_user author ON author.username = 'demo_ben'
WHERE post.content = '今天卧推状态不错，最后一组仍然保持了稳定停顿。'
  AND NOT EXISTS (
      SELECT 1 FROM social_post_comment comment
      WHERE comment.post_id = post.id AND comment.author_user_id = author.id
        AND comment.content = '动作很稳，下次可以继续保持这个节奏。'
  );

INSERT INTO social_post_comment (post_id, author_user_id, content, created_at)
SELECT post.id, author.id, '停顿卧推对控制很有帮助，继续加油！',
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 90 MINUTE)
FROM social_post post JOIN app_user author ON author.username = 'demo_fiona'
WHERE post.content = '今天卧推状态不错，最后一组仍然保持了稳定停顿。'
  AND NOT EXISTS (
      SELECT 1 FROM social_post_comment comment
      WHERE comment.post_id = post.id AND comment.author_user_id = author.id
        AND comment.content = '停顿卧推对控制很有帮助，继续加油！'
  );

INSERT INTO social_post_comment (post_id, author_user_id, content, created_at)
SELECT post.id, author.id, '控制优先，这个思路很赞。',
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 16 HOUR)
FROM social_post post JOIN app_user author ON author.username = 'jimmy'
WHERE post.content = '拉背日完成。先控制肩胛，再考虑增加重量。'
  AND NOT EXISTS (
      SELECT 1 FROM social_post_comment comment
      WHERE comment.post_id = post.id AND comment.author_user_id = author.id
        AND comment.content = '控制优先，这个思路很赞。'
  );

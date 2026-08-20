-- 为本机 Jimmy 账号生成一条可重复识别的动态，并补齐可见互动与未读通知。
INSERT INTO social_post (author_user_id, content, created_at)
SELECT owner.id,
       '今天的训练不追求花哨，把每一组都做扎实，状态自然会回来。',
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 8 MINUTE)
FROM app_user owner
WHERE owner.username = 'jimmy'
  AND NOT EXISTS (
      SELECT 1
      FROM social_post post
      WHERE post.author_user_id = owner.id
        AND post.content = '今天的训练不追求花哨，把每一组都做扎实，状态自然会回来。'
  );

-- 五位好友的点赞常驻帖子；主键约束保证迁移内容保持幂等。
INSERT IGNORE INTO social_post_like (post_id, user_id, created_at)
SELECT post.id, actor.id, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 5 MINUTE)
FROM social_post post
JOIN app_user owner ON owner.id = post.author_user_id AND owner.username = 'jimmy'
JOIN app_user actor ON actor.username IN (
    'demo_annie', 'demo_ben', 'demo_coco', 'demo_grace', 'demo_harper'
)
WHERE post.content = '今天的训练不追求花哨，把每一组都做扎实，状态自然会回来。';

-- 四条不同语气的评论用于验证列表、互动区与未读入口。
INSERT INTO social_post_comment (post_id, author_user_id, content, created_at)
SELECT post.id, actor.id, seed.comment_text,
       TIMESTAMPADD(SECOND, -seed.age_seconds, CURRENT_TIMESTAMP)
FROM social_post post
JOIN app_user owner ON owner.id = post.author_user_id AND owner.username = 'jimmy'
JOIN (
    SELECT 'demo_ben' AS username, '这股稳定劲儿很对，继续保持！' AS comment_text, 240 AS age_seconds
    UNION ALL
    SELECT 'demo_coco', '看得我也想马上去训练了。', 180
    UNION ALL
    SELECT 'demo_grace', '动作质量优先，今天这节练得漂亮。', 120
    UNION ALL
    SELECT 'demo_harper', '稳稳推进，比盲目加重量更强。', 60
) seed
JOIN app_user actor ON actor.username = seed.username
WHERE post.content = '今天的训练不追求花哨，把每一组都做扎实，状态自然会回来。'
  AND NOT EXISTS (
      SELECT 1
      FROM social_post_comment comment
      WHERE comment.post_id = post.id
        AND comment.author_user_id = actor.id
        AND comment.content = seed.comment_text
  );

-- 点赞通知按帖子聚合，因此使用最后一位点赞好友作为未读入口头像。
INSERT INTO social_notification (
    recipient_user_id, actor_user_id, notification_type, interaction_key,
    post_id, comment_id, read_at, created_at
)
SELECT owner.id, actor.id, 'POST_LIKE', CONCAT('LIKE:', post.id),
       post.id, NULL, NULL, DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 45 SECOND)
FROM social_post post
JOIN app_user owner ON owner.id = post.author_user_id AND owner.username = 'jimmy'
JOIN app_user actor ON actor.username = 'demo_harper'
WHERE post.content = '今天的训练不追求花哨，把每一组都做扎实，状态自然会回来。'
ON DUPLICATE KEY UPDATE
    actor_user_id = VALUES(actor_user_id),
    read_at = NULL,
    created_at = VALUES(created_at);

-- 每条评论独立生成通知，并显式保持未读状态。
INSERT INTO social_notification (
    recipient_user_id, actor_user_id, notification_type, interaction_key,
    post_id, comment_id, read_at, created_at
)
SELECT owner.id, comment.author_user_id, 'POST_COMMENT', CONCAT('COMMENT:', comment.id),
       post.id, comment.id, NULL, comment.created_at
FROM social_post post
JOIN app_user owner ON owner.id = post.author_user_id AND owner.username = 'jimmy'
JOIN social_post_comment comment ON comment.post_id = post.id AND comment.deleted_at IS NULL
WHERE post.content = '今天的训练不追求花哨，把每一组都做扎实，状态自然会回来。'
  AND comment.content IN (
      '这股稳定劲儿很对，继续保持！',
      '看得我也想马上去训练了。',
      '动作质量优先，今天这节练得漂亮。',
      '稳稳推进，比盲目加重量更强。'
  )
ON DUPLICATE KEY UPDATE
    actor_user_id = VALUES(actor_user_id),
    read_at = NULL,
    created_at = VALUES(created_at);

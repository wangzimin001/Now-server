-- 演示账号头像使用随应用发布的本地静态资源，不依赖运行时外网。
UPDATE app_user
SET avatar_url = CASE username
    WHEN 'demo_annie' THEN '/demo-media/avatars/annie.png'
    WHEN 'demo_ben' THEN '/demo-media/avatars/ben.png'
    WHEN 'demo_coco' THEN '/demo-media/avatars/coco.png'
    WHEN 'demo_dylan' THEN '/demo-media/avatars/dylan.png'
    WHEN 'demo_evan' THEN '/demo-media/avatars/evan.png'
    WHEN 'demo_fiona' THEN '/demo-media/avatars/fiona.png'
    ELSE avatar_url
END
WHERE username IN ('demo_annie', 'demo_ben', 'demo_coco', 'demo_dylan', 'demo_evan', 'demo_fiona');

-- 根据真实发布时间倒序查询，主键仅用于同时间点的稳定次序。
ALTER TABLE social_post
    DROP INDEX idx_social_post_feed,
    ADD KEY idx_social_post_feed (deleted_at, created_at, id);

-- Annie 的卧推动态展示两张图片，用于验收双列布局。
INSERT INTO social_attachment (
    owner_user_id, attachment_type, original_name, stored_name,
    mime_type, size_bytes, public_url
)
SELECT demo_user.id, 'IMAGE', 'annie-bench-press.jpg', 'demo-annie-bench-press.jpg',
       'image/jpeg', 99482, '/demo-media/moments/annie-bench-press.jpg'
FROM app_user demo_user
WHERE demo_user.username = 'demo_annie'
  AND NOT EXISTS (
      SELECT 1 FROM social_attachment attachment
      WHERE attachment.stored_name = 'demo-annie-bench-press.jpg'
  );

INSERT INTO social_attachment (
    owner_user_id, attachment_type, original_name, stored_name,
    mime_type, size_bytes, public_url
)
SELECT demo_user.id, 'IMAGE', 'annie-spotter.jpg', 'demo-annie-spotter.jpg',
       'image/jpeg', 137353, '/demo-media/moments/annie-spotter.jpg'
FROM app_user demo_user
WHERE demo_user.username = 'demo_annie'
  AND NOT EXISTS (
      SELECT 1 FROM social_attachment attachment
      WHERE attachment.stored_name = 'demo-annie-spotter.jpg'
  );

-- Ben 的拉背日动态同样展示两张地下健身房风格图片。
INSERT INTO social_attachment (
    owner_user_id, attachment_type, original_name, stored_name,
    mime_type, size_bytes, public_url
)
SELECT demo_user.id, 'IMAGE', 'ben-dark-gym.jpg', 'demo-ben-dark-gym.jpg',
       'image/jpeg', 114239, '/demo-media/moments/ben-dark-gym.jpg'
FROM app_user demo_user
WHERE demo_user.username = 'demo_ben'
  AND NOT EXISTS (
      SELECT 1 FROM social_attachment attachment
      WHERE attachment.stored_name = 'demo-ben-dark-gym.jpg'
  );

INSERT INTO social_attachment (
    owner_user_id, attachment_type, original_name, stored_name,
    mime_type, size_bytes, public_url
)
SELECT demo_user.id, 'IMAGE', 'ben-barbell.jpg', 'demo-ben-barbell.jpg',
       'image/jpeg', 103844, '/demo-media/moments/ben-barbell.jpg'
FROM app_user demo_user
WHERE demo_user.username = 'demo_ben'
  AND NOT EXISTS (
      SELECT 1 FROM social_attachment attachment
      WHERE attachment.stored_name = 'demo-ben-barbell.jpg'
  );

-- Coco 的恢复训练动态保留单图片布局样例。
INSERT INTO social_attachment (
    owner_user_id, attachment_type, original_name, stored_name,
    mime_type, size_bytes, public_url
)
SELECT demo_user.id, 'IMAGE', 'coco-recovery.jpg', 'demo-coco-recovery.jpg',
       'image/jpeg', 154946, '/demo-media/moments/coco-recovery.jpg'
FROM app_user demo_user
WHERE demo_user.username = 'demo_coco'
  AND NOT EXISTS (
      SELECT 1 FROM social_attachment attachment
      WHERE attachment.stored_name = 'demo-coco-recovery.jpg'
  );

INSERT IGNORE INTO social_post_attachment (post_id, attachment_id, display_order)
SELECT post.id, attachment.id, 0
FROM social_post post
JOIN app_user author ON author.id = post.author_user_id AND author.username = 'demo_annie'
JOIN social_attachment attachment ON attachment.stored_name = 'demo-annie-bench-press.jpg'
WHERE post.content = '今天卧推状态不错，最后一组仍然保持了稳定停顿。';

INSERT IGNORE INTO social_post_attachment (post_id, attachment_id, display_order)
SELECT post.id, attachment.id, 1
FROM social_post post
JOIN app_user author ON author.id = post.author_user_id AND author.username = 'demo_annie'
JOIN social_attachment attachment ON attachment.stored_name = 'demo-annie-spotter.jpg'
WHERE post.content = '今天卧推状态不错，最后一组仍然保持了稳定停顿。';

INSERT IGNORE INTO social_post_attachment (post_id, attachment_id, display_order)
SELECT post.id, attachment.id, 0
FROM social_post post
JOIN app_user author ON author.id = post.author_user_id AND author.username = 'demo_ben'
JOIN social_attachment attachment ON attachment.stored_name = 'demo-ben-dark-gym.jpg'
WHERE post.content = '拉背日完成。先控制肩胛，再考虑增加重量。';

INSERT IGNORE INTO social_post_attachment (post_id, attachment_id, display_order)
SELECT post.id, attachment.id, 1
FROM social_post post
JOIN app_user author ON author.id = post.author_user_id AND author.username = 'demo_ben'
JOIN social_attachment attachment ON attachment.stored_name = 'demo-ben-barbell.jpg'
WHERE post.content = '拉背日完成。先控制肩胛，再考虑增加重量。';

INSERT IGNORE INTO social_post_attachment (post_id, attachment_id, display_order)
SELECT post.id, attachment.id, 0
FROM social_post post
JOIN app_user author ON author.id = post.author_user_id AND author.username = 'demo_coco'
JOIN social_attachment attachment ON attachment.stored_name = 'demo-coco-recovery.jpg'
WHERE post.content = '恢复训练也要认真做，今天把每一组都留了两次余力。';

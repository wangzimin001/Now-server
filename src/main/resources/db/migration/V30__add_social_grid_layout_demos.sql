-- 为三位好友分别登记同一组本地布局图片，保持附件归属与动态作者一致。
INSERT INTO social_attachment (
    owner_user_id, attachment_type, original_name, stored_name,
    mime_type, size_bytes, public_url
)
SELECT demo_owner.id, 'IMAGE', image_file.file_name,
       CONCAT('demo-grid-', demo_owner.username, '-', LPAD(image_file.sequence_number, 2, '0'), '.jpg'),
       'image/jpeg', image_file.size_bytes,
       CONCAT('/demo-media/moments/grid-layout/', image_file.file_name)
FROM app_user demo_owner
JOIN (
    SELECT 1 AS sequence_number, 'layout-01.jpg' AS file_name, 60661 AS size_bytes
    UNION ALL SELECT 2, 'layout-02.jpg', 71635
    UNION ALL SELECT 3, 'layout-03.jpg', 65474
    UNION ALL SELECT 4, 'layout-04.jpg', 67204
    UNION ALL SELECT 5, 'layout-05.jpg', 46866
    UNION ALL SELECT 6, 'layout-06.jpg', 62049
    UNION ALL SELECT 7, 'layout-07.jpg', 84099
    UNION ALL SELECT 8, 'layout-08.jpg', 76073
    UNION ALL SELECT 9, 'layout-09.jpg', 64449
) image_file ON TRUE
WHERE demo_owner.username IN ('demo_annie', 'demo_ben', 'demo_coco')
  AND NOT EXISTS (
      SELECT 1
      FROM social_attachment attachment
      WHERE attachment.stored_name = CONCAT(
          'demo-grid-', demo_owner.username, '-', LPAD(image_file.sequence_number, 2, '0'), '.jpg'
      )
  );

-- 从最近到更早依次创建一至九图动态，便于连续滚动比较布局。
INSERT INTO social_post (author_user_id, content, created_at)
SELECT author.id, demo_post.content,
       DATE_SUB(CURRENT_TIMESTAMP, INTERVAL demo_post.minutes_ago MINUTE)
FROM app_user author
JOIN (
    SELECT 'demo_annie' AS username, 1 AS image_count, 1 AS minutes_ago,
           '1 图 · 夜训结束，留一张器械区的记录。' AS content
    UNION ALL SELECT 'demo_ben', 2, 2,
           '2 图 · 推力训练完成，今天的动作节奏更稳定。'
    UNION ALL SELECT 'demo_coco', 3, 3,
           '3 图 · 晨间训练打卡，状态正在慢慢回来。'
    UNION ALL SELECT 'demo_annie', 4, 4,
           '4 图 · 训练前后都拍了一点，记录今天的过程。'
    UNION ALL SELECT 'demo_ben', 5, 5,
           '5 图 · 背部训练日，先控制动作再增加重量。'
    UNION ALL SELECT 'demo_coco', 6, 6,
           '6 图 · 今天尝试了不同器械，整体感觉不错。'
    UNION ALL SELECT 'demo_annie', 7, 7,
           '7 图 · 周末训练随手记，保持稳定比追重量重要。'
    UNION ALL SELECT 'demo_ben', 8, 8,
           '8 图 · 地下训练区的一组照片，今天练得很扎实。'
    UNION ALL SELECT 'demo_coco', 9, 9,
           '9 图 · 九宫格训练记录，热身、训练和拉伸都完成了。'
) demo_post ON demo_post.username = author.username
WHERE NOT EXISTS (
    SELECT 1
    FROM social_post post
    WHERE post.author_user_id = author.id
      AND post.content = demo_post.content
);

-- 每条动态取图片序列的前 N 张，display_order 从零开始。
INSERT IGNORE INTO social_post_attachment (post_id, attachment_id, display_order)
SELECT post.id, attachment.id, image_index.sequence_number - 1
FROM (
    SELECT 'demo_annie' AS username, 1 AS image_count,
           '1 图 · 夜训结束，留一张器械区的记录。' AS content
    UNION ALL SELECT 'demo_ben', 2,
           '2 图 · 推力训练完成，今天的动作节奏更稳定。'
    UNION ALL SELECT 'demo_coco', 3,
           '3 图 · 晨间训练打卡，状态正在慢慢回来。'
    UNION ALL SELECT 'demo_annie', 4,
           '4 图 · 训练前后都拍了一点，记录今天的过程。'
    UNION ALL SELECT 'demo_ben', 5,
           '5 图 · 背部训练日，先控制动作再增加重量。'
    UNION ALL SELECT 'demo_coco', 6,
           '6 图 · 今天尝试了不同器械，整体感觉不错。'
    UNION ALL SELECT 'demo_annie', 7,
           '7 图 · 周末训练随手记，保持稳定比追重量重要。'
    UNION ALL SELECT 'demo_ben', 8,
           '8 图 · 地下训练区的一组照片，今天练得很扎实。'
    UNION ALL SELECT 'demo_coco', 9,
           '9 图 · 九宫格训练记录，热身、训练和拉伸都完成了。'
) demo_post
JOIN app_user author ON author.username = demo_post.username
JOIN social_post post
  ON post.author_user_id = author.id
 AND post.content = demo_post.content
JOIN (
    SELECT 1 AS sequence_number
    UNION ALL SELECT 2
    UNION ALL SELECT 3
    UNION ALL SELECT 4
    UNION ALL SELECT 5
    UNION ALL SELECT 6
    UNION ALL SELECT 7
    UNION ALL SELECT 8
    UNION ALL SELECT 9
) image_index ON image_index.sequence_number <= demo_post.image_count
JOIN social_attachment attachment
  ON attachment.owner_user_id = author.id
 AND attachment.stored_name = CONCAT(
     'demo-grid-', author.username, '-', LPAD(image_index.sequence_number, 2, '0'), '.jpg'
 );

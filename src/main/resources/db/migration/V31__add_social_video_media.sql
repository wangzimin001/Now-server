-- 把附件和消息类型限制为后端领域枚举允许的稳定值。
ALTER TABLE social_attachment
    ADD CONSTRAINT ck_social_attachment_type
        CHECK (attachment_type IN ('IMAGE', 'VIDEO', 'FILE'));

ALTER TABLE social_message
    ADD CONSTRAINT ck_social_message_type
        CHECK (message_type IN ('TEXT', 'EMOJI', 'IMAGE', 'VIDEO', 'FILE', 'SYSTEM'));

-- 九宫格仍由客户端方形容器裁切，但点击预览必须读取未裁切的完整原图。
UPDATE social_attachment
SET public_url = CASE original_name
    WHEN 'layout-01.jpg' THEN '/demo-media/moments/annie-bench-press.jpg'
    WHEN 'layout-02.jpg' THEN '/demo-media/moments/annie-spotter.jpg'
    WHEN 'layout-03.jpg' THEN '/demo-media/moments/ben-dark-gym.jpg'
    WHEN 'layout-04.jpg' THEN '/demo-media/moments/ben-barbell.jpg'
    WHEN 'layout-05.jpg' THEN '/demo-media/moments/coco-recovery.jpg'
    WHEN 'layout-06.jpg' THEN '/demo-media/moments/annie-bench-press.jpg'
    WHEN 'layout-07.jpg' THEN '/demo-media/moments/annie-spotter.jpg'
    WHEN 'layout-08.jpg' THEN '/demo-media/moments/ben-dark-gym.jpg'
    WHEN 'layout-09.jpg' THEN '/demo-media/moments/ben-barbell.jpg'
    ELSE public_url
END
WHERE stored_name LIKE 'demo-grid-%'
  AND original_name IN (
      'layout-01.jpg', 'layout-02.jpg', 'layout-03.jpg',
      'layout-04.jpg', 'layout-05.jpg', 'layout-06.jpg',
      'layout-07.jpg', 'layout-08.jpg', 'layout-09.jpg'
  );

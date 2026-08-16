ALTER TABLE social_attachment
    ADD COLUMN poster_url VARCHAR(600) NULL AFTER public_url;

UPDATE social_attachment
SET poster_url = CONCAT(public_url, '/poster')
WHERE attachment_type = 'VIDEO' AND poster_url IS NULL;

CREATE TABLE social_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    actor_user_id BIGINT NOT NULL,
    notification_type VARCHAR(30) NOT NULL,
    interaction_key VARCHAR(100) NOT NULL,
    post_id BIGINT NOT NULL,
    comment_id BIGINT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_social_notification_recipient FOREIGN KEY (recipient_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_social_notification_actor FOREIGN KEY (actor_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_social_notification_post FOREIGN KEY (post_id) REFERENCES social_post (id),
    CONSTRAINT fk_social_notification_comment FOREIGN KEY (comment_id) REFERENCES social_post_comment (id),
    CONSTRAINT ck_social_notification_type CHECK (notification_type IN ('POST_LIKE', 'POST_COMMENT')),
    CONSTRAINT ck_social_notification_not_self CHECK (recipient_user_id <> actor_user_id),
    UNIQUE KEY uk_social_notification_interaction (recipient_user_id, interaction_key),
    KEY idx_social_notification_recipient_unread (recipient_user_id, read_at, created_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

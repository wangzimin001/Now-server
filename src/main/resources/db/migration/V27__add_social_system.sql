ALTER TABLE app_user
    ADD COLUMN public_id VARCHAR(20) NULL AFTER id,
    ADD COLUMN avatar_url VARCHAR(500) NULL AFTER display_name;

UPDATE app_user
SET public_id = CONCAT('N', LPAD(id, 9, '0'))
WHERE public_id IS NULL;

ALTER TABLE app_user
    MODIFY COLUMN public_id VARCHAR(20) NOT NULL,
    ADD UNIQUE KEY uk_app_user_public_id (public_id);

CREATE TABLE friend_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    requester_user_id BIGINT NOT NULL,
    recipient_user_id BIGINT NOT NULL,
    pair_low_user_id BIGINT NOT NULL,
    pair_high_user_id BIGINT NOT NULL,
    request_message VARCHAR(120) NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_friend_request_requester FOREIGN KEY (requester_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_friend_request_recipient FOREIGN KEY (recipient_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_friend_request_distinct CHECK (requester_user_id <> recipient_user_id),
    CONSTRAINT ck_friend_request_pair CHECK (pair_low_user_id < pair_high_user_id),
    UNIQUE KEY uk_friend_request_pair (pair_low_user_id, pair_high_user_id),
    KEY idx_friend_request_recipient_status (recipient_user_id, status, updated_at),
    KEY idx_friend_request_requester_status (requester_user_id, status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE friendship (
    user_id BIGINT NOT NULL,
    friend_user_id BIGINT NOT NULL,
    remark VARCHAR(40) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, friend_user_id),
    CONSTRAINT fk_friendship_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_friendship_friend FOREIGN KEY (friend_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_friendship_distinct CHECK (user_id <> friend_user_id),
    KEY idx_friendship_friend (friend_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE social_conversation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_type VARCHAR(20) NOT NULL,
    name VARCHAR(60) NULL,
    owner_user_id BIGINT NULL,
    direct_low_user_id BIGINT NULL,
    direct_high_user_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    dissolved_at TIMESTAMP NULL,
    CONSTRAINT fk_social_conversation_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_social_conversation_direct_low FOREIGN KEY (direct_low_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_social_conversation_direct_high FOREIGN KEY (direct_high_user_id) REFERENCES app_user (id),
    UNIQUE KEY uk_social_conversation_direct (direct_low_user_id, direct_high_user_id),
    KEY idx_social_conversation_updated (updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE social_conversation_member (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(20) NOT NULL,
    last_read_message_id BIGINT NULL,
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    left_at TIMESTAMP NULL,
    muted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_social_member_conversation FOREIGN KEY (conversation_id) REFERENCES social_conversation (id),
    CONSTRAINT fk_social_member_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    KEY idx_social_member_user_active (user_id, left_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE social_attachment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    attachment_type VARCHAR(20) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(100) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    public_url VARCHAR(600) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_social_attachment_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (id),
    UNIQUE KEY uk_social_attachment_stored_name (stored_name),
    KEY idx_social_attachment_owner (owner_user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE social_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_user_id BIGINT NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    message_text VARCHAR(2000) NULL,
    attachment_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_social_message_conversation FOREIGN KEY (conversation_id) REFERENCES social_conversation (id),
    CONSTRAINT fk_social_message_sender FOREIGN KEY (sender_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_social_message_attachment FOREIGN KEY (attachment_id) REFERENCES social_attachment (id),
    KEY idx_social_message_conversation (conversation_id, id),
    KEY idx_social_message_sender (sender_user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE social_conversation_member
    ADD CONSTRAINT fk_social_member_last_read FOREIGN KEY (last_read_message_id) REFERENCES social_message (id);

CREATE TABLE social_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_user_id BIGINT NOT NULL,
    content VARCHAR(2000) NULL,
    workout_session_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_social_post_author FOREIGN KEY (author_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_social_post_workout FOREIGN KEY (workout_session_id) REFERENCES workout_session (id),
    KEY idx_social_post_author_created (author_user_id, created_at),
    KEY idx_social_post_feed (deleted_at, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE social_post_attachment (
    post_id BIGINT NOT NULL,
    attachment_id BIGINT NOT NULL,
    display_order INT NOT NULL,
    PRIMARY KEY (post_id, attachment_id),
    CONSTRAINT fk_social_post_attachment_post FOREIGN KEY (post_id) REFERENCES social_post (id),
    CONSTRAINT fk_social_post_attachment_file FOREIGN KEY (attachment_id) REFERENCES social_attachment (id),
    UNIQUE KEY uk_social_post_attachment_order (post_id, display_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE social_post_like (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_social_post_like_post FOREIGN KEY (post_id) REFERENCES social_post (id),
    CONSTRAINT fk_social_post_like_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    KEY idx_social_post_like_created (post_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE social_post_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    reply_to_comment_id BIGINT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT fk_social_post_comment_post FOREIGN KEY (post_id) REFERENCES social_post (id),
    CONSTRAINT fk_social_post_comment_author FOREIGN KEY (author_user_id) REFERENCES app_user (id),
    CONSTRAINT fk_social_post_comment_reply FOREIGN KEY (reply_to_comment_id) REFERENCES social_post_comment (id),
    KEY idx_social_post_comment_created (post_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

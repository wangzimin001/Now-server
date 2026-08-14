CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(30) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_app_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE auth_refresh_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP NULL,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id),
    KEY idx_refresh_token_expiry (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE workout_plan
    ADD COLUMN owner_user_id BIGINT NULL,
    ADD CONSTRAINT fk_workout_plan_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (id),
    ADD KEY idx_workout_plan_owner (owner_user_id);

ALTER TABLE workout_session
    ADD COLUMN owner_user_id BIGINT NULL,
    ADD COLUMN client_record_id VARCHAR(80) NULL,
    ADD CONSTRAINT fk_workout_session_owner FOREIGN KEY (owner_user_id) REFERENCES app_user (id),
    ADD UNIQUE KEY uk_workout_session_client_record (owner_user_id, client_record_id),
    ADD KEY idx_workout_session_owner_ended (owner_user_id, ended_at);

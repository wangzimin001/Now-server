CREATE TABLE user_training_config (
    user_id BIGINT PRIMARY KEY,
    training_mode VARCHAR(16) NOT NULL DEFAULT 'free',
    cycle_plan JSON NULL,
    client_updated_at TIMESTAMP(3) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_training_config_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_training_config_mode CHECK (training_mode IN ('free', 'cycle'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

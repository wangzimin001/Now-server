CREATE TABLE user_hidden_workout_plan (
    user_id BIGINT NOT NULL,
    plan_id BIGINT NOT NULL,
    hidden_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, plan_id),
    CONSTRAINT fk_hidden_plan_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_hidden_plan_plan FOREIGN KEY (plan_id) REFERENCES workout_plan (id),
    KEY idx_hidden_plan_plan (plan_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

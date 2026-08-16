ALTER TABLE plan_exercise
    ADD COLUMN replacement_exercise_id BIGINT NULL AFTER exercise_id,
    ADD KEY idx_plan_exercise_replacement (replacement_exercise_id),
    ADD CONSTRAINT fk_plan_exercise_replacement
        FOREIGN KEY (replacement_exercise_id) REFERENCES exercise(id) ON DELETE SET NULL;

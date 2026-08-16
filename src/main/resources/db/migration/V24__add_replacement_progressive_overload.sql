ALTER TABLE plan_exercise
    ADD COLUMN replacement_progressive_overload_enabled BOOLEAN NOT NULL DEFAULT FALSE
        AFTER progressive_overload_enabled;

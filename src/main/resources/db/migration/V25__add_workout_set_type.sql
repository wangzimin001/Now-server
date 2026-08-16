-- Persist standard, warm-up and drop-set semantics without changing existing records.
ALTER TABLE set_record
    ADD COLUMN set_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD' AFTER set_number,
    ADD CONSTRAINT chk_set_record_type
        CHECK (set_type IN ('STANDARD', 'WARM_UP', 'DROP_SET'));

-- Normalize primary and secondary exercise categories.
-- Runtime code reads category definitions and exercise mappings from these tables only.

CREATE TABLE exercise_category (
    code VARCHAR(40) PRIMARY KEY,
    name VARCHAR(40) NOT NULL,
    sort_order INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exercise_category_name (name),
    KEY idx_exercise_category_order (is_active, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO exercise_category (code, name, sort_order)
SELECT category_code, MAX(category_name), MIN(category_sort)
FROM exercise
WHERE source = 'exercise-dataset'
  AND category_code IS NOT NULL
GROUP BY category_code;

CREATE TABLE exercise_subcategory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category_code VARCHAR(40) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(40) NOT NULL,
    sort_order INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exercise_subcategory_category
        FOREIGN KEY (category_code) REFERENCES exercise_category (code),
    UNIQUE KEY uk_exercise_subcategory_code (category_code, code),
    UNIQUE KEY uk_exercise_subcategory_name (category_code, name),
    KEY idx_exercise_subcategory_order (category_code, is_active, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO exercise_subcategory (category_code, code, name, sort_order) VALUES
    ('chest', 'upper-chest', '上胸', 10),
    ('chest', 'middle-chest', '中胸', 20),
    ('chest', 'lower-chest', '下胸', 30),
    ('back', 'latissimus-dorsi', '背阔肌', 10),
    ('back', 'upper-back', '上背部', 20),
    ('back', 'trapezius', '斜方肌', 30),
    ('back', 'lower-back', '下背部', 40),
    ('shoulders', 'anterior-deltoid', '前束', 10),
    ('shoulders', 'lateral-deltoid', '中束', 20),
    ('shoulders', 'posterior-deltoid', '后束', 30),
    ('upper legs', 'quadriceps', '股四头肌', 10),
    ('upper legs', 'hamstrings', '腘绳肌', 20),
    ('upper legs', 'gluteal-muscles', '臀肌', 30),
    ('upper legs', 'adductors', '内收肌', 40),
    ('upper legs', 'abductors', '外展肌', 50),
    ('waist', 'upper-abdomen', '上腹', 10),
    ('waist', 'lower-abdomen', '下腹', 20),
    ('waist', 'obliques', '腹斜肌', 30),
    ('waist', 'core-stability', '核心稳定', 40),
    ('waist', 'lower-back', '下背部', 50),
    ('upper arms', 'inner-biceps', '二头内侧', 10),
    ('upper arms', 'outer-biceps', '二头外侧', 20),
    ('upper arms', 'triceps', '三头', 30),
    ('lower legs', 'gastrocnemius', '腓肠肌', 10),
    ('lower legs', 'soleus', '比目鱼肌', 20),
    ('lower legs', 'tibialis-anterior', '胫骨前肌', 30),
    ('lower legs', 'ankle-stability', '踝部稳定', 40),
    ('lower arms', 'wrist-flexors', '腕屈肌', 10),
    ('lower arms', 'wrist-extensors', '腕伸肌', 20),
    ('lower arms', 'pronation-supination', '旋前旋后', 30),
    ('lower arms', 'grip', '握力', 40);

CREATE TABLE exercise_subcategory_mapping (
    exercise_id BIGINT NOT NULL,
    subcategory_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (exercise_id, subcategory_id),
    CONSTRAINT fk_exercise_subcategory_mapping_exercise
        FOREIGN KEY (exercise_id) REFERENCES exercise (id) ON DELETE CASCADE,
    CONSTRAINT fk_exercise_subcategory_mapping_subcategory
        FOREIGN KEY (subcategory_id) REFERENCES exercise_subcategory (id) ON DELETE CASCADE,
    KEY idx_exercise_subcategory_mapping_reverse (subcategory_id, exercise_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Copy the existing JSON labels into the normalized many-to-many mapping before removing legacy columns.
INSERT INTO exercise_subcategory_mapping (exercise_id, subcategory_id)
SELECT exercise.id, subcategory.id
FROM exercise
JOIN exercise_subcategory subcategory ON subcategory.category_code = exercise.category_code
WHERE exercise.source = 'exercise-dataset'
  AND CASE exercise.category_code
        WHEN 'chest' THEN JSON_CONTAINS(COALESCE(exercise.chest_regions, JSON_ARRAY()), JSON_QUOTE(subcategory.name))
        WHEN 'back' THEN JSON_CONTAINS(COALESCE(exercise.back_regions, JSON_ARRAY()), JSON_QUOTE(subcategory.name))
        WHEN 'shoulders' THEN JSON_CONTAINS(COALESCE(exercise.shoulder_regions, JSON_ARRAY()), JSON_QUOTE(subcategory.name))
        WHEN 'upper legs' THEN JSON_CONTAINS(COALESCE(exercise.thigh_regions, JSON_ARRAY()), JSON_QUOTE(subcategory.name))
        WHEN 'waist' THEN JSON_CONTAINS(COALESCE(exercise.waist_regions, JSON_ARRAY()), JSON_QUOTE(subcategory.name))
        WHEN 'upper arms' THEN JSON_CONTAINS(COALESCE(exercise.upper_arm_regions, JSON_ARRAY()), JSON_QUOTE(subcategory.name))
        WHEN 'lower legs' THEN JSON_CONTAINS(COALESCE(exercise.calf_regions, JSON_ARRAY()), JSON_QUOTE(subcategory.name))
        WHEN 'lower arms' THEN JSON_CONTAINS(COALESCE(exercise.forearm_regions, JSON_ARRAY()), JSON_QUOTE(subcategory.name))
        ELSE FALSE
      END;

ALTER TABLE exercise
    DROP COLUMN chest_regions,
    DROP COLUMN back_regions,
    DROP COLUMN shoulder_regions,
    DROP COLUMN thigh_regions,
    DROP COLUMN waist_regions,
    DROP COLUMN upper_arm_regions,
    DROP COLUMN calf_regions,
    DROP COLUMN forearm_regions,
    DROP COLUMN category_name,
    DROP COLUMN category_sort,
    ADD CONSTRAINT fk_exercise_category
        FOREIGN KEY (category_code) REFERENCES exercise_category (code);

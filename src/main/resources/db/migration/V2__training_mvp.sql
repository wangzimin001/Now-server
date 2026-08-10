CREATE TABLE exercise (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    muscle_group VARCHAR(40) NOT NULL,
    equipment VARCHAR(40) NOT NULL,
    level VARCHAR(20) NOT NULL,
    instructions VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_exercise_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE workout_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    estimated_minutes INT NOT NULL,
    level VARCHAR(20) NOT NULL,
    weekly_target INT NOT NULL DEFAULT 3,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE plan_exercise (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    exercise_id BIGINT NOT NULL,
    exercise_order INT NOT NULL,
    target_sets INT NOT NULL,
    target_reps INT NOT NULL,
    rest_seconds INT NOT NULL,
    CONSTRAINT fk_plan_exercise_plan FOREIGN KEY (plan_id) REFERENCES workout_plan (id),
    CONSTRAINT fk_plan_exercise_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (id),
    UNIQUE KEY uk_plan_exercise_order (plan_id, exercise_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE workout_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    plan_id BIGINT NULL,
    name_snapshot VARCHAR(80) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP NULL,
    duration_minutes INT NOT NULL DEFAULT 0,
    total_volume_kg DECIMAL(12, 2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_workout_session_plan FOREIGN KEY (plan_id) REFERENCES workout_plan (id),
    KEY idx_workout_session_ended_at (ended_at),
    KEY idx_workout_session_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE session_exercise (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    exercise_id BIGINT NULL,
    exercise_name_snapshot VARCHAR(80) NOT NULL,
    exercise_order INT NOT NULL,
    CONSTRAINT fk_session_exercise_session FOREIGN KEY (session_id) REFERENCES workout_session (id),
    CONSTRAINT fk_session_exercise_exercise FOREIGN KEY (exercise_id) REFERENCES exercise (id),
    UNIQUE KEY uk_session_exercise_order (session_id, exercise_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE set_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_exercise_id BIGINT NOT NULL,
    set_number INT NOT NULL,
    weight_kg DECIMAL(8, 2) NOT NULL DEFAULT 0,
    repetitions INT NOT NULL,
    rpe DECIMAL(3, 1) NULL,
    status VARCHAR(20) NOT NULL,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_set_record_session_exercise FOREIGN KEY (session_exercise_id) REFERENCES session_exercise (id),
    UNIQUE KEY uk_set_record_number (session_exercise_id, set_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO exercise (id, name, muscle_group, equipment, level, instructions) VALUES
    (1, '杠铃卧推', '胸部', '杠铃', '基础', '肩胛骨后缩并稳定贴凳，前臂保持垂直，控制下放后从胸部上方推起。'),
    (2, '高位下拉', '背部', '固定器械', '基础', '保持躯干稳定，先下沉肩胛，再将横杆拉向锁骨上方，避免身体后仰借力。'),
    (3, '坐姿哑铃推举', '肩部', '哑铃', '基础', '收紧核心，手肘保持在手腕下方，向上推至接近伸直后受控返回。'),
    (4, '坐姿划船', '背部', '绳索', '基础', '胸口保持打开，拉动时肘部贴近身体，终点夹紧肩胛骨。'),
    (5, '绳索下压', '手臂', '绳索', '入门', '固定上臂，只做肘关节伸展，在底端完全收紧肱三头肌。'),
    (6, '哑铃弯举', '手臂', '哑铃', '入门', '保持肘部贴近躯干，避免摆动，用肱二头肌控制举起和下放。'),
    (7, '高脚杯深蹲', '腿部', '哑铃', '入门', '双脚稳定踩地，膝盖与脚尖方向一致，下蹲时保持胸口抬起。'),
    (8, '罗马尼亚硬拉', '腿部', '杠铃', '进阶', '髋部向后移动，脊柱保持中立，让杠铃贴近腿部下降至腘绳肌充分拉伸。');

INSERT INTO workout_plan (id, name, description, estimated_minutes, level, weekly_target) VALUES
    (1, '上肢力量 · 基础', '围绕推、拉和肩部稳定建立上肢力量基础，动作节奏优先于重量。', 45, '基础', 3),
    (2, '全身适应训练', '适合恢复训练和动作学习，以中等重量覆盖全身主要肌群。', 38, '入门', 3),
    (3, '下肢力量 · 基础', '强化深蹲和髋铰链模式，建立稳定的下肢发力能力。', 50, '基础', 3);

INSERT INTO plan_exercise (plan_id, exercise_id, exercise_order, target_sets, target_reps, rest_seconds) VALUES
    (1, 1, 1, 4, 8, 120),
    (1, 2, 2, 4, 10, 90),
    (1, 3, 3, 3, 10, 90),
    (1, 4, 4, 3, 12, 75),
    (1, 5, 5, 3, 12, 60),
    (1, 6, 6, 3, 12, 60),
    (2, 7, 1, 3, 12, 90),
    (2, 1, 2, 3, 10, 75),
    (2, 4, 3, 3, 12, 75),
    (3, 7, 1, 4, 10, 120),
    (3, 8, 2, 4, 8, 120);

INSERT INTO workout_session (id, plan_id, name_snapshot, started_at, ended_at, duration_minutes, total_volume_kg, status) VALUES
    (101, 2, '全身适应训练', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY), DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 3 DAY), INTERVAL 38 MINUTE), 38, 1860, 'COMPLETED'),
    (102, 1, '上肢力量 · 基础', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY), DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 6 DAY), INTERVAL 44 MINUTE), 44, 2420, 'COMPLETED'),
    (103, 3, '下肢力量 · 基础', DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 9 DAY), DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 9 DAY), INTERVAL 51 MINUTE), 51, 3180, 'COMPLETED');

INSERT INTO session_exercise (id, session_id, exercise_id, exercise_name_snapshot, exercise_order) VALUES
    (1001, 101, 7, '高脚杯深蹲', 1),
    (1002, 101, 1, '杠铃卧推', 2),
    (1003, 101, 4, '坐姿划船', 3),
    (1004, 101, 5, '绳索下压', 4),
    (1005, 101, 6, '哑铃弯举', 5),
    (1006, 102, 1, '杠铃卧推', 1),
    (1007, 102, 2, '高位下拉', 2),
    (1008, 102, 3, '坐姿哑铃推举', 3),
    (1009, 102, 4, '坐姿划船', 4),
    (1010, 102, 5, '绳索下压', 5),
    (1011, 102, 6, '哑铃弯举', 6),
    (1012, 103, 7, '高脚杯深蹲', 1),
    (1013, 103, 8, '罗马尼亚硬拉', 2),
    (1014, 103, 7, '高脚杯深蹲', 3),
    (1015, 103, 8, '罗马尼亚硬拉', 4),
    (1016, 103, 7, '高脚杯深蹲', 5);

-- 渐进超负荷由用户对模板中的每个动作单独开启，旧模板默认保持关闭。
ALTER TABLE plan_exercise
    ADD COLUMN progressive_overload_enabled BOOLEAN NOT NULL DEFAULT FALSE AFTER rest_seconds;

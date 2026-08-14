-- 每组保存用户真实结束休息时的时长；空值表示尚未产生或未确认组间歇。
-- 独立迁移兼容已经执行 V2 的数据库，新安装也会按 V2 到 V6 的顺序得到相同结构。
ALTER TABLE set_record
    ADD COLUMN rest_duration_seconds INT NULL AFTER repetitions;

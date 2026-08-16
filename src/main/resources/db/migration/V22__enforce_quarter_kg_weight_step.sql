-- 所有训练重量统一保守向下规范到 0.25 kg，随后由数据库阻止绕过应用层的非法写入。
UPDATE set_record
SET weight_kg = FLOOR(weight_kg / 0.25) * 0.25
WHERE MOD(weight_kg, 0.25) <> 0;

ALTER TABLE set_record
    ADD CONSTRAINT chk_set_record_quarter_kg
        CHECK (MOD(weight_kg, 0.25) = 0);

-- Repair installations where V5 was added after a higher Flyway version had already run.
-- Templates keep only exercise IDs; image URLs continue to come from the canonical exercise rows.
UPDATE plan_exercise
SET exercise_id = CASE exercise_id
    WHEN 1 THEN 100025
    WHEN 2 THEN 100673
    WHEN 3 THEN 100405
    WHEN 4 THEN 100861
    WHEN 5 THEN 100201
    WHEN 6 THEN 100294
    WHEN 7 THEN 101760
    WHEN 8 THEN 100085
END
WHERE exercise_id IN (1, 2, 3, 4, 5, 6, 7, 8);

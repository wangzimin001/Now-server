-- Remap V2 seed plan references to the corresponding V4 exercise-dataset rows.
-- Only plan_exercise is changed; session_exercise historical IDs and snapshots stay untouched.
-- No GIF value is persisted in plan_exercise: the API continues to read media from exercise.
--
-- V2 id 1 (barbell bench press)       -> V4 id 100025 (source_exercise_id 0025)
-- V2 id 2 (machine lat pulldown)      -> V4 id 100673 (source_exercise_id 0673, reverse-grip variation)
-- V2 id 3 (seated dumbbell press)     -> V4 id 100405 (source_exercise_id 0405)
-- V2 id 4 (seated cable row)          -> V4 id 100861 (source_exercise_id 0861)
-- V2 id 5 (cable pushdown)            -> V4 id 100201 (source_exercise_id 0201)
-- V2 id 6 (dumbbell curl)             -> V4 id 100294 (source_exercise_id 0294)
-- V2 id 7 (dumbbell goblet squat)     -> V4 id 101760 (source_exercise_id 1760)
-- V2 id 8 (barbell Romanian deadlift) -> V4 id 100085 (source_exercise_id 0085)

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

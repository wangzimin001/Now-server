-- Use anatomically complete shoulder subcategory names while preserving stable codes and mappings.
UPDATE exercise_subcategory
SET name = CASE code
    WHEN 'anterior-deltoid' THEN '三角肌前束'
    WHEN 'lateral-deltoid' THEN '三角肌中束'
    WHEN 'posterior-deltoid' THEN '三角肌后束'
    ELSE name
END
WHERE category_code = 'shoulders'
  AND code IN ('anterior-deltoid', 'lateral-deltoid', 'posterior-deltoid');

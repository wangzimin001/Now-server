package com.wangzimin.now.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ExerciseCategorySchemaTest {

    @Test
    void migrationNormalizesSecondaryCategoriesAndRemovesLegacyJsonColumns() throws IOException {
        String migration = Files.readString(Path.of("src", "main", "resources", "db", "migration",
                "V20__normalize_exercise_subcategories.sql"));

        assertTrue(migration.contains("CREATE TABLE exercise_category"));
        assertTrue(migration.contains("CREATE TABLE exercise_subcategory"));
        assertTrue(migration.contains("CREATE TABLE exercise_subcategory_mapping"));
        assertTrue(migration.contains("DROP COLUMN chest_regions"));
        assertTrue(migration.contains("DROP COLUMN forearm_regions"));
    }

    @Test
    void runtimeQueryUsesGenericSubcategoryTablesOnly() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "com", "wangzimin", "now",
                "repository", "FitnessQueryRepository.java"));

        assertTrue(source.contains("exercise_subcategory_mapping"));
        assertTrue(source.contains("exercise_subcategory"));
        assertFalse(source.contains("chest_regions"));
        assertFalse(source.contains("chestRegion"));
        assertFalse(source.contains("forearmRegion"));
    }

    /** 确认肩部二级分类使用完整解剖名称，稳定编码和动作映射保持不变。 */
    @Test
    void migrationUsesCompleteDeltoidSubcategoryNames() throws IOException {
        String migration = Files.readString(Path.of("src", "main", "resources", "db", "migration",
                "V26__rename_deltoid_subcategories.sql"));

        assertTrue(migration.contains("'anterior-deltoid' THEN '三角肌前束'"));
        assertTrue(migration.contains("'lateral-deltoid' THEN '三角肌中束'"));
        assertTrue(migration.contains("'posterior-deltoid' THEN '三角肌后束'"));
        assertTrue(migration.contains("WHERE category_code = 'shoulders'"));
    }
}

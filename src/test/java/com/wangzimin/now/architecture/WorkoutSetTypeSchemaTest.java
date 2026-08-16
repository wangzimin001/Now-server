package com.wangzimin.now.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WorkoutSetTypeSchemaTest {

    @Test
    void migrationPersistsEverySupportedSetType() throws IOException {
        String migration = Files.readString(Path.of("src", "main", "resources", "db", "migration",
                "V25__add_workout_set_type.sql"));
        assertTrue(migration.contains("ADD COLUMN set_type"));
        assertTrue(migration.contains("'STANDARD'"));
        assertTrue(migration.contains("'WARM_UP'"));
        assertTrue(migration.contains("'DROP_SET'"));
    }
}

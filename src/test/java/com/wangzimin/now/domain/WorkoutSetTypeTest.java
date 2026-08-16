package com.wangzimin.now.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorkoutSetTypeTest {

    @Test
    void separatesVolumeAndPerformanceSemantics() {
        assertTrue(WorkoutSetType.STANDARD.contributesToVolume());
        assertTrue(WorkoutSetType.STANDARD.contributesToPerformance());
        assertFalse(WorkoutSetType.WARM_UP.contributesToVolume());
        assertFalse(WorkoutSetType.WARM_UP.contributesToPerformance());
        assertTrue(WorkoutSetType.DROP_SET.contributesToVolume());
        assertFalse(WorkoutSetType.DROP_SET.contributesToPerformance());
    }

    @Test
    void defaultsMissingLegacyValueToStandard() {
        assertEquals(WorkoutSetType.STANDARD, WorkoutSetType.normalize(null));
        assertEquals(WorkoutSetType.STANDARD, WorkoutSetType.fromDatabaseValue(null));
        assertEquals(WorkoutSetType.DROP_SET, WorkoutSetType.fromDatabaseValue("DROP_SET"));
    }
}

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
        assertTrue(WorkoutSetType.STANDARD.countsAsPrimaryGroup());
        assertFalse(WorkoutSetType.WARM_UP.contributesToVolume());
        assertFalse(WorkoutSetType.WARM_UP.contributesToPerformance());
        assertTrue(WorkoutSetType.WARM_UP.countsAsPrimaryGroup());
        assertTrue(WorkoutSetType.DROP_SET.contributesToVolume());
        assertFalse(WorkoutSetType.DROP_SET.contributesToPerformance());
        assertFalse(WorkoutSetType.DROP_SET.countsAsPrimaryGroup());
    }

    @Test
    void defaultsMissingLegacyValueToStandard() {
        assertEquals(WorkoutSetType.STANDARD, WorkoutSetType.normalize(null));
        assertEquals(WorkoutSetType.STANDARD, WorkoutSetType.fromDatabaseValue(null));
        assertEquals(WorkoutSetType.DROP_SET, WorkoutSetType.fromDatabaseValue("DROP_SET"));
    }
}

package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.wangzimin.now.service.FitnessQueryService.PlanExercise;
import com.wangzimin.now.service.FitnessQueryService.WorkoutPlanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class FitnessQueryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void workoutPlansIncludesUsageLastUsedAndExerciseGifUrl() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec planStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.StatementSpec exerciseStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<FitnessQueryService.WorkoutPlanRow> planQuery = mock(JdbcClient.MappedQuerySpec.class);
        JdbcClient.MappedQuerySpec<PlanExercise> exerciseQuery = mock(JdbcClient.MappedQuerySpec.class);
        LocalDateTime lastUsedAt = LocalDateTime.of(2026, 8, 10, 12, 30);

        when(jdbcClient.sql(anyString())).thenReturn(planStatement, exerciseStatement);
        when(planStatement.query(FitnessQueryService.WorkoutPlanRow.class)).thenReturn(planQuery);
        when(exerciseStatement.query(PlanExercise.class)).thenReturn(exerciseQuery);
        when(planQuery.list()).thenReturn(List.of(
                new FitnessQueryService.WorkoutPlanRow(1L, "Used", "desc", 45, "基础", 2L, lastUsedAt),
                new FitnessQueryService.WorkoutPlanRow(2L, "Unused", "desc", 30, "入门", 0L, null)));
        when(exerciseQuery.list()).thenReturn(List.of(
                new PlanExercise(1L, 10L, "深蹲", "腿部", 3, 10, 90,
                        "/static/exercises/gifs/squat.gif")));

        List<WorkoutPlanResponse> plans = new FitnessQueryService(jdbcClient).workoutPlans();

        assertEquals(2L, plans.get(0).usageCount());
        assertEquals(lastUsedAt, plans.get(0).lastUsedAt());
        assertEquals("/static/exercises/gifs/squat.gif", plans.get(0).exercises().get(0).gifUrl());
        assertEquals(0L, plans.get(1).usageCount());
        assertNull(plans.get(1).lastUsedAt());

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcClient, times(2)).sql(sqlCaptor.capture());
        String planSql = sqlCaptor.getAllValues().get(0);
        assertTrue(planSql.contains("COUNT(*) AS usage_count"));
        assertTrue(planSql.contains("MAX(ended_at) AS last_used_at"));
        assertTrue(planSql.contains("WHERE status = 'COMPLETED'"));
        assertTrue(planSql.contains("GROUP BY plan_id"));
        assertTrue(planSql.contains("LEFT JOIN ("));
        assertFalse(planSql.contains("plan_exercise"));

        String exerciseSql = sqlCaptor.getAllValues().get(1);
        assertTrue(exerciseSql.contains("COALESCE("));
        assertTrue(exerciseSql.contains("NULLIF(e.gif_url, '')"));
        assertTrue(exerciseSql.contains("gif_candidate.name = e.name"));
        assertTrue(exerciseSql.contains("gif_candidate.gif_url IS NOT NULL"));
        assertTrue(exerciseSql.contains("gif_candidate.gif_url <> ''"));
        assertTrue(exerciseSql.contains("ORDER BY gif_candidate.id DESC"));
        assertTrue(exerciseSql.contains("LIMIT 1"));
        assertTrue(exerciseSql.contains("JOIN exercise e ON e.id = pe.exercise_id"));
        assertFalse(exerciseSql.contains("pe.gif_url"));
    }
}

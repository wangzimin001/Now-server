package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.wangzimin.now.service.FitnessQueryService.PlanExercise;
import com.wangzimin.now.service.FitnessQueryService.WorkoutHistoryItem;
import com.wangzimin.now.service.FitnessQueryService.WorkoutPlanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class FitnessQueryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void historyCountsDistinctExercisesAndCompletedSets() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<WorkoutHistoryItem> query = mock(JdbcClient.MappedQuerySpec.class);
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 14, 10, 30);
        WorkoutHistoryItem expected = new WorkoutHistoryItem(
                1L, "上肢力量", completedAt, 45, 5, 16, BigDecimal.valueOf(2400));

        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.param(anyString(), any(), anyInt())).thenReturn(statement);
        when(statement.query(WorkoutHistoryItem.class)).thenReturn(query);
        when(query.list()).thenReturn(List.of(expected));

        List<WorkoutHistoryItem> history = new FitnessQueryService(jdbcClient).history();

        assertEquals(16, history.get(0).completedSetCount());
        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcClient).sql(sqlCaptor.capture());
        String historySql = sqlCaptor.getValue();
        assertTrue(historySql.contains("COUNT(DISTINCT se.id) AS exerciseCount"));
        assertTrue(historySql.contains("sr.status = :completedStatus"));
        assertTrue(historySql.contains("LEFT JOIN set_record sr"));
        assertTrue(historySql.contains("ws.owner_user_id = :userId"));
        assertTrue(historySql.contains("LIMIT :historyLimit"));
    }

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
        when(planStatement.param(anyString(), any(), anyInt())).thenReturn(planStatement);
        when(exerciseStatement.param(anyString(), any(), anyInt())).thenReturn(exerciseStatement);
        when(planStatement.param(anyString(), any())).thenReturn(planStatement);
        when(planStatement.query(FitnessQueryService.WorkoutPlanRow.class)).thenReturn(planQuery);
        when(exerciseStatement.query(PlanExercise.class)).thenReturn(exerciseQuery);
        when(planQuery.list()).thenReturn(List.of(
                new FitnessQueryService.WorkoutPlanRow(1L, "Used", "desc", 45, 2L, lastUsedAt),
                new FitnessQueryService.WorkoutPlanRow(2L, "Unused", "desc", 30, 0L, null)));
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
        assertTrue(planSql.contains("WHERE status = :completedStatus"));
        assertTrue(planSql.contains("GROUP BY plan_id"));
        assertTrue(planSql.contains("LEFT JOIN ("));
        assertTrue(planSql.contains("wp.owner_user_id IS NULL OR wp.owner_user_id = :userId"));
        assertTrue(planSql.contains("owner_user_id = :userId"));
        assertTrue(planSql.contains("user_hidden_workout_plan"));
        assertTrue(planSql.contains("hidden.user_id = :userId"));
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

    @Test
    void latestPerformanceSkipsDatabaseWhenExerciseIdsAreEmpty() {
        JdbcClient jdbcClient = mock(JdbcClient.class);

        assertTrue(new FitnessQueryService(jdbcClient).latestExercisePerformances(7L, List.of()).isEmpty());

        verify(jdbcClient, times(0)).sql(anyString());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void latestPerformanceUsesCurrentUserAndMostRecentCompletedExercise() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec query = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.query(any(Class.class))).thenReturn(query);
        when(query.list()).thenReturn(List.of());

        assertTrue(new FitnessQueryService(jdbcClient)
                .latestExercisePerformances(7L, List.of(100025L, 100026L)).isEmpty());

        var sqlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcClient).sql(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("ws.owner_user_id = :userId"));
        assertTrue(sql.contains("PARTITION BY se.exercise_id"));
        assertTrue(sql.contains("ORDER BY ws.ended_at DESC, se.id DESC"));
        assertTrue(sql.contains("completed_set.status = :completedStatus"));
        assertTrue(sql.contains("ranked.performanceRank = :latestRank"));
        assertTrue(sql.contains("sr.status = :completedStatus"));
    }
}

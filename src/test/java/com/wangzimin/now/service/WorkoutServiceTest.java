package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.List;

import com.wangzimin.now.service.WorkoutService.ExercisePerformanceSummary;
import com.wangzimin.now.service.WorkoutService.HistoricalExerciseMetricRow;
import com.wangzimin.now.service.WorkoutService.HistoricalWeightRepetitionRow;
import com.wangzimin.now.service.WorkoutService.WorkoutExerciseRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutSetRequest;

class WorkoutServiceTest {

    @Test
    void deletingOwnedPlanSoftDeletesIt() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.update()).thenReturn(1);

        new WorkoutService(jdbcClient).deletePlan(7L, 9L);

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient).sql(sql.capture());
        assertTrue(sql.getValue().contains("SET is_active = FALSE"));
        assertTrue(sql.getValue().contains("owner_user_id = :userId"));
    }

    @Test
    void deletingSystemPlanHidesItOnlyForCurrentUser() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec ownedStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.StatementSpec hiddenStatement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(ownedStatement, hiddenStatement);
        when(ownedStatement.param(anyString(), any())).thenReturn(ownedStatement);
        when(hiddenStatement.param(anyString(), any())).thenReturn(hiddenStatement);
        when(ownedStatement.update()).thenReturn(0);
        when(hiddenStatement.update()).thenReturn(1);

        new WorkoutService(jdbcClient).deletePlan(7L, 9L);

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient, times(2)).sql(sql.capture());
        assertTrue(sql.getAllValues().get(1).contains("INSERT IGNORE INTO user_hidden_workout_plan"));
        assertTrue(sql.getAllValues().get(1).contains("owner_user_id IS NULL"));
    }

    @Test
    void deletingWorkoutHistoryUsesOwnershipScopedSoftDelete() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.param(anyString(), any(), any(Integer.class))).thenReturn(statement);
        when(statement.update()).thenReturn(1);

        new WorkoutService(jdbcClient).deleteWorkoutHistory(7L, 201L);

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcClient).sql(sql.capture());
        assertTrue(sql.getValue().contains("SET status = :deletedStatus"));
        assertTrue(sql.getValue().contains("owner_user_id = :userId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void detectsSixMeaningfulExerciseRecordsAgainstHistory() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec metricStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.StatementSpec weightRepStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<HistoricalExerciseMetricRow> metricQuery = mock(JdbcClient.MappedQuerySpec.class);
        JdbcClient.MappedQuerySpec<HistoricalWeightRepetitionRow> weightRepQuery = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(metricStatement, weightRepStatement);
        when(metricStatement.param(anyString(), any(), any(Integer.class))).thenReturn(metricStatement);
        when(weightRepStatement.param(anyString(), any(), any(Integer.class))).thenReturn(weightRepStatement);
        when(metricStatement.param(anyString(), any())).thenReturn(metricStatement);
        when(weightRepStatement.param(anyString(), any())).thenReturn(weightRepStatement);
        when(metricStatement.query(HistoricalExerciseMetricRow.class)).thenReturn(metricQuery);
        when(weightRepStatement.query(HistoricalWeightRepetitionRow.class)).thenReturn(weightRepQuery);
        when(metricQuery.list()).thenReturn(List.of(new HistoricalExerciseMetricRow(
                100025L, BigDecimal.valueOf(57.5), BigDecimal.valueOf(75), BigDecimal.valueOf(600),
                BigDecimal.valueOf(1100), 20)));
        when(weightRepQuery.list()).thenReturn(List.of(
                new HistoricalWeightRepetitionRow(100025L, BigDecimal.valueOf(60), 8)));

        WorkoutExerciseRequest exercise = new WorkoutExerciseRequest(100025L, "杠铃卧推", List.of(
                new WorkoutSetRequest(1, BigDecimal.valueOf(60), 10, 90, true),
                new WorkoutSetRequest(2, BigDecimal.valueOf(55), 12, 90, true)));
        ExercisePerformanceSummary summary = new WorkoutService(jdbcClient)
                .evaluateExercisePerformance(7L, List.of(exercise)).get(0);

        assertFalse(summary.firstRecorded());
        assertEquals(6, summary.achievements().size());
        assertTrue(summary.achievements().stream().anyMatch(item -> item.type().equals("MAX_WEIGHT")));
        assertTrue(summary.achievements().stream().anyMatch(item -> item.type().equals("SAME_WEIGHT_REPS")));
        assertTrue(summary.achievements().stream().anyMatch(item -> item.type().equals("MAX_SET_VOLUME")));
        assertTrue(summary.achievements().stream().anyMatch(item -> item.type().equals("EXERCISE_VOLUME")));
        assertEquals(BigDecimal.valueOf(1260), summary.totalVolumeKg());
        assertEquals(22, summary.totalRepetitions());
    }
}

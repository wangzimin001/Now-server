package com.wangzimin.now.service;

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
}

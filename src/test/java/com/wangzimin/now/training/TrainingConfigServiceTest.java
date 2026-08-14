package com.wangzimin.now.training;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class TrainingConfigServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void firstConfigIsInsertedAndReturnedWithRevision() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec findStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.StatementSpec insertStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.StatementSpec reloadStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<TrainingConfigService.TrainingConfigResponse> emptyQuery = mock(JdbcClient.MappedQuerySpec.class);
        JdbcClient.MappedQuerySpec<TrainingConfigService.TrainingConfigResponse> savedQuery = mock(JdbcClient.MappedQuerySpec.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Instant updatedAt = Instant.parse("2026-08-14T07:00:00Z");
        var expected = new TrainingConfigService.TrainingConfigResponse(
                "cycle", objectMapper.createObjectNode().putArray("days").addObject(), updatedAt, updatedAt, 1, true);

        when(jdbcClient.sql(anyString())).thenReturn(findStatement, insertStatement, reloadStatement);
        when(findStatement.param(anyString(), any())).thenReturn(findStatement);
        when(insertStatement.param(anyString(), any())).thenReturn(insertStatement);
        when(reloadStatement.param(anyString(), any())).thenReturn(reloadStatement);
        when(findStatement.query(any(RowMapper.class))).thenReturn(emptyQuery);
        when(reloadStatement.query(any(RowMapper.class))).thenReturn(savedQuery);
        when(emptyQuery.optional()).thenReturn(Optional.empty());
        when(savedQuery.optional()).thenReturn(Optional.of(expected));
        when(insertStatement.update()).thenReturn(1);

        ObjectNode cyclePlan = objectMapper.createObjectNode();
        cyclePlan.putArray("days").addObject().put("type", "rest");
        var request = new TrainingConfigService.TrainingConfigRequest("cycle", cyclePlan, updatedAt);
        var result = new TrainingConfigService(jdbcClient, objectMapper).save(9L, request);

        assertEquals(1, result.revision());
        assertEquals("cycle", result.trainingMode());
        verify(insertStatement).update();
    }

    @Test
    @SuppressWarnings("unchecked")
    void olderOfflineChangeDoesNotOverwriteNewerRemoteConfig() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<TrainingConfigService.TrainingConfigResponse> query = mock(JdbcClient.MappedQuerySpec.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Instant remoteTime = Instant.parse("2026-08-14T06:00:00Z");
        var remote = new TrainingConfigService.TrainingConfigResponse(
                "cycle", objectMapper.createObjectNode(), remoteTime, remoteTime, 4, true);

        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.query(any(RowMapper.class))).thenReturn(query);
        when(query.optional()).thenReturn(Optional.of(remote));

        var request = new TrainingConfigService.TrainingConfigRequest(
                "free", null, Instant.parse("2026-08-14T05:00:00Z"));
        var result = new TrainingConfigService(jdbcClient, objectMapper).save(9L, request);

        assertFalse(result.applied());
        assertEquals(4, result.revision());
        assertEquals("cycle", result.trainingMode());
        verify(statement, never()).update();
    }

    @Test
    void cyclePlanRejectsMoreThanThirtyDays() {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode cyclePlan = objectMapper.createObjectNode();
        ArrayNode days = cyclePlan.putArray("days");
        for (int index = 0; index < 31; index++) days.addObject().put("type", "rest");

        var service = new TrainingConfigService(mock(JdbcClient.class), objectMapper);
        var request = new TrainingConfigService.TrainingConfigRequest("cycle", cyclePlan, Instant.now());

        assertThrows(ResponseStatusException.class, () -> service.save(9L, request));
    }
}

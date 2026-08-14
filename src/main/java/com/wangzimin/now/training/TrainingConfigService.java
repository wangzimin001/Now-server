package com.wangzimin.now.training;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Service
public class TrainingConfigService {

    private static final int MAX_PLAN_BYTES = 64 * 1024;

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public TrainingConfigService(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public TrainingConfigResponse get(Long userId) {
        return find(userId, false).orElseGet(() -> TrainingConfigResponse.empty());
    }

    @Transactional
    public TrainingConfigResponse save(Long userId, TrainingConfigRequest request) {
        validateCyclePlan(request.cyclePlan());
        var existing = find(userId, true);
        if (existing.isPresent() && request.clientUpdatedAt().isBefore(existing.get().clientUpdatedAt())) {
            return existing.get().withApplied(false);
        }

        String cyclePlan = writeCyclePlan(request.cyclePlan());
        if (existing.isEmpty()) {
            jdbcClient.sql("""
                            INSERT INTO user_training_config
                                (user_id, training_mode, cycle_plan, client_updated_at, revision)
                            VALUES (:userId, :trainingMode, CAST(:cyclePlan AS JSON), :clientUpdatedAt, 1)
                            """)
                    .param("userId", userId)
                    .param("trainingMode", request.trainingMode())
                    .param("cyclePlan", cyclePlan)
                    .param("clientUpdatedAt", Timestamp.from(request.clientUpdatedAt()))
                    .update();
        } else {
            jdbcClient.sql("""
                            UPDATE user_training_config
                            SET training_mode = :trainingMode,
                                cycle_plan = CAST(:cyclePlan AS JSON),
                                client_updated_at = :clientUpdatedAt,
                                revision = revision + 1
                            WHERE user_id = :userId
                            """)
                    .param("trainingMode", request.trainingMode())
                    .param("cyclePlan", cyclePlan)
                    .param("clientUpdatedAt", Timestamp.from(request.clientUpdatedAt()))
                    .param("userId", userId)
                    .update();
        }
        return find(userId, false).orElseThrow(() -> new IllegalStateException("训练配置保存后无法读取"));
    }

    private java.util.Optional<TrainingConfigResponse> find(Long userId, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        return jdbcClient.sql("""
                        SELECT training_mode, cycle_plan, client_updated_at, updated_at, revision
                        FROM user_training_config
                        WHERE user_id = :userId
                        """ + lock)
                .param("userId", userId)
                .query(this::mapRow)
                .optional();
    }

    private TrainingConfigResponse mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            String rawPlan = resultSet.getString("cycle_plan");
            JsonNode cyclePlan = rawPlan == null ? null : objectMapper.readTree(rawPlan);
            return new TrainingConfigResponse(
                    resultSet.getString("training_mode"),
                    cyclePlan,
                    resultSet.getTimestamp("client_updated_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant(),
                    resultSet.getLong("revision"),
                    true);
        } catch (JacksonException exception) {
            throw new SQLException("训练配置 JSON 无法解析", exception);
        }
    }

    private void validateCyclePlan(JsonNode cyclePlan) {
        if (cyclePlan == null || cyclePlan.isNull()) return;
        if (!cyclePlan.isObject()) throw badRequest("训练周期格式不正确");
        JsonNode days = cyclePlan.path("days");
        if (!days.isArray() || days.isEmpty() || days.size() > 30) {
            throw badRequest("训练周期需要包含 1 至 30 天");
        }
        if (writeCyclePlan(cyclePlan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PLAN_BYTES) {
            throw badRequest("训练周期内容过大");
        }
    }

    private String writeCyclePlan(JsonNode cyclePlan) {
        try {
            return cyclePlan == null || cyclePlan.isNull() ? "null" : objectMapper.writeValueAsString(cyclePlan);
        } catch (JacksonException exception) {
            throw badRequest("训练周期格式不正确");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    public record TrainingConfigRequest(
            @NotBlank @Pattern(regexp = "free|cycle") String trainingMode,
            JsonNode cyclePlan,
            @NotNull Instant clientUpdatedAt) {
    }

    public record TrainingConfigResponse(
            String trainingMode,
            JsonNode cyclePlan,
            Instant clientUpdatedAt,
            Instant serverUpdatedAt,
            long revision,
            boolean applied) {

        static TrainingConfigResponse empty() {
            return new TrainingConfigResponse("free", null, null, null, 0, true);
        }

        TrainingConfigResponse withApplied(boolean value) {
            return new TrainingConfigResponse(trainingMode, cyclePlan, clientUpdatedAt, serverUpdatedAt, revision, value);
        }
    }
}

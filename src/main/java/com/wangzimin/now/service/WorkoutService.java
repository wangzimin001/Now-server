package com.wangzimin.now.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkoutService {

    private final JdbcClient jdbcClient;

    public WorkoutService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public Long createPlan(WorkoutPlanRequest request) {
        return createPlan(null, request);
    }

    @Transactional
    public Long createPlan(Long userId, WorkoutPlanRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO workout_plan
                            (owner_user_id, name, description, estimated_minutes, weekly_target, is_active)
                        VALUES (:userId, :name, :description, :estimatedMinutes, 3, TRUE)
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("name", request.name().trim())
                .param("description", cleanDescription(request.description()))
                .param("estimatedMinutes", request.estimatedMinutes())
                .update(keyHolder, "id");

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建训练模板后未返回主键");
        }
        long planId = key.longValue();
        replacePlanExercises(planId, request.exercises());
        return planId;
    }

    @Transactional
    public void updatePlan(Long planId, WorkoutPlanRequest request) {
        updatePlan(null, planId, request);
    }

    @Transactional
    public void updatePlan(Long userId, Long planId, WorkoutPlanRequest request) {
        int changed = jdbcClient.sql("""
                        UPDATE workout_plan
                        SET name = :name,
                            description = :description,
                            estimated_minutes = :estimatedMinutes,
                            is_active = TRUE
                        WHERE id = :planId AND is_active = TRUE
                          AND ((:userId IS NULL AND owner_user_id IS NULL) OR owner_user_id = :userId)
                        """)
                .param("name", request.name().trim())
                .param("description", cleanDescription(request.description()))
                .param("estimatedMinutes", request.estimatedMinutes())
                .param("planId", planId)
                .param("userId", userId, Types.BIGINT)
                .update();
        requirePlan(changed);
        replacePlanExercises(planId, request.exercises());
    }

    @Transactional
    public void deletePlan(Long planId) {
        deletePlan(null, planId);
    }

    @Transactional
    public void deletePlan(Long userId, Long planId) {
        int changed = jdbcClient.sql("""
                        UPDATE workout_plan
                        SET is_active = FALSE
                        WHERE id = :planId AND is_active = TRUE
                          AND ((:userId IS NULL AND owner_user_id IS NULL) OR owner_user_id = :userId)
                        """)
                .param("planId", planId)
                .param("userId", userId, Types.BIGINT)
                .update();
        requirePlan(changed);
    }

    @Transactional
    public WorkoutCompletionResponse completeWorkout(WorkoutCompletionRequest request) {
        return completeWorkout(null, request);
    }

    @Transactional
    public WorkoutCompletionResponse completeWorkout(Long userId, WorkoutCompletionRequest request) {
        if (request.endedAt().isBefore(request.startedAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "训练结束时间不能早于开始时间");
        }

        if (request.clientRecordId() != null && !request.clientRecordId().isBlank()) {
            WorkoutCompletionResponse existing = jdbcClient.sql("""
                            SELECT id, total_volume_kg AS totalVolumeKg
                            FROM workout_session
                            WHERE owner_user_id = :userId AND client_record_id = :clientRecordId
                            """)
                    .param("userId", userId, Types.BIGINT)
                    .param("clientRecordId", request.clientRecordId().trim())
                    .query(WorkoutCompletionResponse.class)
                    .optional()
                    .orElse(null);
            if (existing != null) return existing;
        }

        validatePlanAccess(userId, request.planId());

        BigDecimal totalVolume = request.exercises().stream()
                .flatMap(exercise -> exercise.sets().stream())
                .filter(set -> Boolean.TRUE.equals(set.completed()))
                .map(set -> set.weightKg().multiply(BigDecimal.valueOf(set.repetitions())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        KeyHolder sessionKeyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO workout_session
                            (owner_user_id, client_record_id, plan_id, name_snapshot, started_at, ended_at, duration_minutes, total_volume_kg, status)
                        VALUES
                            (:userId, :clientRecordId, :planId, :name, :startedAt, :endedAt, :durationMinutes, :totalVolume, 'COMPLETED')
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("clientRecordId", cleanClientRecordId(request.clientRecordId()), Types.VARCHAR)
                .param("planId", request.planId(), Types.BIGINT)
                .param("name", request.name().trim())
                .param("startedAt", Timestamp.from(request.startedAt()))
                .param("endedAt", Timestamp.from(request.endedAt()))
                .param("durationMinutes", request.durationMinutes())
                .param("totalVolume", totalVolume)
                .update(sessionKeyHolder, "id");

        Number sessionKey = sessionKeyHolder.getKey();
        if (sessionKey == null) {
            throw new IllegalStateException("保存训练后未返回主键");
        }
        long sessionId = sessionKey.longValue();

        for (int exerciseIndex = 0; exerciseIndex < request.exercises().size(); exerciseIndex++) {
            WorkoutExerciseRequest exercise = request.exercises().get(exerciseIndex);
            long sessionExerciseId = insertSessionExercise(sessionId, exercise, exerciseIndex + 1);
            insertSetRecords(sessionExerciseId, exercise.sets(), request.endedAt());
        }

        return new WorkoutCompletionResponse(sessionId, totalVolume);
    }

    private void validatePlanAccess(Long userId, Long planId) {
        if (planId == null) return;
        int count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM workout_plan
                        WHERE id = :planId AND is_active = TRUE
                          AND (owner_user_id IS NULL OR owner_user_id = :userId)
                        """)
                .param("planId", planId)
                .param("userId", userId, Types.BIGINT)
                .query(Integer.class)
                .single();
        if (count == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "训练模板不存在");
    }

    private void replacePlanExercises(Long planId, List<PlanExerciseRequest> exercises) {
        jdbcClient.sql("DELETE FROM plan_exercise WHERE plan_id = :planId")
                .param("planId", planId)
                .update();

        for (int index = 0; index < exercises.size(); index++) {
            PlanExerciseRequest exercise = exercises.get(index);
            jdbcClient.sql("""
                            INSERT INTO plan_exercise
                                (plan_id, exercise_id, exercise_order, target_sets, target_reps, rest_seconds)
                            VALUES
                                (:planId, :exerciseId, :exerciseOrder, :targetSets, :targetReps, :restSeconds)
                            """)
                    .param("planId", planId)
                    .param("exerciseId", exercise.exerciseId())
                    .param("exerciseOrder", index + 1)
                    .param("targetSets", exercise.targetSets())
                    .param("targetReps", exercise.targetReps())
                    .param("restSeconds", exercise.restSeconds())
                    .update();
        }
    }

    private long insertSessionExercise(Long sessionId, WorkoutExerciseRequest exercise, int order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO session_exercise
                            (session_id, exercise_id, exercise_name_snapshot, exercise_order)
                        VALUES (:sessionId, :exerciseId, :name, :exerciseOrder)
                        """)
                .param("sessionId", sessionId)
                .param("exerciseId", exercise.exerciseId())
                .param("name", exercise.name().trim())
                .param("exerciseOrder", order)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("保存训练动作后未返回主键");
        }
        return key.longValue();
    }

    private void insertSetRecords(Long sessionExerciseId, List<WorkoutSetRequest> sets, Instant endedAt) {
        for (int index = 0; index < sets.size(); index++) {
            WorkoutSetRequest set = sets.get(index);
            boolean completed = Boolean.TRUE.equals(set.completed());
            // 每组独立保存实际组间歇；未确认或未完成的组允许为空，不能伪造成零秒。
            // 该字段与重量、次数处于同一事务中，训练保存失败时会整体回滚。
            jdbcClient.sql("""
                            INSERT INTO set_record
                                (session_exercise_id, set_number, weight_kg, repetitions, rest_duration_seconds, status, completed_at)
                            VALUES
                                (:sessionExerciseId, :setNumber, :weightKg, :repetitions, :restDurationSeconds, :status,
                                 CASE WHEN :status = 'COMPLETED' THEN :endedAt ELSE NULL END)
                            """)
                    .param("sessionExerciseId", sessionExerciseId)
                    .param("setNumber", index + 1)
                    .param("weightKg", set.weightKg())
                    .param("repetitions", set.repetitions())
                    .param("restDurationSeconds", set.restDurationSeconds(), Types.INTEGER)
                    .param("status", completed ? "COMPLETED" : "SKIPPED")
                    .param("endedAt", Timestamp.from(endedAt))
                    .update();
        }
    }

    private String cleanDescription(String description) {
        return description == null ? "" : description.trim();
    }

    private String cleanClientRecordId(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requirePlan(int changed) {
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "训练模板不存在");
        }
    }

    public record WorkoutPlanRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            @NotNull @Min(1) @Max(600) Integer estimatedMinutes,
            @NotNull @Size(min = 1, max = 50) List<@Valid PlanExerciseRequest> exercises) {
    }

    public record PlanExerciseRequest(
            @NotNull @Positive Long exerciseId,
            @NotNull @Min(1) @Max(20) Integer targetSets,
            @NotNull @Min(1) @Max(999) Integer targetReps,
            @NotNull @Min(0) @Max(600) Integer restSeconds) {
    }

    public record WorkoutCompletionRequest(
            @Positive Long planId,
            @NotBlank @Size(max = 80) String name,
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @NotNull @Min(1) @Max(1440) Integer durationMinutes,
            @NotNull @Size(min = 1, max = 100) List<@Valid WorkoutExerciseRequest> exercises,
            @Size(max = 80) String clientRecordId) {

        public WorkoutCompletionRequest(Long planId, String name, Instant startedAt, Instant endedAt,
                Integer durationMinutes, List<WorkoutExerciseRequest> exercises) {
            this(planId, name, startedAt, endedAt, durationMinutes, exercises, null);
        }
    }

    public record WorkoutExerciseRequest(
            @NotNull @Positive Long exerciseId,
            @NotBlank @Size(max = 80) String name,
            @NotNull @Size(min = 1, max = 50) List<@Valid WorkoutSetRequest> sets) {
    }

    public record WorkoutSetRequest(
            // number 由客户端表达顺序，服务端落库时仍按列表位置生成连续组号。
            @NotNull @Positive Integer number,
            @NotNull @DecimalMin("0.0") BigDecimal weightKg,
            @NotNull @Min(0) @Max(999) Integer repetitions,
            // 正向计时可超过模板预设，因此上限按最长训练会话的一天设置。
            @Min(0) @Max(86400) Integer restDurationSeconds,
            @NotNull Boolean completed) {
    }

    public record WorkoutCompletionResponse(Long id, BigDecimal totalVolumeKg) {
    }
}

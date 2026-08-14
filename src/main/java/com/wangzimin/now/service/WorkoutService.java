package com.wangzimin.now.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        if (userId == null) {
            int changed = jdbcClient.sql("""
                            UPDATE workout_plan
                            SET is_active = FALSE
                            WHERE id = :planId AND is_active = TRUE AND owner_user_id IS NULL
                            """)
                    .param("planId", planId)
                    .update();
            requirePlan(changed);
            return;
        }

        int changed = jdbcClient.sql("""
                        UPDATE workout_plan
                        SET is_active = FALSE
                        WHERE id = :planId AND is_active = TRUE
                          AND owner_user_id = :userId
                        """)
                .param("planId", planId)
                .param("userId", userId)
                .update();
        if (changed > 0) return;

        int hidden = jdbcClient.sql("""
                        INSERT IGNORE INTO user_hidden_workout_plan (user_id, plan_id)
                        SELECT :userId, id
                        FROM workout_plan
                        WHERE id = :planId AND is_active = TRUE AND owner_user_id IS NULL
                        """)
                .param("userId", userId)
                .param("planId", planId)
                .update();
        requirePlan(hidden);
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
            ExistingWorkout existing = jdbcClient.sql("""
                            SELECT id, total_volume_kg AS totalVolumeKg
                            FROM workout_session
                            WHERE owner_user_id = :userId AND client_record_id = :clientRecordId
                            """)
                    .param("userId", userId, Types.BIGINT)
                    .param("clientRecordId", request.clientRecordId().trim())
                    .query(ExistingWorkout.class)
                    .optional()
                    .orElse(null);
            if (existing != null) return new WorkoutCompletionResponse(existing.id(), existing.totalVolumeKg(), List.of(), null);
        }

        validatePlanAccess(userId, request.planId());

        List<ExercisePerformanceSummary> exerciseSummaries = evaluateExercisePerformance(userId, request.exercises());

        BigDecimal totalVolume = request.exercises().stream()
                .flatMap(exercise -> exercise.sets().stream())
                .filter(set -> Boolean.TRUE.equals(set.completed()))
                .map(set -> set.weightKg().multiply(BigDecimal.valueOf(set.repetitions())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        WorkoutComparison comparison = compareWithPreviousWorkout(userId, request, totalVolume);

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

        return new WorkoutCompletionResponse(sessionId, totalVolume, exerciseSummaries, comparison);
    }

    private WorkoutComparison compareWithPreviousWorkout(Long userId, WorkoutCompletionRequest request,
            BigDecimal totalVolume) {
        PreviousWorkout previous = jdbcClient.sql("""
                        SELECT ws.total_volume_kg AS totalVolumeKg,
                               ws.duration_minutes AS durationMinutes,
                               SUM(CASE WHEN sr.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedSetCount
                        FROM workout_session ws
                        LEFT JOIN session_exercise se ON se.session_id = ws.id
                        LEFT JOIN set_record sr ON sr.session_exercise_id = se.id
                        WHERE ws.status = 'COMPLETED'
                          AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                          AND ((:planId IS NOT NULL AND ws.plan_id = :planId)
                               OR (:planId IS NULL AND ws.plan_id IS NULL AND ws.name_snapshot = :name))
                        GROUP BY ws.id, ws.total_volume_kg, ws.duration_minutes, ws.ended_at
                        ORDER BY ws.ended_at DESC
                        LIMIT 1
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("planId", request.planId(), Types.BIGINT)
                .param("name", request.name().trim())
                .query(PreviousWorkout.class)
                .optional()
                .orElse(null);
        if (previous == null) return null;
        int completedSetCount = request.exercises().stream()
                .mapToInt(exercise -> (int) exercise.sets().stream().filter(set -> Boolean.TRUE.equals(set.completed())).count())
                .sum();
        BigDecimal volumeDifference = totalVolume.subtract(previous.totalVolumeKg());
        BigDecimal volumePercent = previous.totalVolumeKg().compareTo(BigDecimal.ZERO) > 0
                ? volumeDifference.multiply(BigDecimal.valueOf(100))
                        .divide(previous.totalVolumeKg(), 2, RoundingMode.HALF_UP)
                : null;
        return new WorkoutComparison(roundMetric(previous.totalVolumeKg()), previous.completedSetCount(),
                previous.durationMinutes(), roundMetric(volumeDifference), volumePercent,
                completedSetCount - previous.completedSetCount(), request.durationMinutes() - previous.durationMinutes());
    }

    List<ExercisePerformanceSummary> evaluateExercisePerformance(Long userId, List<WorkoutExerciseRequest> exercises) {
        List<Long> exerciseIds = exercises.stream().map(WorkoutExerciseRequest::exerciseId).distinct().toList();
        Map<Long, HistoricalExerciseMetricRow> baselines = loadHistoricalExerciseMetrics(userId, exerciseIds).stream()
                .collect(Collectors.toMap(HistoricalExerciseMetricRow::exerciseId, item -> item));
        Map<Long, Map<BigDecimal, Integer>> repetitionsByWeight = new LinkedHashMap<>();
        loadHistoricalWeightRepetitions(userId, exerciseIds).forEach(item -> repetitionsByWeight
                .computeIfAbsent(item.exerciseId(), ignored -> new LinkedHashMap<>())
                .put(normalizeWeight(item.weightKg()), item.maxRepetitions()));

        List<ExercisePerformanceSummary> summaries = new ArrayList<>();
        for (WorkoutExerciseRequest exercise : exercises) {
            List<WorkoutSetRequest> completedSets = exercise.sets().stream()
                    .filter(set -> Boolean.TRUE.equals(set.completed()))
                    .toList();
            CurrentExerciseMetrics current = currentMetrics(completedSets);
            HistoricalExerciseMetricRow previous = baselines.get(exercise.exerciseId());
            Map<BigDecimal, Integer> previousWeightRepetitions = repetitionsByWeight
                    .getOrDefault(exercise.exerciseId(), Map.of());
            List<ExerciseAchievement> achievements = previous == null
                    ? List.of()
                    : buildAchievements(exercise, current, previous, previousWeightRepetitions);
            PersonalBestSnapshot personalBest = personalBest(current, previous, previousWeightRepetitions);
            summaries.add(new ExercisePerformanceSummary(
                    exercise.exerciseId(), exercise.name(), completedSets.size(), current.totalRepetitions(),
                    current.totalVolumeKg(), current.maxWeightKg(), current.estimatedOneRepMaxKg(),
                    previous == null, achievements, personalBest));
        }
        return summaries;
    }

    private List<HistoricalExerciseMetricRow> loadHistoricalExerciseMetrics(Long userId, List<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) return List.of();
        return jdbcClient.sql("""
                        WITH exercise_session_metrics AS (
                            SELECT se.exercise_id AS exerciseId, se.session_id,
                                   SUM(sr.weight_kg * sr.repetitions) AS totalVolumeKg,
                                   SUM(sr.repetitions) AS totalRepetitions
                            FROM session_exercise se
                            JOIN workout_session ws ON ws.id = se.session_id
                            JOIN set_record sr ON sr.session_exercise_id = se.id AND sr.status = 'COMPLETED'
                            WHERE ws.status = 'COMPLETED'
                              AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                              AND se.exercise_id IN (:exerciseIds)
                            GROUP BY se.exercise_id, se.session_id
                        )
                        SELECT se.exercise_id AS exerciseId,
                               MAX(sr.weight_kg) AS maxWeightKg,
                               MAX(sr.weight_kg * (1 + sr.repetitions / 30.0)) AS estimatedOneRepMaxKg,
                               MAX(sr.weight_kg * sr.repetitions) AS maxSetVolumeKg,
                               MAX(metrics.totalVolumeKg) AS maxExerciseVolumeKg,
                               MAX(metrics.totalRepetitions) AS maxExerciseRepetitions
                        FROM session_exercise se
                        JOIN workout_session ws ON ws.id = se.session_id
                        JOIN set_record sr ON sr.session_exercise_id = se.id AND sr.status = 'COMPLETED'
                        JOIN exercise_session_metrics metrics
                          ON metrics.exerciseId = se.exercise_id AND metrics.session_id = se.session_id
                        WHERE ws.status = 'COMPLETED'
                          AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                          AND se.exercise_id IN (:exerciseIds)
                        GROUP BY se.exercise_id
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("exerciseIds", exerciseIds)
                .query(HistoricalExerciseMetricRow.class)
                .list();
    }

    private List<HistoricalWeightRepetitionRow> loadHistoricalWeightRepetitions(Long userId, List<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) return List.of();
        return jdbcClient.sql("""
                        SELECT se.exercise_id AS exerciseId, sr.weight_kg AS weightKg,
                               MAX(sr.repetitions) AS maxRepetitions
                        FROM session_exercise se
                        JOIN workout_session ws ON ws.id = se.session_id
                        JOIN set_record sr ON sr.session_exercise_id = se.id
                        WHERE ws.status = 'COMPLETED' AND sr.status = 'COMPLETED'
                          AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                          AND se.exercise_id IN (:exerciseIds)
                        GROUP BY se.exercise_id, sr.weight_kg
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("exerciseIds", exerciseIds)
                .query(HistoricalWeightRepetitionRow.class)
                .list();
    }

    private CurrentExerciseMetrics currentMetrics(List<WorkoutSetRequest> sets) {
        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal maxWeight = BigDecimal.ZERO;
        BigDecimal maxSetVolume = BigDecimal.ZERO;
        BigDecimal maxEstimatedOneRepMax = BigDecimal.ZERO;
        int totalRepetitions = 0;
        Map<BigDecimal, Integer> repetitionsByWeight = new LinkedHashMap<>();
        for (WorkoutSetRequest set : sets) {
            BigDecimal weight = normalizeWeight(set.weightKg());
            BigDecimal setVolume = weight.multiply(BigDecimal.valueOf(set.repetitions()));
            BigDecimal estimatedOneRepMax = estimatedOneRepMax(weight, set.repetitions());
            totalVolume = totalVolume.add(setVolume);
            totalRepetitions += set.repetitions();
            maxWeight = maxWeight.max(weight);
            maxSetVolume = maxSetVolume.max(setVolume);
            maxEstimatedOneRepMax = maxEstimatedOneRepMax.max(estimatedOneRepMax);
            repetitionsByWeight.merge(weight, set.repetitions(), Math::max);
        }
        return new CurrentExerciseMetrics(totalVolume, totalRepetitions, maxWeight, maxSetVolume,
                maxEstimatedOneRepMax, repetitionsByWeight);
    }

    private List<ExerciseAchievement> buildAchievements(WorkoutExerciseRequest exercise, CurrentExerciseMetrics current,
            HistoricalExerciseMetricRow previous, Map<BigDecimal, Integer> previousRepetitionsByWeight) {
        List<ExerciseAchievement> achievements = new ArrayList<>();
        addAchievement(achievements, "MAX_WEIGHT", "最大重量", current.maxWeightKg(), previous.maxWeightKg(), "kg", "");
        addAchievement(achievements, "ESTIMATED_1RM", "估算 1RM", current.estimatedOneRepMaxKg(),
                previous.estimatedOneRepMaxKg(), "kg", "综合重量与次数估算");
        addAchievement(achievements, "MAX_SET_VOLUME", "单组最大容量", current.maxSetVolumeKg(),
                previous.maxSetVolumeKg(), "kg", "单组重量 × 次数");
        addAchievement(achievements, "EXERCISE_VOLUME", "动作最大容量", current.totalVolumeKg(),
                previous.maxExerciseVolumeKg(), "kg", "本次该动作全部完成组");
        addAchievement(achievements, "EXERCISE_REPS", "动作最多次数", BigDecimal.valueOf(current.totalRepetitions()),
                BigDecimal.valueOf(previous.maxExerciseRepetitions()), "次", "本次该动作累计次数");

        WeightRepImprovement bestWeightRep = current.repetitionsByWeight().entrySet().stream()
                .filter(entry -> previousRepetitionsByWeight.containsKey(entry.getKey()))
                .filter(entry -> entry.getValue() > previousRepetitionsByWeight.get(entry.getKey()))
                .map(entry -> new WeightRepImprovement(entry.getKey(), entry.getValue(),
                        previousRepetitionsByWeight.get(entry.getKey())))
                .max((left, right) -> Integer.compare(left.currentRepetitions() - left.previousRepetitions(),
                        right.currentRepetitions() - right.previousRepetitions()))
                .orElse(null);
        if (bestWeightRep != null) {
            achievements.add(new ExerciseAchievement("SAME_WEIGHT_REPS", "同重量最多次数",
                    BigDecimal.valueOf(bestWeightRep.currentRepetitions()),
                    BigDecimal.valueOf(bestWeightRep.previousRepetitions()), "次",
                    formatMetric(bestWeightRep.weightKg()) + "kg 下完成"));
        }
        return achievements;
    }

    private PersonalBestSnapshot personalBest(CurrentExerciseMetrics current, HistoricalExerciseMetricRow previous,
            Map<BigDecimal, Integer> previousRepetitionsByWeight) {
        Map<String, Integer> mergedRepetitions = new LinkedHashMap<>();
        previousRepetitionsByWeight.forEach((weight, repetitions) -> mergedRepetitions.put(formatMetric(weight), repetitions));
        current.repetitionsByWeight().forEach((weight, repetitions) ->
                mergedRepetitions.merge(formatMetric(weight), repetitions, Math::max));
        return new PersonalBestSnapshot(
                previous == null ? current.maxWeightKg() : current.maxWeightKg().max(previous.maxWeightKg()),
                previous == null ? current.estimatedOneRepMaxKg()
                        : current.estimatedOneRepMaxKg().max(previous.estimatedOneRepMaxKg()),
                previous == null ? current.maxSetVolumeKg() : current.maxSetVolumeKg().max(previous.maxSetVolumeKg()),
                previous == null ? current.totalVolumeKg() : current.totalVolumeKg().max(previous.maxExerciseVolumeKg()),
                previous == null ? current.totalRepetitions()
                        : Math.max(current.totalRepetitions(), previous.maxExerciseRepetitions()),
                mergedRepetitions);
    }

    private void addAchievement(List<ExerciseAchievement> achievements, String type, String label,
            BigDecimal current, BigDecimal previous, String unit, String detail) {
        BigDecimal safePrevious = previous == null ? BigDecimal.ZERO : previous;
        if (current != null && current.compareTo(safePrevious) > 0) {
            achievements.add(new ExerciseAchievement(type, label, roundMetric(current), roundMetric(safePrevious), unit, detail));
        }
    }

    private BigDecimal estimatedOneRepMax(BigDecimal weight, int repetitions) {
        return roundMetric(weight.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(repetitions)
                .divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP))));
    }

    private BigDecimal normalizeWeight(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).stripTrailingZeros();
    }

    private BigDecimal roundMetric(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    private String formatMetric(BigDecimal value) {
        return roundMetric(value).toPlainString();
    }

    private void validatePlanAccess(Long userId, Long planId) {
        if (planId == null) return;
        int count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM workout_plan
                        WHERE id = :planId AND is_active = TRUE
                          AND (owner_user_id IS NULL OR owner_user_id = :userId)
                          AND NOT EXISTS (
                              SELECT 1 FROM user_hidden_workout_plan hidden
                              WHERE hidden.user_id = :userId AND hidden.plan_id = workout_plan.id
                          )
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

    private record ExistingWorkout(Long id, BigDecimal totalVolumeKg) {
    }

    private record PreviousWorkout(BigDecimal totalVolumeKg, Integer durationMinutes, Integer completedSetCount) {
    }

    record HistoricalExerciseMetricRow(Long exerciseId, BigDecimal maxWeightKg,
            BigDecimal estimatedOneRepMaxKg, BigDecimal maxSetVolumeKg, BigDecimal maxExerciseVolumeKg,
            Integer maxExerciseRepetitions) {
    }

    record HistoricalWeightRepetitionRow(Long exerciseId, BigDecimal weightKg, Integer maxRepetitions) {
    }

    private record CurrentExerciseMetrics(BigDecimal totalVolumeKg, Integer totalRepetitions,
            BigDecimal maxWeightKg, BigDecimal maxSetVolumeKg, BigDecimal estimatedOneRepMaxKg,
            Map<BigDecimal, Integer> repetitionsByWeight) {
    }

    private record WeightRepImprovement(BigDecimal weightKg, Integer currentRepetitions,
            Integer previousRepetitions) {
    }

    public record ExercisePerformanceSummary(Long exerciseId, String exerciseName, Integer completedSetCount,
            Integer totalRepetitions, BigDecimal totalVolumeKg, BigDecimal maxWeightKg,
            BigDecimal estimatedOneRepMaxKg, Boolean firstRecorded, List<ExerciseAchievement> achievements,
            PersonalBestSnapshot personalBest) {
    }

    public record PersonalBestSnapshot(BigDecimal maxWeightKg, BigDecimal estimatedOneRepMaxKg,
            BigDecimal maxSetVolumeKg, BigDecimal maxExerciseVolumeKg, Integer maxExerciseRepetitions,
            Map<String, Integer> repetitionsByWeight) {
    }

    public record ExerciseAchievement(String type, String label, BigDecimal value, BigDecimal previousValue,
            String unit, String detail) {
    }

    public record WorkoutComparison(BigDecimal previousVolumeKg, Integer previousSetCount,
            Integer previousDurationMinutes, BigDecimal volumeDifferenceKg, BigDecimal volumeChangePercent,
            Integer setDifference, Integer durationDifferenceMinutes) {
    }

    public record WorkoutCompletionResponse(Long id, BigDecimal totalVolumeKg,
            List<ExercisePerformanceSummary> exerciseSummaries, WorkoutComparison comparison) {

        public WorkoutCompletionResponse(Long id, BigDecimal totalVolumeKg) {
            this(id, totalVolumeKg, List.of(), null);
        }
    }
}

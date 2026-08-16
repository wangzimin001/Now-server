package com.wangzimin.now.repository;

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

import com.wangzimin.now.domain.AchievementType;
import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.DecimalBusinessRule;
import com.wangzimin.now.domain.EffortRule;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.domain.ValidationRule;
import com.wangzimin.now.domain.WorkoutSetType;
import com.wangzimin.now.domain.WorkoutStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 负责训练模板、训练记录和个人纪录聚合的持久化操作。
 *
 * <p>所有跨表写入都在事务中执行，用户数据通过 owner_user_id 隔离。
 * 训练完成请求使用 clientRecordId 保证移动端离线重试幂等。
 * 状态、限制、公式参数和纪录类型全部引用领域枚举，服务中不保留业务魔法值。</p>
 */
@Repository
public class WorkoutRepository {

    private final JdbcClient jdbcClient;

    /**
     * 创建训练持久化仓储。
     *
     * @param jdbcClient 执行参数化 SQL 和事务写入的客户端
     */
    public WorkoutRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 以无用户归属方式创建训练模板。
     *
     * <p>该重载用于系统模板和既有测试，实际移动端入口会传入登录用户主键。</p>
     *
     * @param request 模板内容
     * @return 新模板主键
     */
    public Long createPlan(WorkoutPlanRequest request) {
        return createPlan(null, request);
    }

    /**
     * 为指定用户创建训练模板及有序动作。
     *
     * <p>先写模板主记录并取得数据库主键，再在同一事务中写入动作列表。
     * 任一动作写入失败都会回滚模板主记录。</p>
     *
     * @param userId 可空模板所有者
     * @param request 模板内容
     * @return 新模板主键
     */
    public Long createPlan(Long userId, WorkoutPlanRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO workout_plan
                            (owner_user_id, name, description, estimated_minutes, weekly_target, is_active)
                        VALUES (:userId, :name, :description, :estimatedMinutes, :weeklyTarget, TRUE)
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("name", request.name().trim())
                .param("description", cleanDescription(request.description()))
                .param("estimatedMinutes", request.estimatedMinutes())
                .param("weeklyTarget", BusinessRule.DEFAULT_WEEKLY_TARGET.value())
                .update(keyHolder, "id");

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(SystemText.PLAN_KEY_MISSING.value());
        }
        long planId = key.longValue();
        replacePlanExercises(planId, request.exercises());
        return planId;
    }

    /**
     * 更新无用户归属的训练模板。
     *
     * @param planId 模板主键
     * @param request 新模板内容
     */
    public void updatePlan(Long planId, WorkoutPlanRequest request) {
        updatePlan(null, planId, request);
    }

    /**
     * 更新当前用户拥有的有效训练模板。
     *
     * <p>主记录更新成功后完全替换动作列表，两个步骤共享事务，
     * 不会出现模板元数据和动作配置版本不一致。</p>
     *
     * @param userId 可空模板所有者
     * @param planId 模板主键
     * @param request 新模板内容
     */
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

    /**
     * 软删除无用户归属的训练模板。
     *
     * @param planId 模板主键
     */
    public void deletePlan(Long planId) {
        deletePlan(null, planId);
    }

    /**
     * 删除个人模板或为当前用户隐藏系统模板。
     *
     * <p>个人模板更新 is_active；系统模板保持全局有效，只写入用户隐藏关联，
     * 因此不会影响其他账号。</p>
     *
     * @param userId 可空用户主键
     * @param planId 目标模板主键
     */
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
        if (changed > BusinessRule.ZERO_COUNT.value()) {
            return;
        }

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

    /**
     * 保存一条无用户归属的完成训练。
     *
     * @param request 完整训练快照
     * @return 会话主键、容量和纪录结果
     */
    public WorkoutCompletionResponse completeWorkout(WorkoutCompletionRequest request) {
        return completeWorkout(null, request);
    }

    /**
     * 保存一次完成训练并计算历史对比与个人纪录。
     *
     * <p>方法先检查时间和幂等键，再验证模板访问权限；历史基线在写入本次训练前读取，
     * 避免本次结果参与自身对比。会话、动作和组记录在同一事务中持久化。</p>
     *
     * @param userId 可空训练所有者
     * @param request 完整训练快照
     * @return 新会话或幂等命中会话的结果
     */
    public WorkoutCompletionResponse completeWorkout(Long userId, WorkoutCompletionRequest request) {
        if (request.endedAt().isBefore(request.startedAt())) {
            throw ApiErrorCode.WORKOUT_END_BEFORE_START.exception();
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
            if (existing != null) {
                return new WorkoutCompletionResponse(existing.id(), existing.totalVolumeKg(), List.of(), null);
            }
        }

        validatePlanAccess(userId, request.planId());

        List<ExercisePerformanceSummary> exerciseSummaries = evaluateExercisePerformance(userId, request.exercises());

        BigDecimal totalVolume = request.exercises().stream()
                .flatMap(exercise -> exercise.sets().stream())
                .filter(set -> Boolean.TRUE.equals(set.completed()) && set.setType().contributesToVolume())
                .map(set -> set.weightKg().multiply(BigDecimal.valueOf(set.repetitions())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        WorkoutComparison comparison = compareWithPreviousWorkout(userId, request, totalVolume);

        KeyHolder sessionKeyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO workout_session
                            (owner_user_id, client_record_id, plan_id, name_snapshot, started_at, ended_at, duration_minutes, total_volume_kg, status)
                        VALUES
                            (:userId, :clientRecordId, :planId, :name, :startedAt, :endedAt, :durationMinutes, :totalVolume, :status)
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("clientRecordId", cleanClientRecordId(request.clientRecordId()), Types.VARCHAR)
                .param("planId", request.planId(), Types.BIGINT)
                .param("name", request.name().trim())
                .param("startedAt", Timestamp.from(request.startedAt()))
                .param("endedAt", Timestamp.from(request.endedAt()))
                .param("durationMinutes", request.durationMinutes())
                .param("totalVolume", totalVolume)
                .param("status", WorkoutStatus.COMPLETED.databaseValue())
                .update(sessionKeyHolder, "id");

        Number sessionKey = sessionKeyHolder.getKey();
        if (sessionKey == null) {
            throw new IllegalStateException(SystemText.WORKOUT_KEY_MISSING.value());
        }
        long sessionId = sessionKey.longValue();

        for (int exerciseIndex = 0; exerciseIndex < request.exercises().size(); exerciseIndex++) {
            WorkoutExerciseRequest exercise = request.exercises().get(exerciseIndex);
            long sessionExerciseId = insertSessionExercise(
                    sessionId, exercise, exerciseIndex + BusinessRule.ORDER_INDEX_OFFSET.value());
            insertSetRecords(sessionExerciseId, exercise.sets(), request.endedAt());
        }

        return new WorkoutCompletionResponse(sessionId, totalVolume, exerciseSummaries, comparison);
    }

    /**
     * 纠正一条已完成训练的全部组数据并重算容量。
     *
     * <p>方法验证会话归属、动作数量和动作主键集合完全一致，然后删除旧组并重建。
     * 至少保留一组记录；任何校验或写入失败都会回滚原数据。</p>
     *
     * @param userId 当前账号主键
     * @param sessionId 训练会话主键
     * @param request 完整动作和组快照
     * @return 重算后的总容量
     */
    public BigDecimal updateWorkoutHistory(Long userId, Long sessionId, WorkoutHistoryUpdateRequest request) {
        int exerciseCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM session_exercise se
                        JOIN workout_session ws ON ws.id = se.session_id
                        WHERE se.session_id = :sessionId AND ws.status = :completedStatus
                          AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId, Types.BIGINT)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .query(Integer.class)
                .single();
        long distinctExerciseCount = request.exercises().stream()
                .map(WorkoutHistoryExerciseUpdate::sessionExerciseId)
                .distinct()
                .count();
        if (exerciseCount == BusinessRule.ZERO_COUNT.value()) {
            throw ApiErrorCode.WORKOUT_NOT_FOUND.exception();
        }
        if (distinctExerciseCount != exerciseCount || request.exercises().size() != exerciseCount) {
            throw ApiErrorCode.WORKOUT_EXERCISES_MISMATCH.exception();
        }
        int matchingExercises = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM session_exercise
                        WHERE session_id = :sessionId AND id IN (:exerciseIds)
                        """)
                .param("sessionId", sessionId)
                .param("exerciseIds", request.exercises().stream()
                        .map(WorkoutHistoryExerciseUpdate::sessionExerciseId).toList())
                .query(Integer.class)
                .single();
        if (matchingExercises != exerciseCount) {
            throw ApiErrorCode.WORKOUT_EXERCISES_MISMATCH.exception();
        }

        int totalSetCount = request.exercises().stream().mapToInt(item -> item.sets().size()).sum();
        if (totalSetCount == BusinessRule.ZERO_COUNT.value()) {
            throw ApiErrorCode.WORKOUT_SET_REQUIRED.exception();
        }

        jdbcClient.sql("""
                        DELETE sr FROM set_record sr
                        JOIN session_exercise se ON se.id = sr.session_exercise_id
                        WHERE se.session_id = :sessionId
                        """)
                .param("sessionId", sessionId)
                .update();

        BigDecimal totalVolume = BigDecimal.ZERO;
        for (WorkoutHistoryExerciseUpdate exercise : request.exercises()) {
            for (int index = 0; index < exercise.sets().size(); index++) {
                WorkoutSetRequest set = exercise.sets().get(index);
                boolean completed = Boolean.TRUE.equals(set.completed());
                if (completed && set.setType().contributesToVolume()) {
                    totalVolume = totalVolume.add(set.weightKg().multiply(BigDecimal.valueOf(set.repetitions())));
                }
                jdbcClient.sql("""
                                INSERT INTO set_record
                                    (session_exercise_id, set_number, set_type, weight_kg, repetitions, rpe,
                                     rest_duration_seconds, status, completed_at)
                                SELECT :sessionExerciseId, :setNumber, :setType, :weightKg, :repetitions,
                                       :rpe, :restDurationSeconds, :status,
                                       CASE WHEN :status = :completedStatus THEN ws.ended_at ELSE NULL END
                                FROM workout_session ws
                                WHERE ws.id = :sessionId
                                """)
                        .param("sessionExerciseId", exercise.sessionExerciseId())
                        .param("setNumber", index + BusinessRule.ORDER_INDEX_OFFSET.value())
                        .param("setType", set.setType().databaseValue())
                        .param("weightKg", set.weightKg())
                        .param("repetitions", set.repetitions())
                        .param("rpe", EffortRule.toRpe(completed && set.setType().contributesToPerformance()
                                ? set.repsInReserve() : null), Types.DECIMAL)
                        .param("restDurationSeconds", set.restDurationSeconds(), Types.INTEGER)
                        .param("status", completed
                                ? WorkoutStatus.COMPLETED.databaseValue()
                                : WorkoutStatus.SKIPPED.databaseValue())
                        .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                        .param("sessionId", sessionId)
                        .update();
            }
        }

        jdbcClient.sql("""
                        UPDATE workout_session
                        SET total_volume_kg = :totalVolume
                        WHERE id = :sessionId
                        """)
                .param("totalVolume", totalVolume)
                .param("sessionId", sessionId)
                .update();
        return totalVolume;
    }

    /**
     * 将当前用户的一条已完成训练软删除。
     *
     * <p>状态更新同时匹配用户归属和完成状态；删除后的记录自动从历史、趋势和 PR 基线排除。</p>
     *
     * @param userId 当前账号主键
     * @param sessionId 训练会话主键
     */
    public void deleteWorkoutHistory(Long userId, Long sessionId) {
        int changed = jdbcClient.sql("""
                        UPDATE workout_session
                        SET status = :deletedStatus
                        WHERE id = :sessionId AND status = :completedStatus
                          AND ((:userId IS NULL AND owner_user_id IS NULL) OR owner_user_id = :userId)
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId, Types.BIGINT)
                .param("deletedStatus", WorkoutStatus.DELETED.databaseValue())
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .update();
        if (changed == BusinessRule.ZERO_COUNT.value()) {
            throw ApiErrorCode.WORKOUT_NOT_FOUND.exception();
        }
    }

    /**
     * 将本次训练与同模板或同名自由训练的上一次记录比较。
     *
     * <p>模板训练按 plan_id 对比；自由训练按空 plan_id 和名称快照对比。
     * 首次训练没有历史基线时返回空，避免误报提升。</p>
     *
     * @param userId 当前账号主键
     * @param request 本次训练请求
     * @param totalVolume 本次总容量
     * @return 上次训练对比；无基线时为空
     */
    private WorkoutComparison compareWithPreviousWorkout(Long userId, WorkoutCompletionRequest request,
            BigDecimal totalVolume) {
        PreviousWorkout previous = jdbcClient.sql("""
                        SELECT ws.total_volume_kg AS totalVolumeKg,
                               ws.duration_minutes AS durationMinutes,
                               SUM(CASE WHEN sr.status = :completedStatus AND sr.set_type <> :warmUpSetType
                                   THEN 1 ELSE 0 END) AS completedSetCount
                        FROM workout_session ws
                        LEFT JOIN session_exercise se ON se.session_id = ws.id
                        LEFT JOIN set_record sr ON sr.session_exercise_id = se.id
                        WHERE ws.status = :completedStatus
                          AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                          AND ((:planId IS NOT NULL AND ws.plan_id = :planId)
                               OR (:planId IS NULL AND ws.plan_id IS NULL AND ws.name_snapshot = :name))
                        GROUP BY ws.id, ws.total_volume_kg, ws.duration_minutes, ws.ended_at
                        ORDER BY ws.ended_at DESC
                        LIMIT :resultLimit
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("planId", request.planId(), Types.BIGINT)
                .param("name", request.name().trim())
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .param("warmUpSetType", WorkoutSetType.WARM_UP.databaseValue())
                .param("resultLimit", BusinessRule.COLLECTION_MIN_SIZE.value())
                .query(PreviousWorkout.class)
                .optional()
                .orElse(null);
        if (previous == null) {
            return null;
        }
        int completedSetCount = request.exercises().stream()
                .mapToInt(exercise -> (int) exercise.sets().stream()
                        .filter(set -> Boolean.TRUE.equals(set.completed()) && set.setType().contributesToVolume())
                        .count())
                .sum();
        BigDecimal volumeDifference = totalVolume.subtract(previous.totalVolumeKg());
        BigDecimal volumePercent = previous.totalVolumeKg().compareTo(BigDecimal.ZERO)
                > BusinessRule.ZERO_COUNT.value()
                ? volumeDifference.multiply(BigDecimal.valueOf(BusinessRule.PERCENT_MULTIPLIER.value()))
                        .divide(previous.totalVolumeKg(), BusinessRule.METRIC_RESULT_SCALE.value(),
                                RoundingMode.HALF_UP)
                : null;
        return new WorkoutComparison(roundMetric(previous.totalVolumeKg()), previous.completedSetCount(),
                previous.durationMinutes(), roundMetric(volumeDifference), volumePercent,
                completedSetCount - previous.completedSetCount(), request.durationMinutes() - previous.durationMinutes());
    }

    /**
     * 计算请求中每个动作相对历史基线的表现摘要。
     *
     * <p>历史聚合按动作批量加载，避免逐动作查询；未完成组不参与容量、次数和纪录统计。
     * 首次出现的动作只建立基线，不生成突破提示。</p>
     *
     * @param userId 当前账号主键
     * @param exercises 本次训练动作
     * @return 与输入顺序一致的动作表现摘要
     */
    public List<ExercisePerformanceSummary> evaluateExercisePerformance(Long userId, List<WorkoutExerciseRequest> exercises) {
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
                    .filter(set -> Boolean.TRUE.equals(set.completed()) && set.setType().contributesToVolume())
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

    /**
     * 批量加载多个动作的历史最大指标。
     *
     * <p>只读取当前账号已完成训练和已完成组，并分别计算会话总量与跨会话最大值。</p>
     *
     * @param userId 当前账号主键
     * @param exerciseIds 去重动作主键
     * @return 每个存在历史的动作最大指标
     */
    private List<HistoricalExerciseMetricRow> loadHistoricalExerciseMetrics(Long userId, List<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        WITH exercise_session_metrics AS (
                            SELECT se.exercise_id AS exerciseId, se.session_id,
                                   SUM(sr.weight_kg * sr.repetitions) AS totalVolumeKg,
                                   SUM(sr.repetitions) AS totalRepetitions
                            FROM session_exercise se
                            JOIN workout_session ws ON ws.id = se.session_id
                            JOIN set_record sr ON sr.session_exercise_id = se.id
                              AND sr.status = :completedStatus
                              AND sr.set_type <> :warmUpSetType
                            WHERE ws.status = :completedStatus
                              AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                              AND se.exercise_id IN (:exerciseIds)
                            GROUP BY se.exercise_id, se.session_id
                        )
                        SELECT se.exercise_id AS exerciseId,
                               MAX(sr.weight_kg) AS maxWeightKg,
                               MAX(FLOOR((sr.weight_kg * (1 + sr.repetitions / :oneRepMaxDivisor))
                                   / :oneRepMaxStep) * :oneRepMaxStep) AS estimatedOneRepMaxKg,
                               MAX(sr.weight_kg * sr.repetitions) AS maxSetVolumeKg,
                               MAX(metrics.totalVolumeKg) AS maxExerciseVolumeKg,
                               MAX(metrics.totalRepetitions) AS maxExerciseRepetitions
                        FROM session_exercise se
                        JOIN workout_session ws ON ws.id = se.session_id
                         JOIN set_record sr ON sr.session_exercise_id = se.id
                           AND sr.status = :completedStatus
                           AND sr.set_type = :standardSetType
                        JOIN exercise_session_metrics metrics
                          ON metrics.exerciseId = se.exercise_id AND metrics.session_id = se.session_id
                        WHERE ws.status = :completedStatus
                          AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                          AND se.exercise_id IN (:exerciseIds)
                        GROUP BY se.exercise_id
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("exerciseIds", exerciseIds)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .param("warmUpSetType", WorkoutSetType.WARM_UP.databaseValue())
                .param("standardSetType", WorkoutSetType.STANDARD.databaseValue())
                .param("oneRepMaxDivisor", BigDecimal.valueOf(BusinessRule.ONE_REP_MAX_DIVISOR.value()))
                .param("oneRepMaxStep", DecimalBusinessRule.WEIGHT_STEP_KG.value())
                .query(HistoricalExerciseMetricRow.class)
                .list();
    }

    /**
     * 批量加载每个动作在相同重量下的历史最大次数。
     *
     * <p>结果用于识别“同重量更多次数”纪录，与最大重量和估算 1RM 分开计算。</p>
     *
     * @param userId 当前账号主键
     * @param exerciseIds 去重动作主键
     * @return 动作、重量和历史最大次数行
     */
    private List<HistoricalWeightRepetitionRow> loadHistoricalWeightRepetitions(Long userId, List<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT se.exercise_id AS exerciseId, sr.weight_kg AS weightKg,
                               MAX(sr.repetitions) AS maxRepetitions
                        FROM session_exercise se
                        JOIN workout_session ws ON ws.id = se.session_id
                        JOIN set_record sr ON sr.session_exercise_id = se.id
                         WHERE ws.status = :completedStatus AND sr.status = :completedStatus
                           AND sr.set_type = :standardSetType
                           AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                          AND se.exercise_id IN (:exerciseIds)
                        GROUP BY se.exercise_id, sr.weight_kg
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("exerciseIds", exerciseIds)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .param("standardSetType", WorkoutSetType.STANDARD.databaseValue())
                .query(HistoricalWeightRepetitionRow.class)
                .list();
    }

    /**
     * 聚合一个动作在本次训练中的完成组指标。
     *
     * <p>同时计算总容量、总次数、最大重量、单组最大容量、最大估算 1RM，
     * 并保存每个重量对应的本次最高次数。</p>
     *
     * @param sets 一个动作的完成组
     * @return 本次动作指标
     */
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
            totalVolume = totalVolume.add(setVolume);
            totalRepetitions += set.repetitions();
            // 递减组贡献真实训练容量，但最大重量、估算 1RM 和同重量次数只由主工作组建立。
            if (!set.setType().contributesToPerformance()) {
                continue;
            }
            BigDecimal estimatedOneRepMax = estimatedOneRepMax(weight, set.repetitions());
            maxWeight = maxWeight.max(weight);
            maxSetVolume = maxSetVolume.max(setVolume);
            maxEstimatedOneRepMax = maxEstimatedOneRepMax.max(estimatedOneRepMax);
            repetitionsByWeight.merge(weight, set.repetitions(), Math::max);
        }
        return new CurrentExerciseMetrics(totalVolume, totalRepetitions, maxWeight, maxSetVolume,
                maxEstimatedOneRepMax, repetitionsByWeight);
    }

    /**
     * 根据历史最大值生成本次新纪录列表。
     *
     * <p>纪录编码、中文标签、单位和默认说明由 AchievementType 枚举提供，
     * 本方法只负责数值比较和同重量次数的特殊详情。</p>
     *
     * @param exercise 当前动作请求
     * @param current 本次聚合指标
     * @param previous 历史最大指标
     * @param previousRepetitionsByWeight 历史同重量最大次数
     * @return 本次真正超过历史的纪录
     */
    private List<ExerciseAchievement> buildAchievements(WorkoutExerciseRequest exercise, CurrentExerciseMetrics current,
            HistoricalExerciseMetricRow previous, Map<BigDecimal, Integer> previousRepetitionsByWeight) {
        List<ExerciseAchievement> achievements = new ArrayList<>();
        addAchievement(achievements, AchievementType.MAX_WEIGHT,
                current.maxWeightKg(), previous.maxWeightKg(), null);
        addAchievement(achievements, AchievementType.ESTIMATED_ONE_REP_MAX,
                current.estimatedOneRepMaxKg(), previous.estimatedOneRepMaxKg(), null);
        addAchievement(achievements, AchievementType.MAX_SET_VOLUME,
                current.maxSetVolumeKg(), previous.maxSetVolumeKg(), null);
        addAchievement(achievements, AchievementType.EXERCISE_VOLUME,
                current.totalVolumeKg(), previous.maxExerciseVolumeKg(), null);
        addAchievement(achievements, AchievementType.EXERCISE_REPETITIONS,
                BigDecimal.valueOf(current.totalRepetitions()),
                BigDecimal.valueOf(previous.maxExerciseRepetitions()), null);

        WeightRepImprovement bestWeightRep = current.repetitionsByWeight().entrySet().stream()
                .filter(entry -> previousRepetitionsByWeight.containsKey(entry.getKey()))
                .filter(entry -> entry.getValue() > previousRepetitionsByWeight.get(entry.getKey()))
                .map(entry -> new WeightRepImprovement(entry.getKey(), entry.getValue(),
                        previousRepetitionsByWeight.get(entry.getKey())))
                .max((left, right) -> Integer.compare(left.currentRepetitions() - left.previousRepetitions(),
                        right.currentRepetitions() - right.previousRepetitions()))
                .orElse(null);
        if (bestWeightRep != null) {
            AchievementType type = AchievementType.SAME_WEIGHT_REPETITIONS;
            achievements.add(new ExerciseAchievement(type.code(), type.label(),
                    BigDecimal.valueOf(bestWeightRep.currentRepetitions()),
                    BigDecimal.valueOf(bestWeightRep.previousRepetitions()), type.unit(),
                    formatMetric(bestWeightRep.weightKg()) + SystemText.SAME_WEIGHT_DETAIL_SUFFIX.value()));
        }
        return achievements;
    }

    /**
     * 合并历史和本次数据，生成截至当前的个人最佳快照。
     *
     * <p>每个指标取历史与本次最大值，同重量次数映射逐键合并。</p>
     *
     * @param current 本次动作指标
     * @param previous 可空历史最大指标
     * @param previousRepetitionsByWeight 历史同重量次数
     * @return 包含所有最佳值的快照
     */
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

    /**
     * 在当前值严格超过历史值时追加一个纪录。
     *
     * <p>空历史值按零处理，输出数值统一舍入；展示元数据来自纪录枚举。</p>
     *
     * @param achievements 待追加的纪录集合
     * @param type 纪录类型
     * @param current 当前值
     * @param previous 历史值
     * @param detailOverride 可空详情覆盖
     */
    private void addAchievement(List<ExerciseAchievement> achievements, AchievementType type,
            BigDecimal current, BigDecimal previous, String detailOverride) {
        BigDecimal safePrevious = previous == null ? BigDecimal.ZERO : previous;
        if (current != null && current.compareTo(safePrevious) > 0) {
            String detail = detailOverride == null ? type.detail() : detailOverride;
            achievements.add(new ExerciseAchievement(type.code(), type.label(), roundMetric(current),
                    roundMetric(safePrevious), type.unit(), detail));
        }
    }

    /**
     * 使用 Epley 公式估算单次最大重量。
     *
     * <p>公式除数、计算精度和输出步进均来自业务规则枚举。结果统一向下取到
     * 0.25 kg 的倍数，避免估算值高于保守可展示值，并与历史查询口径保持一致。</p>
     *
     * @param weight 当前组重量
     * @param repetitions 当前组次数
     * @return 向下取到业务步进后的估算 1RM
     */
    private BigDecimal estimatedOneRepMax(BigDecimal weight, int repetitions) {
        BigDecimal rawValue = weight.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(repetitions)
                .divide(BigDecimal.valueOf(BusinessRule.ONE_REP_MAX_DIVISOR.value()),
                        BusinessRule.METRIC_CALCULATION_SCALE.value(), RoundingMode.HALF_UP)));
        return floorToStep(rawValue, DecimalBusinessRule.WEIGHT_STEP_KG.value());
    }

    /**
     * 将十进制指标保守地向下取到指定步进。
     *
     * @param value 原始指标
     * @param step 大于零的业务步进
     * @return 不超过原值且落在指定步进上的指标
     */
    private BigDecimal floorToStep(BigDecimal value, BigDecimal step) {
        return value.divide(step, BusinessRule.ZERO_COUNT.value(), RoundingMode.FLOOR)
                .multiply(step)
                .stripTrailingZeros();
    }

    /**
     * 将可空重量转换为适合 Map 键比较的规范值。
     *
     * @param value 可空重量
     * @return 非空且去除多余小数零的重量
     */
    private BigDecimal normalizeWeight(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).stripTrailingZeros();
    }

    /**
     * 按统一精度和舍入方式格式化指标数值。
     *
     * @param value 原始指标
     * @return 舍入并去除多余小数零的数值
     */
    private BigDecimal roundMetric(BigDecimal value) {
        return value.setScale(BusinessRule.METRIC_RESULT_SCALE.value(), RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /**
     * 将规范指标转换为不使用科学计数法的文本。
     *
     * @param value 原始指标
     * @return 供重量映射键和详情文案使用的文本
     */
    private String formatMetric(BigDecimal value) {
        return roundMetric(value).toPlainString();
    }

    /**
     * 验证训练请求引用的模板对当前用户可见。
     *
     * <p>自由训练允许空模板；非空模板必须为系统模板或当前用户模板，且不能被当前用户隐藏。</p>
     *
     * @param userId 可空用户主键
     * @param planId 可空模板主键
     */
    private void validatePlanAccess(Long userId, Long planId) {
        if (planId == null) {
            return;
        }
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
        if (count == BusinessRule.ZERO_COUNT.value()) {
            throw ApiErrorCode.PLAN_NOT_FOUND.exception();
        }
    }

    /**
     * 在同一事务中完全替换模板的动作配置。
     *
     * <p>列表位置生成连续顺序，动作主键和训练参数由请求校验保证范围有效。</p>
     *
     * @param planId 模板主键
     * @param exercises 新动作配置
     */
    private void replacePlanExercises(Long planId, List<PlanExerciseRequest> exercises) {
        jdbcClient.sql("DELETE FROM plan_exercise WHERE plan_id = :planId")
                .param("planId", planId)
                .update();

        for (int index = 0; index < exercises.size(); index++) {
            PlanExerciseRequest exercise = exercises.get(index);
            jdbcClient.sql("""
                            INSERT INTO plan_exercise
                                (plan_id, exercise_id, replacement_exercise_id, exercise_order, target_sets,
                                 target_reps, rest_seconds, progressive_overload_enabled,
                                 replacement_progressive_overload_enabled)
                            VALUES
                                (:planId, :exerciseId, :replacementExerciseId, :exerciseOrder, :targetSets,
                                 :targetReps, :restSeconds, :progressiveOverloadEnabled,
                                 :replacementProgressiveOverloadEnabled)
                            """)
                    .param("planId", planId)
                    .param("exerciseId", exercise.exerciseId())
                    .param("replacementExerciseId", exercise.replacementExerciseId(), Types.BIGINT)
                    .param("exerciseOrder", index + BusinessRule.ORDER_INDEX_OFFSET.value())
                    .param("targetSets", exercise.targetSets())
                    .param("targetReps", exercise.targetReps())
                    .param("restSeconds", exercise.restSeconds())
                    // 旧客户端未传该字段时按关闭写入，不能替用户自动开启渐进推荐。
                    .param("progressiveOverloadEnabled", Boolean.TRUE.equals(exercise.progressiveOverloadEnabled()))
                    // 没有替换动作时强制关闭候选开关，避免产生无法归属的渐进配置。
                    .param("replacementProgressiveOverloadEnabled", exercise.replacementExerciseId() != null
                            && Boolean.TRUE.equals(exercise.replacementProgressiveOverloadEnabled()))
                    .update();
        }
    }

    /**
     * 写入一次训练中的动作快照并返回关联主键。
     *
     * <p>保存动作库主键用于后续表现查询，同时保存名称快照保证历史可读性。</p>
     *
     * @param sessionId 训练会话主键
     * @param exercise 动作请求
     * @param order 动作顺序
     * @return 会话动作主键
     */
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
            throw new IllegalStateException(SystemText.SESSION_EXERCISE_KEY_MISSING.value());
        }
        return key.longValue();
    }

    /**
     * 批量写入一个训练动作的全部组记录。
     *
     * <p>完成组记录训练结束时间，跳过组保留重量和次数快照但完成时间为空。
     * 实际组间歇可空，不能用零伪装未记录数据。</p>
     *
     * @param sessionExerciseId 会话动作主键
     * @param sets 有序组请求
     * @param endedAt 训练结束时间
     */
    private void insertSetRecords(Long sessionExerciseId, List<WorkoutSetRequest> sets, Instant endedAt) {
        for (int index = 0; index < sets.size(); index++) {
            WorkoutSetRequest set = sets.get(index);
            boolean completed = Boolean.TRUE.equals(set.completed());
            // 每组独立保存实际组间歇；未确认或未完成的组允许为空，不能伪造成零秒。
            // 该字段与重量、次数处于同一事务中，训练保存失败时会整体回滚。
            jdbcClient.sql("""
                            INSERT INTO set_record
                                (session_exercise_id, set_number, set_type, weight_kg, repetitions, rpe,
                                 rest_duration_seconds, status, completed_at)
                            VALUES
                                (:sessionExerciseId, :setNumber, :setType, :weightKg, :repetitions, :rpe,
                                 :restDurationSeconds, :status,
                                 CASE WHEN :status = :completedStatus THEN :endedAt ELSE NULL END)
                            """)
                    .param("sessionExerciseId", sessionExerciseId)
                    .param("setNumber", index + BusinessRule.ORDER_INDEX_OFFSET.value())
                    .param("setType", set.setType().databaseValue())
                    .param("weightKg", set.weightKg())
                    .param("repetitions", set.repetitions())
                    .param("rpe", EffortRule.toRpe(completed && set.setType().contributesToPerformance()
                            ? set.repsInReserve() : null), Types.DECIMAL)
                    .param("restDurationSeconds", set.restDurationSeconds(), Types.INTEGER)
                    .param("status", completed
                            ? WorkoutStatus.COMPLETED.databaseValue()
                            : WorkoutStatus.SKIPPED.databaseValue())
                    .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                    .param("endedAt", Timestamp.from(endedAt))
                    .update();
        }
    }

    /**
     * 清理可空模板说明。
     *
     * @param description 原始说明
     * @return 去除首尾空白后的非空字符串
     */
    private String cleanDescription(String description) {
        return description == null ? SystemText.EMPTY.value() : description.trim();
    }

    /**
     * 规范化移动端幂等记录键。
     *
     * @param value 原始记录键
     * @return 去除空白后的值；空输入返回空值
     */
    private String cleanClientRecordId(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 确保模板更新、删除或隐藏操作命中一条可见记录。
     *
     * @param changed 数据库受影响行数
     */
    private void requirePlan(int changed) {
        if (changed == BusinessRule.ZERO_COUNT.value()) {
            throw ApiErrorCode.PLAN_NOT_FOUND.exception();
        }
    }

    /**
     * 描述创建或更新训练模板的请求。
     *
     * @param name 模板名称
     * @param description 可空说明
     * @param estimatedMinutes 预计分钟数
     * @param exercises 有序动作配置
     */
    public record WorkoutPlanRequest(
            @NotBlank @Size(max = ValidationRule.PLAN_NAME_MAX_LENGTH) String name,
            @Size(max = ValidationRule.PLAN_DESCRIPTION_MAX_LENGTH) String description,
            @NotNull @Min(ValidationRule.PLAN_DURATION_MIN_MINUTES)
            @Max(ValidationRule.PLAN_DURATION_MAX_MINUTES) Integer estimatedMinutes,
            @NotNull @Size(min = ValidationRule.PLAN_EXERCISE_MIN_COUNT,
                    max = ValidationRule.PLAN_EXERCISE_MAX_COUNT) List<@Valid PlanExerciseRequest> exercises) {
    }

    /**
     * 描述模板中的一个动作目标。
     *
     * @param exerciseId 动作库主键
     * @param replacementExerciseId 可空的替换动作主键
     * @param targetSets 目标组数
     * @param targetReps 目标次数
     * @param restSeconds 预设休息秒数
     * @param progressiveOverloadEnabled 是否为该动作开启渐进超负荷
     * @param replacementProgressiveOverloadEnabled 是否为替换动作开启渐进超负荷
     */
    public record PlanExerciseRequest(
            @NotNull @Positive Long exerciseId,
            @Positive Long replacementExerciseId,
            @NotNull @Min(ValidationRule.TARGET_SET_MIN_COUNT)
            @Max(ValidationRule.TARGET_SET_MAX_COUNT) Integer targetSets,
            @NotNull @Min(ValidationRule.TARGET_REPETITION_MIN_COUNT)
            @Max(ValidationRule.REPETITION_MAX_COUNT) Integer targetReps,
            @NotNull @Min(ValidationRule.REST_MIN_SECONDS)
            @Max(ValidationRule.REST_MAX_SECONDS) Integer restSeconds,
            Boolean progressiveOverloadEnabled,
            Boolean replacementProgressiveOverloadEnabled) {

        /**
         * 构造包含替换动作但未提供替换动作渐进开关的兼容请求。
         *
         * @param exerciseId 动作库主键
         * @param replacementExerciseId 可空的替换动作主键
         * @param targetSets 目标组数
         * @param targetReps 目标次数
         * @param restSeconds 预设休息秒数
         * @param progressiveOverloadEnabled 主动作是否开启渐进超负荷
         */
        public PlanExerciseRequest(Long exerciseId, Long replacementExerciseId, Integer targetSets,
                Integer targetReps, Integer restSeconds, Boolean progressiveOverloadEnabled) {
            this(exerciseId, replacementExerciseId, targetSets, targetReps, restSeconds,
                    progressiveOverloadEnabled, false);
        }

        /**
         * 构造未配置替换动作的渐进超负荷请求。
         *
         * @param exerciseId 动作库主键
         * @param targetSets 目标组数
         * @param targetReps 目标次数
         * @param restSeconds 预设休息秒数
         * @param progressiveOverloadEnabled 是否开启渐进超负荷
         */
        public PlanExerciseRequest(Long exerciseId, Integer targetSets, Integer targetReps,
                Integer restSeconds, Boolean progressiveOverloadEnabled) {
            this(exerciseId, null, targetSets, targetReps, restSeconds, progressiveOverloadEnabled, false);
        }

        /**
         * 构造旧客户端兼容请求；未明确选择时关闭渐进超负荷。
         *
         * @param exerciseId 动作库主键
         * @param targetSets 目标组数
         * @param targetReps 目标次数
         * @param restSeconds 预设休息秒数
         */
        public PlanExerciseRequest(Long exerciseId, Integer targetSets, Integer targetReps, Integer restSeconds) {
            this(exerciseId, null, targetSets, targetReps, restSeconds, false, false);
        }
    }

    /**
     * 描述一次完整训练保存请求。
     *
     * <p>clientRecordId 由移动端生成，用于离线重试幂等；自由训练允许 planId 为空。</p>
     */
    public record WorkoutCompletionRequest(
            @Positive Long planId,
            @NotBlank @Size(max = ValidationRule.PLAN_NAME_MAX_LENGTH) String name,
            @NotNull Instant startedAt,
            @NotNull Instant endedAt,
            @NotNull @Min(ValidationRule.WORKOUT_DURATION_MIN_MINUTES)
            @Max(ValidationRule.WORKOUT_DURATION_MAX_MINUTES) Integer durationMinutes,
            @NotNull @Size(min = ValidationRule.WORKOUT_EXERCISE_MIN_COUNT,
                    max = ValidationRule.WORKOUT_EXERCISE_MAX_COUNT) List<@Valid WorkoutExerciseRequest> exercises,
            @Size(max = ValidationRule.CLIENT_RECORD_ID_MAX_LENGTH) String clientRecordId) {

        /**
         * 构造没有幂等键的兼容训练请求。
         *
         * @param planId 可空模板主键
         * @param name 训练名称
         * @param startedAt 开始时间
         * @param endedAt 结束时间
         * @param durationMinutes 持续分钟数
         * @param exercises 有序动作请求
         */
        public WorkoutCompletionRequest(Long planId, String name, Instant startedAt, Instant endedAt,
                Integer durationMinutes, List<WorkoutExerciseRequest> exercises) {
            this(planId, name, startedAt, endedAt, durationMinutes, exercises, null);
        }
    }

    /**
     * 描述一次训练中的动作快照。
     *
     * @param exerciseId 动作库主键
     * @param name 动作名称快照
     * @param sets 有序训练组
     */
    public record WorkoutExerciseRequest(
            @NotNull @Positive Long exerciseId,
            @NotBlank @Size(max = ValidationRule.PLAN_NAME_MAX_LENGTH) String name,
            @NotNull @Size(min = ValidationRule.WORKOUT_SET_MIN_COUNT,
                    max = ValidationRule.WORKOUT_SET_MAX_COUNT) List<@Valid WorkoutSetRequest> sets) {
    }

    /**
     * 描述一次训练组的实际结果。
     *
     * @param number 客户端显示序号
     * @param weightKg 重量
     * @param repetitions 次数
     * @param restDurationSeconds 实际组间歇
     * @param repsInReserve 完成后估计还能标准完成的次数；3 表示 3 次或更多
     * @param setType 普通组、热身组或递减组
     * @param completed 是否完成
     */
    public record WorkoutSetRequest(
            // number 由客户端表达顺序，服务端落库时仍按列表位置生成连续组号。
            @NotNull @Positive Integer number,
            @NotNull @DecimalMin(ValidationRule.MIN_WEIGHT_KG) BigDecimal weightKg,
            @NotNull @Min(ValidationRule.REPETITION_MIN_COUNT)
            @Max(ValidationRule.REPETITION_MAX_COUNT) Integer repetitions,
            // 正向计时可超过模板预设，因此上限按最长训练会话的一天设置。
            @Min(ValidationRule.REST_MIN_SECONDS)
            @Max(ValidationRule.ACTUAL_REST_MAX_SECONDS) Integer restDurationSeconds,
            @Min(ValidationRule.REPS_IN_RESERVE_MIN_COUNT)
            @Max(ValidationRule.REPS_IN_RESERVE_MAX_COUNT) Integer repsInReserve,
            @NotNull WorkoutSetType setType,
            @NotNull Boolean completed) {

        /**
         * 将旧客户端省略的组类型兼容为普通组。
         */
        public WorkoutSetRequest {
            setType = WorkoutSetType.normalize(setType);
        }

        /**
         * 兼容已采集 RIR 但尚未提交组类型的内部调用。
         *
         * @param number 客户端显示序号
         * @param weightKg 重量
         * @param repetitions 次数
         * @param restDurationSeconds 实际组间歇
         * @param repsInReserve 完成后估计还能标准完成的次数
         * @param completed 是否完成
         */
        public WorkoutSetRequest(Integer number, BigDecimal weightKg, Integer repetitions,
                Integer restDurationSeconds, Integer repsInReserve, Boolean completed) {
            this(number, weightKg, repetitions, restDurationSeconds, repsInReserve,
                    WorkoutSetType.STANDARD, completed);
        }

        /**
         * 兼容尚未采集 RIR 的内部调用和旧测试数据。
         *
         * @param number 客户端显示序号
         * @param weightKg 重量
         * @param repetitions 次数
         * @param restDurationSeconds 实际组间歇
         * @param completed 是否完成
         */
        public WorkoutSetRequest(Integer number, BigDecimal weightKg, Integer repetitions,
                Integer restDurationSeconds, Boolean completed) {
            this(number, weightKg, repetitions, restDurationSeconds, null,
                    WorkoutSetType.STANDARD, completed);
        }
    }

    /**
     * 描述历史纠错提交的完整动作集合。
     *
     * @param exercises 与原训练动作一一对应的更新
     */
    public record WorkoutHistoryUpdateRequest(
            @NotNull @Size(min = ValidationRule.WORKOUT_EXERCISE_MIN_COUNT,
                    max = ValidationRule.WORKOUT_EXERCISE_MAX_COUNT) List<@Valid WorkoutHistoryExerciseUpdate> exercises) {
    }

    /**
     * 描述一个历史动作的新组列表。
     *
     * @param sessionExerciseId 会话动作主键
     * @param sets 替换原记录的组列表
     */
    public record WorkoutHistoryExerciseUpdate(
            @NotNull @Positive Long sessionExerciseId,
            @NotNull @Size(max = ValidationRule.WORKOUT_SET_MAX_COUNT) List<@Valid WorkoutSetRequest> sets) {
    }

    /**
     * 保存幂等查询命中的既有训练。
     *
     * @param id 会话主键
     * @param totalVolumeKg 已保存总容量
     */
    private record ExistingWorkout(Long id, BigDecimal totalVolumeKg) {
    }

    /**
     * 保存同类上一次训练的对比基线。
     *
     * @param totalVolumeKg 总容量
     * @param durationMinutes 持续分钟数
     * @param completedSetCount 完成组数
     */
    private record PreviousWorkout(BigDecimal totalVolumeKg, Integer durationMinutes, Integer completedSetCount) {
    }

    /**
     * 保存动作跨历史训练聚合后的最大指标。
     *
     * <p>包级可见性供服务单元测试构造基线，不作为接口模型。</p>
     */
    public record HistoricalExerciseMetricRow(Long exerciseId, BigDecimal maxWeightKg,
            BigDecimal estimatedOneRepMaxKg, BigDecimal maxSetVolumeKg, BigDecimal maxExerciseVolumeKg,
            Integer maxExerciseRepetitions) {
    }

    /**
     * 保存某动作某重量的历史最大次数。
     *
     * @param exerciseId 动作主键
     * @param weightKg 规范重量
     * @param maxRepetitions 历史最大次数
     */
    public record HistoricalWeightRepetitionRow(Long exerciseId, BigDecimal weightKg, Integer maxRepetitions) {
    }

    /**
     * 保存本次动作全部完成组的聚合指标。
     *
     * <p>该内部值同时用于突破判断和最终个人最佳快照。</p>
     */
    private record CurrentExerciseMetrics(BigDecimal totalVolumeKg, Integer totalRepetitions,
            BigDecimal maxWeightKg, BigDecimal maxSetVolumeKg, BigDecimal estimatedOneRepMaxKg,
            Map<BigDecimal, Integer> repetitionsByWeight) {
    }

    /**
     * 保存同重量次数提升候选。
     *
     * @param weightKg 对比重量
     * @param currentRepetitions 本次次数
     * @param previousRepetitions 历史次数
     */
    private record WeightRepImprovement(BigDecimal weightKg, Integer currentRepetitions,
            Integer previousRepetitions) {
    }

    /**
     * 描述一个动作的本次训练表现。
     *
     * <p>包含聚合指标、是否首次记录、新纪录列表和截至当前的个人最佳。</p>
     */
    public record ExercisePerformanceSummary(Long exerciseId, String exerciseName, Integer completedSetCount,
            Integer totalRepetitions, BigDecimal totalVolumeKg, BigDecimal maxWeightKg,
            BigDecimal estimatedOneRepMaxKg, Boolean firstRecorded, List<ExerciseAchievement> achievements,
            PersonalBestSnapshot personalBest) {
    }

    /**
     * 描述动作截至当前的个人最佳快照。
     *
     * <p>repetitionsByWeight 的键为规范重量文本，值为该重量最大次数。</p>
     */
    public record PersonalBestSnapshot(BigDecimal maxWeightKg, BigDecimal estimatedOneRepMaxKg,
            BigDecimal maxSetVolumeKg, BigDecimal maxExerciseVolumeKg, Integer maxExerciseRepetitions,
            Map<String, Integer> repetitionsByWeight) {
    }

    /**
     * 描述本次训练新产生的一项纪录。
     *
     * @param type 稳定纪录编码
     * @param label 中文名称
     * @param value 新纪录值
     * @param previousValue 原纪录值
     * @param unit 单位
     * @param detail 计算或重量说明
     */
    public record ExerciseAchievement(String type, String label, BigDecimal value, BigDecimal previousValue,
            String unit, String detail) {
    }

    /**
     * 描述本次训练与上一次同类训练的差异。
     *
     * <p>容量变化率仅在上次容量大于零时计算，避免无意义除零结果。</p>
     */
    public record WorkoutComparison(BigDecimal previousVolumeKg, Integer previousSetCount,
            Integer previousDurationMinutes, BigDecimal volumeDifferenceKg, BigDecimal volumeChangePercent,
            Integer setDifference, Integer durationDifferenceMinutes) {
    }

    /**
     * 描述训练完成后的完整响应。
     *
     * @param id 会话主键
     * @param totalVolumeKg 服务端重算容量
     * @param exerciseSummaries 各动作表现
     * @param comparison 同类训练对比
     */
    public record WorkoutCompletionResponse(Long id, BigDecimal totalVolumeKg,
            List<ExercisePerformanceSummary> exerciseSummaries, WorkoutComparison comparison) {

        /**
         * 构造只包含会话和容量的兼容响应。
         *
         * @param id 会话主键
         * @param totalVolumeKg 总容量
         */
        public WorkoutCompletionResponse(Long id, BigDecimal totalVolumeKg) {
            this(id, totalVolumeKg, List.of(), null);
        }
    }
}

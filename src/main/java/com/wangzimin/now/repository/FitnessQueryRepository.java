package com.wangzimin.now.repository;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.DashboardMetricDefinition;
import com.wangzimin.now.domain.EffortRule;
import com.wangzimin.now.domain.ExerciseSource;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.domain.WeekState;
import com.wangzimin.now.domain.WeekDayDefinition;
import com.wangzimin.now.domain.WorkoutSetType;
import com.wangzimin.now.domain.WorkoutStatus;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 聚合训练看板、动作目录、模板和历史记录的只读查询。
 *
 * <p>仓储使用 JdbcClient 显式控制 SQL，并始终把可选用户主键绑定为参数。
 * 公共数据允许匿名读取，用户模板和历史通过 owner_user_id 隔离。
 * 动作分类从规范化分类表批量装配，不包含任何部位专用字段。</p>
 */
@Repository
public class FitnessQueryRepository {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern(SystemText.DAY_FORMAT.value());
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern(SystemText.MONTH_FORMAT.value());

    private final JdbcClient jdbcClient;

    /**
     * 创建健身只读查询服务。
     *
     * @param jdbcClient 执行参数化 SQL 的客户端
     */
    public FitnessQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 查询匿名访问可见的训练看板。
     *
     * <p>该重载为既有测试和公共调用保留，内部统一走带用户主键的实现。</p>
     *
     * @return 公共模板和空用户历史组成的看板
     */
    public DashboardResponse dashboard() {
        return dashboard(null);
    }

    /**
     * 查询指定账号可见的训练看板。
     *
     * <p>模板选择排除当前用户隐藏的系统模板，并优先个人模板；
     * 周完成次数和最近训练严格按用户归属过滤。</p>
     *
     * @param userId 可空用户主键
     * @return 今日计划、指标、周摘要和最近训练
     */
    public DashboardResponse dashboard(Long userId) {
        PlanRow plan = jdbcClient.sql("""
                        SELECT wp.id, wp.name, wp.estimated_minutes AS estimatedMinutes,
                               wp.weekly_target AS weeklyTarget, COUNT(pe.id) AS exerciseCount
                        FROM workout_plan wp
                        LEFT JOIN plan_exercise pe ON pe.plan_id = wp.id
                        WHERE wp.is_active = TRUE
                          AND (wp.owner_user_id IS NULL OR wp.owner_user_id = :userId)
                          AND (:userId IS NULL OR NOT EXISTS (
                              SELECT 1 FROM user_hidden_workout_plan hidden
                              WHERE hidden.user_id = :userId AND hidden.plan_id = wp.id
                          ))
                        GROUP BY wp.id, wp.name, wp.estimated_minutes, wp.weekly_target
                        ORDER BY MAX(wp.owner_user_id IS NOT NULL) DESC, wp.id
                        LIMIT 1
                        """)
                .param("userId", userId, Types.BIGINT)
                .query(PlanRow.class)
                .optional()
                .orElse(new PlanRow(
                        (long) BusinessRule.ZERO_COUNT.value(),
                        SystemText.EMPTY_PLAN_NAME.value(),
                        BusinessRule.ZERO_COUNT.value(),
                        BusinessRule.DEFAULT_WEEKLY_TARGET.value(),
                        BusinessRule.ZERO_COUNT.value()));

        int completedThisWeek = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM workout_session
                        WHERE status = :completedStatus
                          AND ((:userId IS NULL AND owner_user_id IS NULL) OR owner_user_id = :userId)
                          AND ended_at >= DATE_SUB(CURRENT_DATE, INTERVAL WEEKDAY(CURRENT_DATE) DAY)
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .query(Integer.class)
                .single();

        WorkoutHistoryItem recentWorkout = history(userId).stream()
                .findFirst()
                .orElse(new WorkoutHistoryItem(
                        (long) BusinessRule.ZERO_COUNT.value(),
                        SystemText.EMPTY_WORKOUT_NAME.value(),
                        LocalDateTime.now(),
                        BusinessRule.ZERO_COUNT.value(),
                        BusinessRule.ZERO_COUNT.value(),
                        BusinessRule.ZERO_COUNT.value(),
                        BigDecimal.ZERO));

        List<Metric> metrics = java.util.Arrays.stream(DashboardMetricDefinition.values())
                .map(metric -> new Metric(metric.key(), metric.value(), metric.label(), metric.target()))
                .toList();

        return new DashboardResponse(
                new TodayPlan(plan.id(), plan.name(), plan.exerciseCount(), plan.estimatedMinutes(),
                        BusinessRule.ZERO_COUNT.value()),
                metrics,
                new WeekSummary(completedThisWeek, plan.weeklyTarget(), buildWeekDays()),
                recentWorkout.withFormattedDate());
    }

    /**
     * 查询匿名访问可见的训练模板。
     *
     * @return 公共模板及其动作明细
     */
    public List<WorkoutPlanResponse> workoutPlans() {
        return workoutPlans(null);
    }

    /**
     * 查询指定账号可见的系统模板和个人模板。
     *
     * <p>模板主数据与动作明细分两次批量查询，避免使用统计连接时重复计数。
     * 使用次数仅统计同一账号已完成的训练。</p>
     *
     * @param userId 可空用户主键
     * @return 包含使用统计和 GIF 的模板列表
     */
    public List<WorkoutPlanResponse> workoutPlans(Long userId) {
        List<WorkoutPlanRow> plans = jdbcClient.sql("""
                        SELECT wp.id, wp.name, wp.description,
                               wp.estimated_minutes AS estimatedMinutes,
                               COALESCE(plan_usage.usage_count, 0) AS usageCount,
                               plan_usage.last_used_at AS lastUsedAt,
                               (wp.owner_user_id IS NOT NULL) AS ownedByCurrentUser
                        FROM workout_plan wp
                        LEFT JOIN (
                            SELECT plan_id, COUNT(*) AS usage_count, MAX(ended_at) AS last_used_at
                            FROM workout_session
                            WHERE status = :completedStatus
                              AND ((:userId IS NULL AND owner_user_id IS NULL) OR owner_user_id = :userId)
                            GROUP BY plan_id
                        ) plan_usage ON plan_usage.plan_id = wp.id
                        WHERE wp.is_active = TRUE
                          AND (wp.owner_user_id IS NULL OR wp.owner_user_id = :userId)
                          AND (:userId IS NULL OR NOT EXISTS (
                              SELECT 1 FROM user_hidden_workout_plan hidden
                              WHERE hidden.user_id = :userId AND hidden.plan_id = wp.id
                          ))
                        ORDER BY (wp.owner_user_id IS NOT NULL) DESC, wp.id
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .query(WorkoutPlanRow.class)
                .list();

        List<PlanExercise> planExercises = jdbcClient.sql("""
                        SELECT pe.plan_id AS planId, e.id, e.name, e.muscle_group AS muscleGroup,
                               COALESCE(
                                   (
                                       SELECT subcategory.name
                                       FROM exercise_subcategory_mapping mapping
                                       JOIN exercise_subcategory subcategory
                                         ON subcategory.id = mapping.subcategory_id
                                        AND subcategory.is_active = TRUE
                                       WHERE mapping.exercise_id = e.id
                                       ORDER BY subcategory.sort_order, subcategory.id
                                       LIMIT 1
                                   ),
                                   primary_category.name
                               ) AS subcategoryLabel,
                               pe.target_sets AS targetSets, pe.target_reps AS targetReps,
                               pe.rest_seconds AS restSeconds,
                               pe.progressive_overload_enabled AS progressiveOverloadEnabled,
                               pe.replacement_progressive_overload_enabled AS replacementProgressiveOverloadEnabled,
                               COALESCE(
                                   NULLIF(e.gif_url, ''),
                                   (
                                       SELECT gif_candidate.gif_url
                                       FROM exercise gif_candidate
                                       WHERE gif_candidate.name = e.name
                                         AND gif_candidate.gif_url IS NOT NULL
                                         AND gif_candidate.gif_url <> ''
                                       ORDER BY gif_candidate.id DESC
                                       LIMIT 1
                                   )
                               ) AS gifUrl,
                               alternative.id AS replacementExerciseId,
                               alternative.name AS replacementExerciseName,
                               alternative.muscle_group AS replacementExerciseMuscleGroup,
                               COALESCE(
                                   (
                                       SELECT replacement_subcategory.name
                                       FROM exercise_subcategory_mapping replacement_mapping
                                       JOIN exercise_subcategory replacement_subcategory
                                         ON replacement_subcategory.id = replacement_mapping.subcategory_id
                                        AND replacement_subcategory.is_active = TRUE
                                       WHERE replacement_mapping.exercise_id = alternative.id
                                       ORDER BY replacement_subcategory.sort_order, replacement_subcategory.id
                                       LIMIT 1
                                   ),
                                   replacement_category.name
                               ) AS replacementExerciseSubcategoryLabel,
                               COALESCE(
                                   NULLIF(alternative.gif_url, ''),
                                   (
                                       SELECT replacement_gif.gif_url
                                       FROM exercise replacement_gif
                                       WHERE replacement_gif.name = alternative.name
                                         AND replacement_gif.gif_url IS NOT NULL
                                         AND replacement_gif.gif_url <> ''
                                       ORDER BY replacement_gif.id DESC
                                       LIMIT 1
                                   )
                               ) AS replacementExerciseGifUrl
                        FROM plan_exercise pe
                        JOIN exercise e ON e.id = pe.exercise_id
                        JOIN exercise_category primary_category ON primary_category.code = e.category_code
                        LEFT JOIN exercise alternative ON alternative.id = pe.replacement_exercise_id
                        LEFT JOIN exercise_category replacement_category
                          ON replacement_category.code = alternative.category_code
                        JOIN workout_plan wp ON wp.id = pe.plan_id
                        WHERE wp.is_active = TRUE
                          AND (wp.owner_user_id IS NULL OR wp.owner_user_id = :userId)
                          AND (:userId IS NULL OR NOT EXISTS (
                              SELECT 1 FROM user_hidden_workout_plan hidden
                              WHERE hidden.user_id = :userId AND hidden.plan_id = wp.id
                          ))
                        ORDER BY pe.plan_id, pe.exercise_order
                        """)
                .param("userId", userId, Types.BIGINT)
                .query(PlanExercise.class)
                .list();

        Map<Long, List<PlanExercise>> exercisesByPlan = new LinkedHashMap<>();
        planExercises.forEach(exercise -> exercisesByPlan
                .computeIfAbsent(exercise.planId(), ignored -> new ArrayList<>())
                .add(exercise));

        return plans.stream()
                .map(plan -> new WorkoutPlanResponse(
                        plan.id(),
                        plan.name(),
                        plan.description(),
                        plan.estimatedMinutes(),
                        plan.usageCount(),
                        plan.lastUsedAt(),
                        plan.ownedByCurrentUser(),
                        exercisesByPlan.getOrDefault(plan.id(), List.of())))
                .toList();
    }

    /**
     * 按主键查询匿名可见模板。
     *
     * @param planId 模板主键
     * @return 匹配模板
     */
    public WorkoutPlanResponse workoutPlan(Long planId) {
        return workoutPlan(planId, null);
    }

    /**
     * 按用户可见范围查询单个模板。
     *
     * <p>复用模板列表的归属和隐藏规则，避免详情接口出现权限分叉。</p>
     *
     * @param planId 模板主键
     * @param userId 可空用户主键
     * @return 可见模板
     */
    public WorkoutPlanResponse workoutPlan(Long planId, Long userId) {
        return workoutPlans(userId).stream()
                .filter(plan -> plan.id().equals(planId))
                .findFirst()
                .orElseThrow(ApiErrorCode.PLAN_NOT_FOUND::exception);
    }

    /**
     * 查询标准动作库，并使用数据库维护的二级分类进行通用筛选。
     *
     * <p>category 和 subcategory 都是数据值，服务层不识别胸、背、肩等固定部位。
     * 当数据库新增部位或二级分类时，本方法无需修改。</p>
     *
     * @param category 一级分类编码，可为空
     * @param subcategory 二级分类编码，可为空
     * @param keyword 动作、器械或目标肌群关键词
     * @param page 从一开始的页码
     * @param limit 每页数量
     * @return 动作分页和每个动作的动态二级分类
     */
    public ExercisePageResponse exercises(String category, String subcategory, String keyword,
            Integer page, Integer limit) {
        int firstPage = BusinessRule.EXERCISE_DEFAULT_PAGE.value();
        int safePage = page == null ? firstPage : Math.max(page, firstPage);
        int defaultLimit = BusinessRule.EXERCISE_DEFAULT_LIMIT.value();
        int safeLimit = limit == null
                ? defaultLimit
                : Math.min(Math.max(limit, BusinessRule.COLLECTION_MIN_SIZE.value()),
                        BusinessRule.EXERCISE_MAX_LIMIT.value());
        int offset = (safePage - firstPage) * safeLimit;
        String categoryFilter = normalizeFilter(category);
        String subcategoryFilter = normalizeFilter(subcategory);
        String keywordFilter = normalizeFilter(keyword);

        String whereSql = """
                FROM exercise exercise
                JOIN exercise_category category ON category.code = exercise.category_code
                WHERE exercise.source = :source
                  AND category.is_active = TRUE
                  AND (:category = '' OR exercise.category_code = :category)
                  AND (
                    :subcategory = ''
                    OR EXISTS (
                        SELECT 1
                        FROM exercise_subcategory_mapping mapping
                        JOIN exercise_subcategory subcategory
                          ON subcategory.id = mapping.subcategory_id
                         AND subcategory.is_active = TRUE
                        WHERE mapping.exercise_id = exercise.id
                          AND subcategory.code = :subcategory
                          AND subcategory.category_code = exercise.category_code
                    )
                  )
                  AND (
                    :keyword = ''
                    OR exercise.name LIKE CONCAT('%', :keyword, '%')
                    OR exercise.name_en LIKE CONCAT('%', :keyword, '%')
                    OR exercise.equipment LIKE CONCAT('%', :keyword, '%')
                    OR exercise.target_name LIKE CONCAT('%', :keyword, '%')
                  )
                """;

        long total = jdbcClient.sql("SELECT COUNT(*) " + whereSql)
                .param("source", ExerciseSource.STANDARD_DATASET.databaseValue())
                .param("category", categoryFilter)
                .param("subcategory", subcategoryFilter)
                .param("keyword", keywordFilter)
                .query(Long.class)
                .single();

        List<ExerciseRow> rows = jdbcClient.sql("""
                        SELECT exercise.id,
                               exercise.source_exercise_id AS sourceExerciseId,
                               exercise.name,
                               exercise.name_en AS nameEn,
                               exercise.category_code AS categoryCode,
                               category.name AS categoryName,
                               exercise.muscle_group AS muscleGroup,
                               exercise.equipment,
                               exercise.target_name AS targetName,
                               exercise.instructions,
                               exercise.gif_url AS gifUrl,
                               exercise.attribution
                        """ + whereSql + """
                        ORDER BY category.sort_order, exercise.popularity_rank, exercise.id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("source", ExerciseSource.STANDARD_DATASET.databaseValue())
                .param("category", categoryFilter)
                .param("subcategory", subcategoryFilter)
                .param("keyword", keywordFilter)
                .param("limit", safeLimit)
                .param("offset", offset)
                .query(ExerciseRow.class)
                .list();

        Map<Long, List<ExerciseSubcategory>> subcategoriesByExercise = loadExerciseSubcategories(
                rows.stream().map(ExerciseRow::id).toList());
        List<ExerciseResponse> data = rows.stream()
                .map(row -> new ExerciseResponse(
                        row.id(), row.sourceExerciseId(), row.name(), row.nameEn(), row.categoryCode(),
                        row.categoryName(), subcategoriesByExercise.getOrDefault(row.id(), List.of()),
                        row.muscleGroup(), row.equipment(), row.targetName(), row.instructions(), row.gifUrl(),
                        row.attribution()))
                .toList();

        int totalPages = total == BusinessRule.ZERO_COUNT.value()
                ? BusinessRule.ZERO_COUNT.value()
                : Math.toIntExact((total + safeLimit - BusinessRule.COLLECTION_MIN_SIZE.longValue()) / safeLimit);
        return new ExercisePageResponse(data, total, safePage, safeLimit, totalPages);
    }

    /**
     * 读取一批动作关联的二级分类。
     *
     * <p>单独批量查询可以避免分页主查询因多标签关联而产生重复动作行，
     * 同时避免逐动作查询造成 N+1 性能问题。</p>
     *
     * @param exerciseIds 当前页动作主键
     * @return 按动作主键分组、按数据库排序的二级分类
     */
    private Map<Long, List<ExerciseSubcategory>> loadExerciseSubcategories(List<Long> exerciseIds) {
        if (exerciseIds.isEmpty()) {
            return Map.of();
        }
        List<ExerciseSubcategoryRow> rows = jdbcClient.sql("""
                        SELECT mapping.exercise_id AS exerciseId,
                               subcategory.id,
                               subcategory.code,
                               subcategory.name
                        FROM exercise_subcategory_mapping mapping
                        JOIN exercise_subcategory subcategory ON subcategory.id = mapping.subcategory_id
                        WHERE mapping.exercise_id IN (:exerciseIds)
                          AND subcategory.is_active = TRUE
                        ORDER BY mapping.exercise_id, subcategory.sort_order, subcategory.id
                        """)
                .param("exerciseIds", exerciseIds)
                .query(ExerciseSubcategoryRow.class)
                .list();
        Map<Long, List<ExerciseSubcategory>> result = new LinkedHashMap<>();
        rows.forEach(row -> result
                .computeIfAbsent(row.exerciseId(), ignored -> new ArrayList<>())
                .add(new ExerciseSubcategory(row.id(), row.code(), row.name())));
        return result;
    }

    /**
     * 查询一级分类以及由数据库维护的二级分类定义。
     *
     * <p>响应中的二级分类来自独立表，前端可以直接生成页签，
     * 无需在代码中识别部位或维护标签名称。</p>
     *
     * @return 按数据库顺序排列的分类树
     */
    public List<ExerciseCategory> exerciseCategories() {
        List<ExerciseCategoryRow> categories = jdbcClient.sql("""
                        SELECT category.code, category.name, COUNT(exercise.id) AS exerciseCount
                        FROM exercise_category category
                        LEFT JOIN exercise
                          ON exercise.category_code = category.code
                         AND exercise.source = :source
                        WHERE category.is_active = TRUE
                        GROUP BY category.code, category.name, category.sort_order
                        ORDER BY category.sort_order, category.code
                        """)
                .param("source", ExerciseSource.STANDARD_DATASET.databaseValue())
                .query(ExerciseCategoryRow.class)
                .list();
        List<ExerciseSubcategoryCategoryRow> subcategories = jdbcClient.sql("""
                        SELECT category_code AS categoryCode, id, code, name
                        FROM exercise_subcategory
                        WHERE is_active = TRUE
                        ORDER BY category_code, sort_order, id
                        """)
                .query(ExerciseSubcategoryCategoryRow.class)
                .list();
        Map<String, List<ExerciseSubcategory>> subcategoriesByCategory = new LinkedHashMap<>();
        subcategories.forEach(row -> subcategoriesByCategory
                .computeIfAbsent(row.categoryCode(), ignored -> new ArrayList<>())
                .add(new ExerciseSubcategory(row.id(), row.code(), row.name())));
        return categories.stream()
                .map(category -> new ExerciseCategory(
                        category.code(), category.name(), category.exerciseCount(),
                        subcategoriesByCategory.getOrDefault(category.code(), List.of())))
                .toList();
    }

    /**
     * 将可空的查询值转换为统一的过滤参数。
     *
     * @param value 原始请求参数
     * @return 去除首尾空白后的值，空值转换为空字符串
     */
    private String normalizeFilter(String value) {
        return value == null ? SystemText.EMPTY.value() : value.trim();
    }

    /**
     * 查询匿名账号的最近训练历史。
     *
     * @return 无用户归属的已完成训练
     */
    public List<WorkoutHistoryItem> history() {
        return history(null);
    }

    /**
     * 查询指定账号最近的已完成训练摘要。
     *
     * <p>动作数使用去重计数，完成组数只统计完成状态；
     * 结果数量由统一业务规则限制，覆盖移动端趋势窗口。</p>
     *
     * @param userId 可空用户主键
     * @return 按完成时间倒序排列的历史摘要
     */
    public List<WorkoutHistoryItem> history(Long userId) {
        return jdbcClient.sql("""
                        SELECT ws.id, ws.name_snapshot AS name, ws.ended_at AS completedAt,
                               ws.duration_minutes AS durationMinutes,
                               COUNT(DISTINCT se.id) AS exerciseCount,
                               SUM(CASE WHEN sr.status = :completedStatus
                                   AND (sr.set_type IS NULL OR sr.set_type <> :dropSetType)
                                   THEN 1 ELSE 0 END) AS completedSetCount,
                               ws.total_volume_kg AS totalVolumeKg
                        FROM workout_session ws
                        LEFT JOIN session_exercise se ON se.session_id = ws.id
                        LEFT JOIN set_record sr ON sr.session_exercise_id = se.id
                        WHERE ws.status = :completedStatus
                          AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                        GROUP BY ws.id, ws.name_snapshot, ws.ended_at, ws.duration_minutes, ws.total_volume_kg
                        ORDER BY ws.ended_at DESC
                        LIMIT :historyLimit
                        """)
                .param("userId", userId, Types.BIGINT)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .param("dropSetType", WorkoutSetType.DROP_SET.databaseValue())
                .param("historyLimit", BusinessRule.HISTORY_RESULT_LIMIT.value())
                .query(WorkoutHistoryItem.class)
                .list();
    }

    /**
     * 查询匿名账号的一条训练历史详情。
     *
     * @param sessionId 训练会话主键
     * @return 训练、动作和组明细
     */
    public WorkoutHistoryDetail historyDetail(Long sessionId) {
        return historyDetail(sessionId, null);
    }

    /**
     * 查询指定账号的一条已完成训练详情。
     *
     * <p>先校验会话状态和用户归属，再批量读取动作与组记录。
     * 无组动作仍通过左连接保留，组记录按动作和组号稳定排序。</p>
     *
     * @param sessionId 训练会话主键
     * @param userId 可空用户主键
     * @return 完整训练详情
     */
    public WorkoutHistoryDetail historyDetail(Long sessionId, Long userId) {
        WorkoutHistoryDetailRow session = jdbcClient.sql("""
                        SELECT id, name_snapshot AS name, ended_at AS completedAt,
                               duration_minutes AS durationMinutes, total_volume_kg AS totalVolumeKg
                        FROM workout_session
                        WHERE id = :sessionId AND status = :completedStatus
                          AND ((:userId IS NULL AND owner_user_id IS NULL) OR owner_user_id = :userId)
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId, Types.BIGINT)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .query(WorkoutHistoryDetailRow.class)
                .optional()
                .orElseThrow(ApiErrorCode.WORKOUT_NOT_FOUND::exception);

        List<WorkoutHistoryExercise> exercises = jdbcClient.sql("""
                        SELECT se.id, se.exercise_id AS exerciseId, se.exercise_name_snapshot AS name,
                               COALESCE(
                                   NULLIF(e.gif_url, ''),
                                   (
                                       SELECT gif_candidate.gif_url
                                       FROM exercise gif_candidate
                                       WHERE gif_candidate.name = se.exercise_name_snapshot
                                         AND gif_candidate.gif_url IS NOT NULL
                                         AND gif_candidate.gif_url <> ''
                                       ORDER BY gif_candidate.id DESC
                                       LIMIT 1
                                   )
                               ) AS gifUrl,
                               se.exercise_order AS exerciseOrder,
                               sr.set_number AS setNumber, sr.set_type AS setType,
                               sr.weight_kg AS weightKg, sr.repetitions, sr.rpe,
                               sr.rest_duration_seconds AS restDurationSeconds, sr.status, sr.completed_at AS completedAt
                        FROM session_exercise se
                        LEFT JOIN exercise e ON e.id = se.exercise_id
                        LEFT JOIN set_record sr ON sr.session_exercise_id = se.id
                        WHERE se.session_id = :sessionId
                        ORDER BY se.exercise_order, sr.set_number
                        """)
                .param("sessionId", sessionId)
                .query((resultSet, rowNumber) -> new HistorySetRow(
                        resultSet.getLong("id"),
                        resultSet.getObject("exerciseId", Long.class),
                        resultSet.getString("name"),
                        resultSet.getString("gifUrl"),
                        resultSet.getInt("exerciseOrder"),
                        resultSet.getObject("setNumber", Integer.class),
                        resultSet.getString("setType"),
                        resultSet.getBigDecimal("weightKg"),
                        resultSet.getObject("repetitions", Integer.class),
                        resultSet.getBigDecimal("rpe"),
                        resultSet.getObject("restDurationSeconds", Integer.class),
                        resultSet.getString("status"),
                        resultSet.getTimestamp("completedAt") == null ? null
                                : resultSet.getTimestamp("completedAt").toLocalDateTime()))
                .list()
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        HistorySetRow::id,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .values()
                .stream()
                .map(rows -> {
                    HistorySetRow first = rows.get(0);
                    List<WorkoutSetDetail> sets = rows.stream()
                            .filter(row -> row.setNumber() != null)
                            .map(row -> new WorkoutSetDetail(row.setNumber(),
                                    WorkoutSetType.fromDatabaseValue(row.setType()),
                                    row.weightKg(), row.repetitions(),
                                    row.restDurationSeconds(), EffortRule.toRepsInReserve(row.rpe()),
                                    row.status(), row.completedAt()))
                            .toList();
                    return new WorkoutHistoryExercise(first.id(), first.exerciseId(), first.name(), first.gifUrl(), first.exerciseOrder(), sets);
                })
                .toList();

        return new WorkoutHistoryDetail(session.id(), session.name(), session.completedAt(), session.durationMinutes(),
                session.totalVolumeKg(), exercises);
    }

    /**
     * 批量查询当前用户多个动作的最近训练表现。
     *
     * <p>输入先去空、去重并限制数量；窗口函数为每个动作选择最近两次包含完成组的训练。
     * 顶层字段继续表示最近一次，recentPerformances 则为谨慎渐进判断提供连续证据，
     * 避免客户端逐动作发送请求或根据单次表现直接加重。</p>
     *
     * @param userId 当前账号主键
     * @param exerciseIds 待查询动作主键集合
     * @return 每个动作最近表现
     */
    public List<LatestExercisePerformance> latestExercisePerformances(Long userId, Collection<Long> exerciseIds) {
        List<Long> normalizedIds = exerciseIds == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(exerciseIds.stream()
                .filter(id -> id != null && id > BusinessRule.ZERO_COUNT.value())
                .toList()));
        if (normalizedIds.isEmpty()) {
            return List.of();
        }
        if (normalizedIds.size() > BusinessRule.LATEST_PERFORMANCE_MAX_EXERCISES.value()) {
            throw ApiErrorCode.LATEST_PERFORMANCE_LIMIT.exception();
        }

        List<LatestPerformanceSetRow> rows = jdbcClient.sql("""
                        WITH ranked_exercise AS (
                            SELECT se.id AS sessionExerciseId, se.exercise_id AS exerciseId,
                                   ws.ended_at AS completedAt,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY se.exercise_id
                                       ORDER BY ws.ended_at DESC, se.id DESC
                                   ) AS performanceRank
                            FROM session_exercise se
                            JOIN workout_session ws ON ws.id = se.session_id
                            WHERE ws.status = :completedStatus
                              AND ws.owner_user_id = :userId
                              AND se.exercise_id IN (:exerciseIds)
                              AND EXISTS (
                                  SELECT 1 FROM set_record completed_set
                                  WHERE completed_set.session_exercise_id = se.id
                                    AND completed_set.status = :completedStatus
                              )
                        )
                        SELECT ranked.sessionExerciseId, ranked.exerciseId, ranked.completedAt,
                               ranked.performanceRank,
                               sr.set_number AS setNumber, sr.set_type AS setType,
                               sr.weight_kg AS weightKg, sr.repetitions, sr.rpe,
                               sr.rest_duration_seconds AS restDurationSeconds
                        FROM ranked_exercise ranked
                        JOIN set_record sr ON sr.session_exercise_id = ranked.sessionExerciseId
                        WHERE ranked.performanceRank <= :recentLimit AND sr.status = :completedStatus
                        ORDER BY ranked.exerciseId, ranked.performanceRank, sr.set_number
                        """)
                .param("userId", userId)
                .param("exerciseIds", normalizedIds)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .param("recentLimit", EffortRule.RECENT_PERFORMANCE_SESSION_LIMIT.value())
                .query(LatestPerformanceSetRow.class)
                .list();

        return rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        LatestPerformanceSetRow::exerciseId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .values()
                .stream()
                .map(exerciseRows -> {
                    LatestPerformanceSetRow first = exerciseRows.get(BusinessRule.ZERO_COUNT.value());
                    List<ExercisePerformanceSession> recentPerformances = exerciseRows.stream()
                            .collect(java.util.stream.Collectors.groupingBy(
                                    LatestPerformanceSetRow::sessionExerciseId,
                                    LinkedHashMap::new,
                                    java.util.stream.Collectors.toList()))
                            .values()
                            .stream()
                            .map(sessionRows -> new ExercisePerformanceSession(
                                    sessionRows.get(BusinessRule.ZERO_COUNT.value()).completedAt(),
                                    sessionRows.stream()
                                             .map(row -> new LatestPerformanceSet(row.setNumber(), row.weightKg(),
                                                      row.repetitions(), EffortRule.toRepsInReserve(row.rpe()),
                                                      WorkoutSetType.fromDatabaseValue(row.setType()),
                                                      row.restDurationSeconds()))
                                            .toList()))
                            .toList();
                    ExercisePerformanceSession latest = recentPerformances.get(BusinessRule.ZERO_COUNT.value());
                    return new LatestExercisePerformance(first.exerciseId(), latest.completedAt(), latest.sets(),
                            recentPerformances);
                })
                .toList();
    }

    /**
     * 查询当前账号中一个动作的成长历史。
     *
     * <p>单条 SQL 以动作表为主表，即使账号尚无训练也会返回动作元数据；
     * 窗口函数只保留最近的完成训练，并且只装配完成组，避免跳过组影响曲线。</p>
     *
     * @param userId 当前账号主键
     * @param exerciseId 动作库主键
     * @return 动作资料与按时间倒序排列的训练表现
     */
    public ExerciseProgressResponse exerciseProgress(Long userId, Long exerciseId) {
        if (exerciseId == null || exerciseId <= BusinessRule.ZERO_COUNT.value()) {
            throw ApiErrorCode.EXERCISE_NOT_FOUND.exception();
        }

        List<ExerciseProgressRow> rows = jdbcClient.sql("""
                        WITH ranked_exercise AS (
                            SELECT se.id AS sessionExerciseId, se.exercise_id AS exerciseId,
                                   ws.id AS sessionId, ws.name_snapshot AS workoutName, ws.ended_at AS completedAt,
                                   ROW_NUMBER() OVER (
                                       ORDER BY ws.ended_at DESC, se.id DESC
                                   ) AS performanceRank
                            FROM session_exercise se
                            JOIN workout_session ws ON ws.id = se.session_id
                            WHERE se.exercise_id = :exerciseId
                              AND ws.owner_user_id = :userId
                              AND ws.status = :completedStatus
                              AND EXISTS (
                                  SELECT 1 FROM set_record completed_set
                                  WHERE completed_set.session_exercise_id = se.id
                                    AND completed_set.status = :completedStatus
                                    AND completed_set.set_type = :standardSetType
                              )
                        )
                        SELECT e.id AS exerciseId, e.name, e.muscle_group AS muscleGroup,
                               e.equipment, e.target_name AS targetName, e.gif_url AS gifUrl,
                               e.attribution,
                               ranked.sessionId, ranked.sessionExerciseId, ranked.workoutName,
                               ranked.completedAt, ranked.performanceRank,
                               sr.set_number AS setNumber, sr.set_type AS setType,
                               sr.weight_kg AS weightKg,
                               sr.repetitions, sr.rpe
                        FROM exercise e
                        LEFT JOIN ranked_exercise ranked
                               ON ranked.exerciseId = e.id
                              AND ranked.performanceRank <= :historyLimit
                        LEFT JOIN set_record sr
                               ON sr.session_exercise_id = ranked.sessionExerciseId
                              AND sr.status = :completedStatus
                              AND sr.set_type = :standardSetType
                        WHERE e.id = :exerciseId
                        ORDER BY ranked.completedAt DESC, ranked.sessionExerciseId DESC, sr.set_number
                        """)
                .param("exerciseId", exerciseId)
                .param("userId", userId, Types.BIGINT)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .param("standardSetType", WorkoutSetType.STANDARD.databaseValue())
                .param("historyLimit", BusinessRule.EXERCISE_PROGRESS_SESSION_LIMIT.value())
                .query(ExerciseProgressRow.class)
                .list();

        if (rows.isEmpty()) {
            throw ApiErrorCode.EXERCISE_NOT_FOUND.exception();
        }

        ExerciseProgressRow first = rows.get(BusinessRule.ZERO_COUNT.value());
        List<ExerciseProgressSession> sessions = rows.stream()
                .filter(row -> row.sessionId() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        ExerciseProgressRow::sessionExerciseId,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()))
                .values()
                .stream()
                .map(sessionRows -> {
                    ExerciseProgressRow session = sessionRows.get(BusinessRule.ZERO_COUNT.value());
                    List<LatestPerformanceSet> sets = sessionRows.stream()
                            .filter(row -> row.setNumber() != null)
                            .map(row -> new LatestPerformanceSet(row.setNumber(), row.weightKg(),
                                    row.repetitions(), EffortRule.toRepsInReserve(row.rpe()),
                                    WorkoutSetType.fromDatabaseValue(row.setType())))
                            .toList();
                    return new ExerciseProgressSession(session.sessionId(), session.workoutName(),
                            session.completedAt(), sets);
                })
                .toList();

        return new ExerciseProgressResponse(first.exerciseId(), first.name(), first.muscleGroup(),
                first.equipment(), first.targetName(), first.gifUrl(), first.attribution(), sessions);
    }

    /**
     * 构造看板使用的一周日期状态。
     *
     * <p>星期顺序、标签和默认训练日来自枚举；当前日期始终覆盖为 today 状态。</p>
     *
     * @return 从周一到周日的七个日期单元格
     */
    private List<WeekDay> buildWeekDays() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        List<WeekDay> days = new ArrayList<>();
        for (WeekDayDefinition definition : WeekDayDefinition.values()) {
            LocalDate date = monday.with(definition.dayOfWeek());
            WeekState state = date.equals(today) ? WeekState.TODAY : definition.defaultState();
            days.add(new WeekDay(definition.label(), String.valueOf(date.getDayOfMonth()), state.externalValue()));
        }
        return days;
    }

    /**
     * 保存看板首选模板聚合结果。
     *
     * @param id 模板主键
     * @param name 模板名称
     * @param estimatedMinutes 预计时长
     * @param weeklyTarget 每周目标次数
     * @param exerciseCount 动作数量
     */
    private record PlanRow(Long id, String name, Integer estimatedMinutes, Integer weeklyTarget,
            Integer exerciseCount) {
    }

    /**
     * 保存训练历史详情主记录。
     *
     * @param id 会话主键
     * @param name 训练名称快照
     * @param completedAt 完成时间
     * @param durationMinutes 持续分钟数
     * @param totalVolumeKg 总容量
     */
    private record WorkoutHistoryDetailRow(Long id, String name, LocalDateTime completedAt,
            Integer durationMinutes, BigDecimal totalVolumeKg) {
    }

    /**
     * 保存历史动作与组左连接的一行。
     *
     * <p>setNumber 可为空，用于表达尚无组记录但仍需返回的动作。</p>
     */
    private record HistorySetRow(Long id, Long exerciseId, String name, String gifUrl, Integer exerciseOrder, Integer setNumber,
            String setType,
            BigDecimal weightKg, Integer repetitions, BigDecimal rpe, Integer restDurationSeconds, String status,
            LocalDateTime completedAt) {
    }

    /**
     * 保存最近表现查询中的一组数据。
     *
     * @param sessionExerciseId 会话动作主键，用于把同一次训练的组重新聚合
     * @param exerciseId 动作主键
     * @param completedAt 训练完成时间
     * @param performanceRank 该动作按完成时间倒序的训练名次
     * @param setNumber 组序号
     * @param setType 组类型数据库值
     * @param weightKg 重量
     * @param repetitions 次数
     * @param rpe 数据库存储的主观用力程度
     */
    private record LatestPerformanceSetRow(Long sessionExerciseId, Long exerciseId, LocalDateTime completedAt,
            Integer performanceRank, Integer setNumber, String setType, BigDecimal weightKg,
            Integer repetitions, BigDecimal rpe, Integer restDurationSeconds) {
    }

    /**
     * 保存动作成长查询中的动作、训练与组联合行。
     *
     * <p>sessionId 及组字段允许为空，用于表达动作存在但当前账号没有完成记录。</p>
     */
    public record ExerciseProgressRow(Long exerciseId, String name, String muscleGroup, String equipment,
            String targetName, String gifUrl, String attribution, Long sessionId, Long sessionExerciseId,
            String workoutName, LocalDateTime completedAt, Integer performanceRank, Integer setNumber,
            String setType, BigDecimal weightKg, Integer repetitions, BigDecimal rpe) {

        /**
         * 兼容尚未包含组类型的成长查询测试与内部构造调用。
         *
         * @param exerciseId 动作主键
         * @param name 动作名称
         * @param muscleGroup 辅助肌群
         * @param equipment 器械
         * @param targetName 主要目标肌群
         * @param gifUrl 动作动画地址
         * @param attribution 媒体署名
         * @param sessionId 训练会话主键
         * @param sessionExerciseId 会话动作主键
         * @param workoutName 训练名称
         * @param completedAt 完成时间
         * @param performanceRank 训练倒序名次
         * @param setNumber 组序号
         * @param weightKg 重量
         * @param repetitions 次数
         * @param rpe 主观用力程度
         */
        public ExerciseProgressRow(Long exerciseId, String name, String muscleGroup, String equipment,
                String targetName, String gifUrl, String attribution, Long sessionId, Long sessionExerciseId,
                String workoutName, LocalDateTime completedAt, Integer performanceRank, Integer setNumber,
                BigDecimal weightKg, Integer repetitions, BigDecimal rpe) {
            this(exerciseId, name, muscleGroup, equipment, targetName, gifUrl, attribution,
                    sessionId, sessionExerciseId, workoutName, completedAt, performanceRank, setNumber,
                    WorkoutSetType.STANDARD.databaseValue(), weightKg, repetitions, rpe);
        }
    }

    /**
     * 保存动作分页主查询的一行，不包含多值二级分类。
     *
     * <p>二级分类由批量关联查询装配，避免 SQL 连接导致动作重复。</p>
     * 字段保持与标准动作 API 一致，仅作为内部装配载体。</p>
     */
    private record ExerciseRow(Long id, String sourceExerciseId, String name, String nameEn,
            String categoryCode, String categoryName, String muscleGroup, String equipment,
            String targetName, String instructions, String gifUrl, String attribution) {
    }

    /**
     * 保存动作与二级分类关联查询的一行。
     *
     * @param exerciseId 动作主键
     * @param id 二级分类主键
     * @param code 二级分类稳定编码
     * @param name 二级分类显示名称
     */
    private record ExerciseSubcategoryRow(Long exerciseId, Long id, String code, String name) {
    }

    /**
     * 保存一级分类计数查询的一行。
     *
     * @param code 一级分类编码
     * @param name 一级分类名称
     * @param exerciseCount 标准动作数量
     */
    private record ExerciseCategoryRow(String code, String name, Long exerciseCount) {
    }

    /**
     * 保存按一级分类分组所需的二级分类查询行。
     *
     * @param categoryCode 所属一级分类编码
     * @param id 二级分类主键
     * @param code 二级分类编码
     * @param name 二级分类名称
     */
    private record ExerciseSubcategoryCategoryRow(String categoryCode, Long id, String code, String name) {
    }

    /**
     * 保存训练模板主查询的统计行。
     *
     * <p>公开投影便于仓储测试构造结果，不作为控制器响应类型暴露。</p>
     */
    public record WorkoutPlanRow(Long id, String name, String description, Integer estimatedMinutes,
            Long usageCount, LocalDateTime lastUsedAt, Boolean ownedByCurrentUser) {

        /**
         * 构造默认非当前用户拥有的模板行。
         *
         * @param id 模板主键
         * @param name 模板名称
         * @param description 模板说明
         * @param estimatedMinutes 预计时长
         * @param usageCount 完成次数
         * @param lastUsedAt 最近完成时间
         */
        public WorkoutPlanRow(Long id, String name, String description, Integer estimatedMinutes,
                Long usageCount, LocalDateTime lastUsedAt) {
            this(id, name, description, estimatedMinutes, usageCount, lastUsedAt, false);
        }
    }

    /**
     * 描述训练看板完整响应。
     *
     * @param todayPlan 今日推荐计划
     * @param metrics 健康指标占位或真实数据
     * @param week 本周训练摘要
     * @param recentWorkout 最近训练
     */
    public record DashboardResponse(TodayPlan todayPlan, List<Metric> metrics, WeekSummary week,
            FormattedWorkout recentWorkout) {
    }

    /**
     * 描述今日推荐训练计划。
     *
     * @param id 模板主键
     * @param name 模板名称
     * @param exerciseCount 动作数量
     * @param estimatedMinutes 预计分钟数
     * @param completionPercent 当前完成比例
     */
    public record TodayPlan(Long id, String name, Integer exerciseCount, Integer estimatedMinutes,
            Integer completionPercent) {
    }

    /**
     * 描述看板单个指标。
     *
     * @param key 客户端稳定键
     * @param value 当前值
     * @param label 中文标签
     * @param target 目标或提示
     */
    public record Metric(String key, String value, String label, String target) {
    }

    /**
     * 描述本周训练完成情况。
     *
     * @param completed 已完成次数
     * @param target 每周目标
     * @param days 七天显示状态
     */
    public record WeekSummary(Integer completed, Integer target, List<WeekDay> days) {
    }

    /**
     * 描述周视图中的一个日期单元格。
     *
     * @param label 中文星期标签
     * @param date 月内日期
     * @param state 今日、完成或休息状态
     */
    public record WeekDay(String label, String date, String state) {
    }

    /**
     * 描述训练模板和其有序动作列表。
     *
     * <p>使用统计只计算已完成训练；ownedByCurrentUser 用于客户端决定编辑权限。</p>
     */
    public record WorkoutPlanResponse(Long id, String name, String description, Integer estimatedMinutes,
            Long usageCount, LocalDateTime lastUsedAt, Boolean ownedByCurrentUser, List<PlanExercise> exercises) {

        /**
         * 构造未包含使用统计的兼容响应。
         *
         * @param id 模板主键
         * @param name 模板名称
         * @param description 模板说明
         * @param estimatedMinutes 预计时长
         * @param exercises 模板动作列表
         */
        public WorkoutPlanResponse(Long id, String name, String description, Integer estimatedMinutes,
                List<PlanExercise> exercises) {
            this(id, name, description, estimatedMinutes, BusinessRule.ZERO_COUNT.longValue(), null, false, exercises);
        }
    }

    /**
     * 描述训练模板中的一个动作和目标参数。
     *
     * @param planId 模板主键
     * @param id 动作主键
     * @param name 动作名称
     * @param muscleGroup 辅助肌群
     * @param subcategoryLabel 数据库主二级分类名称；未分类时回退一级分类
     * @param targetSets 目标组数
     * @param targetReps 目标次数
     * @param restSeconds 预设休息秒数
     * @param progressiveOverloadEnabled 是否为该动作开启渐进超负荷
     * @param gifUrl 动作动画地址
     * @param replacementExerciseId 可空的替换动作主键
     * @param replacementExerciseName 替换动作名称
     * @param replacementExerciseMuscleGroup 替换动作辅助肌群
     * @param replacementExerciseSubcategoryLabel 替换动作数据库主二级分类名称
     * @param replacementExerciseGifUrl 替换动作动画地址
     * @param replacementProgressiveOverloadEnabled 是否为替换动作开启渐进超负荷
     */
    public record PlanExercise(Long planId, Long id, String name, String muscleGroup, String subcategoryLabel,
            Integer targetSets,
            Integer targetReps, Integer restSeconds, Boolean progressiveOverloadEnabled, String gifUrl,
            Long replacementExerciseId, String replacementExerciseName,
            String replacementExerciseMuscleGroup, String replacementExerciseSubcategoryLabel,
            String replacementExerciseGifUrl,
            Boolean replacementProgressiveOverloadEnabled) {

        /**
         * 兼容尚未返回二级分类名称的完整模板动作构造调用。
         *
         * @param planId 模板主键
         * @param id 动作主键
         * @param name 动作名称
         * @param muscleGroup 辅助肌群
         * @param targetSets 目标组数
         * @param targetReps 目标次数
         * @param restSeconds 预设休息秒数
         * @param progressiveOverloadEnabled 是否开启渐进超负荷
         * @param gifUrl 动作动画地址
         * @param replacementExerciseId 替换动作主键
         * @param replacementExerciseName 替换动作名称
         * @param replacementExerciseMuscleGroup 替换动作辅助肌群
         * @param replacementExerciseGifUrl 替换动作动画地址
         * @param replacementProgressiveOverloadEnabled 替换动作是否开启渐进超负荷
         */
        public PlanExercise(Long planId, Long id, String name, String muscleGroup, Integer targetSets,
                Integer targetReps, Integer restSeconds, Boolean progressiveOverloadEnabled, String gifUrl,
                Long replacementExerciseId, String replacementExerciseName,
                String replacementExerciseMuscleGroup, String replacementExerciseGifUrl,
                Boolean replacementProgressiveOverloadEnabled) {
            this(planId, id, name, muscleGroup, null, targetSets, targetReps, restSeconds,
                    progressiveOverloadEnabled, gifUrl, replacementExerciseId, replacementExerciseName,
                    replacementExerciseMuscleGroup, null, replacementExerciseGifUrl,
                    replacementProgressiveOverloadEnabled);
        }

        /**
         * 构造旧调用兼容的模板动作；未提供开关时默认关闭。
         *
         * @param planId 模板主键
         * @param id 动作主键
         * @param name 动作名称
         * @param muscleGroup 辅助肌群
         * @param targetSets 目标组数
         * @param targetReps 目标次数
         * @param restSeconds 预设休息秒数
         * @param gifUrl 动作动画地址
         */
        public PlanExercise(Long planId, Long id, String name, String muscleGroup, Integer targetSets,
                Integer targetReps, Integer restSeconds, String gifUrl) {
            this(planId, id, name, muscleGroup, null, targetSets, targetReps, restSeconds, false, gifUrl,
                    null, null, null, null, null, false);
        }

        /**
         * 构造未包含替换动作的兼容模板动作。
         *
         * @param planId 模板主键
         * @param id 动作主键
         * @param name 动作名称
         * @param muscleGroup 辅助肌群
         * @param targetSets 目标组数
         * @param targetReps 目标次数
         * @param restSeconds 预设休息秒数
         * @param progressiveOverloadEnabled 是否开启渐进超负荷
         * @param gifUrl 动作动画地址
         */
        public PlanExercise(Long planId, Long id, String name, String muscleGroup, Integer targetSets,
                Integer targetReps, Integer restSeconds, Boolean progressiveOverloadEnabled, String gifUrl) {
            this(planId, id, name, muscleGroup, null, targetSets, targetReps, restSeconds,
                    progressiveOverloadEnabled, gifUrl, null, null, null, null, null, false);
        }

        /**
         * 构造没有动画字段的兼容模板动作。
         *
         * @return 构造器通过主记录构造器完成字段初始化
         */
        public PlanExercise(Long planId, Long id, String name, String muscleGroup, Integer targetSets,
                Integer targetReps, Integer restSeconds) {
            this(planId, id, name, muscleGroup, null, targetSets, targetReps, restSeconds, false, null,
                    null, null, null, null, null, false);
        }
    }

    /**
     * 描述动作库分页响应。
     *
     * @param data 当前页动作
     * @param total 匹配动作总数
     * @param page 当前页码
     * @param limit 每页大小
     * @param totalPages 总页数
     */
    public record ExercisePageResponse(List<ExerciseResponse> data, Long total, Integer page, Integer limit,
            Integer totalPages) {
    }

    /**
     * 返回一级分类、动作数量和数据库维护的二级分类。
     *
     * @param code 一级分类稳定编码
     * @param name 一级分类显示名称
     * @param exerciseCount 标准动作数量
     * @param subcategories 数据库排序后的二级分类
     */
    public record ExerciseCategory(String code, String name, Long exerciseCount,
            List<ExerciseSubcategory> subcategories) {
    }

    /**
     * 返回一个可供筛选和展示的二级分类定义。
     *
     * @param id 二级分类主键
     * @param code 二级分类稳定编码
     * @param name 二级分类显示名称
     */
    public record ExerciseSubcategory(Long id, String code, String name) {
    }

    /**
     * 返回动作详情和该动作关联的动态二级分类。
     *
     * <p>不再按胸、背、肩等部位提供固定字段，新增分类无需修改响应模型。</p>
     * 分类集合按数据库 sort_order 排列，同一动作可关联多个标签。</p>
     */
    public record ExerciseResponse(Long id, String sourceExerciseId, String name, String nameEn,
            String categoryCode, String categoryName, List<ExerciseSubcategory> subcategories,
            String muscleGroup, String equipment, String targetName, String instructions,
            String gifUrl, String attribution) {
    }

    /**
     * 描述训练历史列表中的一条摘要。
     *
     * @param id 会话主键
     * @param name 训练名称
     * @param completedAt 完成时间
     * @param durationMinutes 持续分钟数
     * @param exerciseCount 去重动作数
     * @param completedSetCount 完成组数
     * @param totalVolumeKg 总容量
     */
    public record WorkoutHistoryItem(Long id, String name, LocalDateTime completedAt, Integer durationMinutes,
            Integer exerciseCount, Integer completedSetCount, BigDecimal totalVolumeKg) {

        /**
         * 补充前端历史卡片需要的日期字段。
         *
         * @return 包含日期日号和月份的格式化响应
         */
        public FormattedWorkout withFormattedDate() {
            LocalDate date = completedAt.toLocalDate();
            return new FormattedWorkout(id, name, completedAt, durationMinutes, exerciseCount, completedSetCount, totalVolumeKg,
                    DAY_FORMAT.format(date), MONTH_FORMAT.format(date));
        }
    }

    /**
     * 描述训练历史完整详情。
     *
     * @param id 会话主键
     * @param name 训练名称
     * @param completedAt 完成时间
     * @param durationMinutes 持续分钟数
     * @param totalVolumeKg 总容量
     * @param exercises 有序动作和组明细
     */
    public record WorkoutHistoryDetail(Long id, String name, LocalDateTime completedAt, Integer durationMinutes,
            BigDecimal totalVolumeKg, List<WorkoutHistoryExercise> exercises) {
    }

    /**
     * 描述历史训练中的一个动作。
     *
     * @param id 会话动作主键
     * @param exerciseId 动作库主键
     * @param name 动作名称快照
     * @param gifUrl 动作库 GIF 地址
     * @param exerciseOrder 动作顺序
     * @param sets 有序组记录
     */
    public record WorkoutHistoryExercise(Long id, Long exerciseId, String name, String gifUrl, Integer exerciseOrder,
            List<WorkoutSetDetail> sets) {
    }

    /**
     * 描述历史训练中的一个组记录。
     *
     * @param setNumber 组序号
     * @param setType 训练组用途
     * @param weightKg 重量
     * @param repetitions 次数
     * @param restDurationSeconds 实际组间歇
     * @param repsInReserve 完成后估计还能标准完成的次数
     * @param status 组状态
     * @param completedAt 完成时间
     */
    public record WorkoutSetDetail(Integer setNumber, WorkoutSetType setType, BigDecimal weightKg, Integer repetitions,
            Integer restDurationSeconds, Integer repsInReserve, String status, LocalDateTime completedAt) {
    }

    /**
     * 描述动作的最近一次完成表现。
     *
     * @param exerciseId 动作主键
     * @param completedAt 最近训练完成时间
     * @param sets 该次训练的所有完成组
     * @param recentPerformances 从近到远排列的两次完整表现
     */
    public record LatestExercisePerformance(Long exerciseId, LocalDateTime completedAt,
            List<LatestPerformanceSet> sets, List<ExercisePerformanceSession> recentPerformances) {

        /**
         * 兼容只提供最近一次表现的内部调用。
         *
         * @param exerciseId 动作主键
         * @param completedAt 最近训练完成时间
         * @param sets 最近一次完成组
         */
        public LatestExercisePerformance(Long exerciseId, LocalDateTime completedAt,
                List<LatestPerformanceSet> sets) {
            this(exerciseId, completedAt, sets, List.of(new ExercisePerformanceSession(completedAt, sets)));
        }
    }

    /**
     * 描述同一动作在一次训练中的完整完成组，用于连续表现判断。
     *
     * @param completedAt 该次训练完成时间
     * @param sets 该次训练的有序完成组
     */
    public record ExercisePerformanceSession(LocalDateTime completedAt, List<LatestPerformanceSet> sets) {
    }

    /**
     * 描述最近表现中的一个完成组。
     *
     * @param setNumber 组序号
     * @param weightKg 重量
     * @param repetitions 次数
     * @param repsInReserve 完成后估计还能标准完成的次数
     * @param setType 训练组用途
     * @param restDurationSeconds 该组完成后的实际间歇秒数
     */
    public record LatestPerformanceSet(Integer setNumber, BigDecimal weightKg, Integer repetitions,
            Integer repsInReserve, WorkoutSetType setType, Integer restDurationSeconds) {

        /**
         * 兼容尚未包含组间歇的内部调用。
         *
         * @param setNumber 组序号
         * @param weightKg 重量
         * @param repetitions 次数
         * @param repsInReserve 完成后估计还能标准完成的次数
         * @param setType 训练组用途
         */
        public LatestPerformanceSet(Integer setNumber, BigDecimal weightKg, Integer repetitions,
                Integer repsInReserve, WorkoutSetType setType) {
            this(setNumber, weightKg, repetitions, repsInReserve, setType, null);
        }

        /**
         * 兼容已包含 RIR 但尚无组类型的内部调用。
         *
         * @param setNumber 组序号
         * @param weightKg 重量
         * @param repetitions 次数
         * @param repsInReserve 完成后估计还能标准完成的次数
         */
        public LatestPerformanceSet(Integer setNumber, BigDecimal weightKg, Integer repetitions,
                Integer repsInReserve) {
            this(setNumber, weightKg, repetitions, repsInReserve, WorkoutSetType.STANDARD, null);
        }

        /**
         * 兼容没有 RIR 的历史数据和内部调用。
         *
         * @param setNumber 组序号
         * @param weightKg 重量
         * @param repetitions 次数
         */
        public LatestPerformanceSet(Integer setNumber, BigDecimal weightKg, Integer repetitions) {
            this(setNumber, weightKg, repetitions, null, WorkoutSetType.STANDARD, null);
        }
    }

    /**
     * 描述一个动作的成长页完整响应。
     *
     * @param exerciseId 动作库主键
     * @param name 动作中文名称
     * @param muscleGroup 辅助肌群
     * @param equipment 所需器械
     * @param targetName 主要目标肌群
     * @param gifUrl 动作演示地址
     * @param attribution 演示媒体署名
     * @param sessions 最近完成训练，按完成时间从近到远排列
     */
    public record ExerciseProgressResponse(Long exerciseId, String name, String muscleGroup,
            String equipment, String targetName, String gifUrl, String attribution,
            List<ExerciseProgressSession> sessions) {
    }

    /**
     * 描述成长历史中的一次完成训练。
     *
     * @param sessionId 训练会话主键
     * @param workoutName 训练名称快照
     * @param completedAt 完成时间
     * @param sets 该动作的全部完成组
     */
    public record ExerciseProgressSession(Long sessionId, String workoutName, LocalDateTime completedAt,
            List<LatestPerformanceSet> sets) {
    }

    /**
     * 描述为看板格式化后的最近训练。
     *
     * <p>保留原始完成时间，同时提供日号和月份字符串，避免客户端重复日期格式逻辑。</p>
     */
    public record FormattedWorkout(Long id, String name, LocalDateTime completedAt, Integer durationMinutes,
            Integer exerciseCount, Integer completedSetCount, BigDecimal totalVolumeKg, String dateDay, String dateMonth) {
    }
}

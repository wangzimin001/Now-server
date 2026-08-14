package com.wangzimin.now.service;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FitnessQueryService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("M月");

    private final JdbcClient jdbcClient;

    public FitnessQueryService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public DashboardResponse dashboard() {
        return dashboard(null);
    }

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
                .orElse(new PlanRow(0L, "暂无训练模板", 0, 3, 0));

        int completedThisWeek = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM workout_session
                        WHERE status = 'COMPLETED'
                          AND ((:userId IS NULL AND owner_user_id IS NULL) OR owner_user_id = :userId)
                          AND ended_at >= DATE_SUB(CURRENT_DATE, INTERVAL WEEKDAY(CURRENT_DATE) DAY)
                        """)
                .param("userId", userId, Types.BIGINT)
                .query(Integer.class)
                .single();

        WorkoutHistoryItem recentWorkout = history(userId).stream()
                .findFirst()
                .orElse(new WorkoutHistoryItem(0L, "暂无训练", LocalDateTime.now(), 0, 0, 0, BigDecimal.ZERO));

        return new DashboardResponse(
                new TodayPlan(plan.id(), plan.name(), plan.exerciseCount(), plan.estimatedMinutes(), 0),
                List.of(
                        new Metric("calories", "0", "活动消耗", "450 kcal"),
                        new Metric("water", "0", "饮水", "2,000 ml"),
                        new Metric("sleep", "--", "睡眠", "待记录")),
                new WeekSummary(completedThisWeek, plan.weeklyTarget(), buildWeekDays()),
                recentWorkout.withFormattedDate());
    }

    public List<WorkoutPlanResponse> workoutPlans() {
        return workoutPlans(null);
    }

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
                            WHERE status = 'COMPLETED'
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
                .query(WorkoutPlanRow.class)
                .list();

        List<PlanExercise> planExercises = jdbcClient.sql("""
                        SELECT pe.plan_id AS planId, e.id, e.name, e.muscle_group AS muscleGroup,
                               pe.target_sets AS targetSets, pe.target_reps AS targetReps,
                               pe.rest_seconds AS restSeconds,
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
                               ) AS gifUrl
                        FROM plan_exercise pe
                        JOIN exercise e ON e.id = pe.exercise_id
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

    public WorkoutPlanResponse workoutPlan(Long planId) {
        return workoutPlan(planId, null);
    }

    public WorkoutPlanResponse workoutPlan(Long planId, Long userId) {
        return workoutPlans(userId).stream()
                .filter(plan -> plan.id().equals(planId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "训练模板不存在"));
    }

    public ExercisePageResponse exercises(String category, String chestRegion, String backRegion,
            String shoulderRegion, String thighRegion, String waistRegion, String upperArmRegion,
            String calfRegion, String forearmRegion, String keyword,
            Integer page, Integer limit) {
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safeLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
        int offset = (safePage - 1) * safeLimit;
        String categoryFilter = category == null ? "" : category.trim();
        // 二级分类只对胸部生效；其他一级分类即使收到残留参数也不得被意外筛空。
        String chestRegionFilter = "chest".equals(categoryFilter) && chestRegion != null ? chestRegion.trim() : "";
        String backRegionFilter = "back".equals(categoryFilter) && backRegion != null ? backRegion.trim() : "";
        // 肩部二级条件只在肩部一级分类下生效，避免页面切换后残留条件误筛其他分类。
        String shoulderRegionFilter = "shoulders".equals(categoryFilter) && shoulderRegion != null
                ? shoulderRegion.trim() : "";
        // 大腿筛选只作用于 upper legs，确保切换一级分类时残留参数不会影响其他动作。
        String thighRegionFilter = "upper legs".equals(categoryFilter) && thighRegion != null
                ? thighRegion.trim() : "";
        // 腰腹二级条件只在 waist 下生效，避免隐藏筛选条件泄漏到其他一级分类。
        String waistRegionFilter = "waist".equals(categoryFilter) && waistRegion != null
                ? waistRegion.trim() : "";
        // 上臂二级条件只在 upper arms 分类下生效。
        String upperArmRegionFilter = "upper arms".equals(categoryFilter) && upperArmRegion != null
                ? upperArmRegion.trim() : "";
        String calfRegionFilter = "lower legs".equals(categoryFilter) && calfRegion != null ? calfRegion.trim() : "";
        String forearmRegionFilter = "lower arms".equals(categoryFilter) && forearmRegion != null
                ? forearmRegion.trim() : "";
        String keywordFilter = keyword == null ? "" : keyword.trim();

        String whereSql = """
                FROM exercise
                WHERE source = 'exercise-dataset'
                  AND (:category = '' OR category_code = :category)
                  AND (:chestRegion = '' OR JSON_CONTAINS(chest_regions, JSON_QUOTE(:chestRegion)))
                  AND (:backRegion = '' OR JSON_CONTAINS(back_regions, JSON_QUOTE(:backRegion)))
                  AND (:shoulderRegion = '' OR JSON_CONTAINS(shoulder_regions, JSON_QUOTE(:shoulderRegion)))
                  AND (:thighRegion = '' OR JSON_CONTAINS(thigh_regions, JSON_QUOTE(:thighRegion)))
                  AND (:waistRegion = '' OR JSON_CONTAINS(waist_regions, JSON_QUOTE(:waistRegion)))
                  AND (:upperArmRegion = '' OR JSON_CONTAINS(upper_arm_regions, JSON_QUOTE(:upperArmRegion)))
                  AND (:calfRegion = '' OR JSON_CONTAINS(calf_regions, JSON_QUOTE(:calfRegion)))
                  AND (:forearmRegion = '' OR JSON_CONTAINS(forearm_regions, JSON_QUOTE(:forearmRegion)))
                  AND (
                    :keyword = ''
                    OR name LIKE CONCAT('%', :keyword, '%')
                    OR name_en LIKE CONCAT('%', :keyword, '%')
                    OR equipment LIKE CONCAT('%', :keyword, '%')
                    OR target_name LIKE CONCAT('%', :keyword, '%')
                  )
                """;

        long total = jdbcClient.sql("SELECT COUNT(*) " + whereSql)
                .param("category", categoryFilter)
                .param("chestRegion", chestRegionFilter)
                .param("backRegion", backRegionFilter)
                .param("shoulderRegion", shoulderRegionFilter)
                .param("thighRegion", thighRegionFilter)
                .param("waistRegion", waistRegionFilter)
                .param("upperArmRegion", upperArmRegionFilter)
                .param("calfRegion", calfRegionFilter)
                .param("forearmRegion", forearmRegionFilter)
                .param("keyword", keywordFilter)
                .query(Long.class)
                .single();

        List<ExerciseResponse> data = jdbcClient.sql("""
                        SELECT id, source_exercise_id AS sourceExerciseId, name, name_en AS nameEn,
                               category_code AS categoryCode, category_name AS categoryName,
                               chest_regions AS chestRegions,
                               back_regions AS backRegions,
                               shoulder_regions AS shoulderRegions,
                               thigh_regions AS thighRegions,
                               waist_regions AS waistRegions,
                               upper_arm_regions AS upperArmRegions,
                               calf_regions AS calfRegions,
                               forearm_regions AS forearmRegions,
                               muscle_group AS muscleGroup, equipment, target_name AS targetName,
                               instructions, gif_url AS gifUrl, attribution
                        """ + whereSql + """
                        ORDER BY category_sort, popularity_rank, id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("category", categoryFilter)
                .param("chestRegion", chestRegionFilter)
                .param("backRegion", backRegionFilter)
                .param("shoulderRegion", shoulderRegionFilter)
                .param("thighRegion", thighRegionFilter)
                .param("waistRegion", waistRegionFilter)
                .param("upperArmRegion", upperArmRegionFilter)
                .param("calfRegion", calfRegionFilter)
                .param("forearmRegion", forearmRegionFilter)
                .param("keyword", keywordFilter)
                .param("limit", safeLimit)
                .param("offset", offset)
                .query((resultSet, rowNumber) -> new ExerciseResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("sourceExerciseId"),
                        resultSet.getString("name"),
                        resultSet.getString("nameEn"),
                        resultSet.getString("categoryCode"),
                        resultSet.getString("categoryName"),
                        parseChestRegions(resultSet.getString("chestRegions")),
                        parseBackRegions(resultSet.getString("backRegions")),
                        parseShoulderRegions(resultSet.getString("shoulderRegions")),
                        parseThighRegions(resultSet.getString("thighRegions")),
                        parseWaistRegions(resultSet.getString("waistRegions")),
                        parseUpperArmRegions(resultSet.getString("upperArmRegions")),
                        parseCalfRegions(resultSet.getString("calfRegions")),
                        parseForearmRegions(resultSet.getString("forearmRegions")),
                        resultSet.getString("muscleGroup"),
                        resultSet.getString("equipment"),
                        resultSet.getString("targetName"),
                        resultSet.getString("instructions"),
                        resultSet.getString("gifUrl"),
                        resultSet.getString("attribution")))
                .list();

        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeLimit);
        return new ExercisePageResponse(data, total, safePage, safeLimit, totalPages);
    }

    // 旧调用默认没有肩束条件，避免既有调用方因新增可选筛选参数而中断。
    public ExercisePageResponse exercises(String category, String chestRegion, String backRegion, String keyword,
            Integer page, Integer limit) {
        return exercises(category, chestRegion, backRegion, null, null, null, null, null, null, keyword, page, limit);
    }

    private static List<String> parseChestRegions(String json) {
        // 数据库只允许三个固定中文值；轻量解析避免把 JDBC JSON 驱动类型暴露到 API。
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of("上胸", "中胸", "下胸").stream()
                .filter(region -> json.contains('"' + region + '"'))
                .toList();
    }

    private static List<String> parseBackRegions(String json) {
        // 返回顺序与前端页签一致，双标签动作在不同筛选中仍保持单条数据。
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of("背阔肌", "上背部", "斜方肌", "下背部").stream()
                .filter(region -> json.contains('"' + region + '"'))
                .toList();
    }

    private static List<String> parseShoulderRegions(String json) {
        // 固定输出前中后束顺序，双标签动作在不同筛选页签中均只返回一条记录。
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of("前束", "中束", "后束").stream()
                .filter(region -> json.contains('"' + region + '"'))
                .toList();
    }

    private static List<String> parseThighRegions(String json) {
        // 顺序与前端页签一致，双标签动作仍由一条动作记录表达。
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of("股四头肌", "腘绳肌", "臀肌", "内收肌", "外展肌").stream()
                .filter(region -> json.contains('"' + region + '"'))
                .toList();
    }

    private static List<String> parseWaistRegions(String json) {
        // 上腹、下腹是训练侧重；固定顺序便于前端稳定展示多标签。
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of("上腹", "下腹", "腹斜肌", "核心稳定", "下背部").stream()
                .filter(region -> json.contains('"' + region + '"'))
                .toList();
    }

    private static List<String> parseUpperArmRegions(String json) {
        // 二头内外侧是长短头侧重标签，按页签顺序稳定输出。
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return List.of("二头内侧", "二头外侧", "三头").stream()
                .filter(region -> json.contains('"' + region + '"'))
                .toList();
    }

    private static List<String> parseCalfRegions(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of("腓肠肌", "比目鱼肌", "胫骨前肌", "踝部稳定").stream()
                .filter(region -> json.contains('"' + region + '"')).toList();
    }

    private static List<String> parseForearmRegions(String json) {
        if (json == null || json.isBlank()) return List.of();
        return List.of("腕屈肌", "腕伸肌", "旋前旋后", "握力").stream()
                .filter(region -> json.contains('"' + region + '"')).toList();
    }

    public List<ExerciseCategory> exerciseCategories() {
        return jdbcClient.sql("""
                        SELECT category_code AS code, category_name AS name, COUNT(*) AS exerciseCount
                        FROM exercise
                        WHERE source = 'exercise-dataset'
                        GROUP BY category_code, category_name
                        ORDER BY MIN(category_sort)
                        """)
                .query(ExerciseCategory.class)
                .list();
    }

    public List<WorkoutHistoryItem> history() {
        return history(null);
    }

    public List<WorkoutHistoryItem> history(Long userId) {
        return jdbcClient.sql("""
                        SELECT ws.id, ws.name_snapshot AS name, ws.ended_at AS completedAt,
                               ws.duration_minutes AS durationMinutes,
                               COUNT(DISTINCT se.id) AS exerciseCount,
                               SUM(CASE WHEN sr.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completedSetCount,
                               ws.total_volume_kg AS totalVolumeKg
                        FROM workout_session ws
                        LEFT JOIN session_exercise se ON se.session_id = ws.id
                        LEFT JOIN set_record sr ON sr.session_exercise_id = se.id
                        WHERE ws.status = 'COMPLETED'
                          AND ((:userId IS NULL AND ws.owner_user_id IS NULL) OR ws.owner_user_id = :userId)
                        GROUP BY ws.id, ws.name_snapshot, ws.ended_at, ws.duration_minutes, ws.total_volume_kg
                        ORDER BY ws.ended_at DESC
                        LIMIT 200
                        """)
                .param("userId", userId, Types.BIGINT)
                .query(WorkoutHistoryItem.class)
                .list();
    }

    public WorkoutHistoryDetail historyDetail(Long sessionId) {
        return historyDetail(sessionId, null);
    }

    public WorkoutHistoryDetail historyDetail(Long sessionId, Long userId) {
        WorkoutHistoryDetailRow session = jdbcClient.sql("""
                        SELECT id, name_snapshot AS name, ended_at AS completedAt,
                               duration_minutes AS durationMinutes, total_volume_kg AS totalVolumeKg
                        FROM workout_session
                        WHERE id = :sessionId AND status = 'COMPLETED'
                          AND ((:userId IS NULL AND owner_user_id IS NULL) OR owner_user_id = :userId)
                        """)
                .param("sessionId", sessionId)
                .param("userId", userId, Types.BIGINT)
                .query(WorkoutHistoryDetailRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "训练记录不存在"));

        List<WorkoutHistoryExercise> exercises = jdbcClient.sql("""
                        SELECT se.id, se.exercise_name_snapshot AS name, se.exercise_order AS exerciseOrder,
                               sr.set_number AS setNumber, sr.weight_kg AS weightKg, sr.repetitions,
                               sr.rest_duration_seconds AS restDurationSeconds, sr.status, sr.completed_at AS completedAt
                        FROM session_exercise se
                        LEFT JOIN set_record sr ON sr.session_exercise_id = se.id
                        WHERE se.session_id = :sessionId
                        ORDER BY se.exercise_order, sr.set_number
                        """)
                .param("sessionId", sessionId)
                .query((resultSet, rowNumber) -> new HistorySetRow(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        resultSet.getInt("exerciseOrder"),
                        resultSet.getObject("setNumber", Integer.class),
                        resultSet.getBigDecimal("weightKg"),
                        resultSet.getObject("repetitions", Integer.class),
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
                            .map(row -> new WorkoutSetDetail(row.setNumber(), row.weightKg(), row.repetitions(),
                                    row.restDurationSeconds(), row.status(), row.completedAt()))
                            .toList();
                    return new WorkoutHistoryExercise(first.id(), first.name(), first.exerciseOrder(), sets);
                })
                .toList();

        return new WorkoutHistoryDetail(session.id(), session.name(), session.completedAt(), session.durationMinutes(),
                session.totalVolumeKg(), exercises);
    }

    private List<WeekDay> buildWeekDays() {
        LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
        String[] labels = { "一", "二", "三", "四", "五", "六", "日" };
        List<WeekDay> days = new ArrayList<>();
        for (int index = 0; index < labels.length; index++) {
            LocalDate date = monday.plusDays(index);
            String state = date.equals(LocalDate.now()) ? "today" : index == 0 || index == 2 ? "done" : "rest";
            days.add(new WeekDay(labels[index], String.valueOf(date.getDayOfMonth()), state));
        }
        return days;
    }

    private record PlanRow(Long id, String name, Integer estimatedMinutes, Integer weeklyTarget,
            Integer exerciseCount) {
    }

    private record WorkoutHistoryDetailRow(Long id, String name, LocalDateTime completedAt,
            Integer durationMinutes, BigDecimal totalVolumeKg) {
    }

    private record HistorySetRow(Long id, String name, Integer exerciseOrder, Integer setNumber,
            BigDecimal weightKg, Integer repetitions, Integer restDurationSeconds, String status,
            LocalDateTime completedAt) {
    }

    record WorkoutPlanRow(Long id, String name, String description, Integer estimatedMinutes,
            Long usageCount, LocalDateTime lastUsedAt, Boolean ownedByCurrentUser) {

        WorkoutPlanRow(Long id, String name, String description, Integer estimatedMinutes,
                Long usageCount, LocalDateTime lastUsedAt) {
            this(id, name, description, estimatedMinutes, usageCount, lastUsedAt, false);
        }
    }

    public record DashboardResponse(TodayPlan todayPlan, List<Metric> metrics, WeekSummary week,
            FormattedWorkout recentWorkout) {
    }

    public record TodayPlan(Long id, String name, Integer exerciseCount, Integer estimatedMinutes,
            Integer completionPercent) {
    }

    public record Metric(String key, String value, String label, String target) {
    }

    public record WeekSummary(Integer completed, Integer target, List<WeekDay> days) {
    }

    public record WeekDay(String label, String date, String state) {
    }

    public record WorkoutPlanResponse(Long id, String name, String description, Integer estimatedMinutes,
            Long usageCount, LocalDateTime lastUsedAt, Boolean ownedByCurrentUser, List<PlanExercise> exercises) {

        public WorkoutPlanResponse(Long id, String name, String description, Integer estimatedMinutes,
                List<PlanExercise> exercises) {
            this(id, name, description, estimatedMinutes, 0L, null, false, exercises);
        }
    }

    public record PlanExercise(Long planId, Long id, String name, String muscleGroup, Integer targetSets,
            Integer targetReps, Integer restSeconds, String gifUrl) {

        public PlanExercise(Long planId, Long id, String name, String muscleGroup, Integer targetSets,
                Integer targetReps, Integer restSeconds) {
            this(planId, id, name, muscleGroup, targetSets, targetReps, restSeconds, null);
        }
    }

    public record ExercisePageResponse(List<ExerciseResponse> data, Long total, Integer page, Integer limit,
            Integer totalPages) {
    }

    public record ExerciseCategory(String code, String name, Long exerciseCount) {
    }

    public record ExerciseResponse(Long id, String sourceExerciseId, String name, String nameEn,
            String categoryCode, String categoryName, List<String> chestRegions, List<String> backRegions,
            List<String> shoulderRegions, List<String> thighRegions, List<String> waistRegions,
            List<String> upperArmRegions, List<String> calfRegions, List<String> forearmRegions,
            String muscleGroup, String equipment, String targetName,
            String instructions, String gifUrl, String attribution) {

        // 旧构造签名默认肩束为空，兼容不关心肩部标签的既有测试夹具。
        public ExerciseResponse(Long id, String sourceExerciseId, String name, String nameEn,
                String categoryCode, String categoryName, List<String> chestRegions, List<String> backRegions,
                String muscleGroup, String equipment, String targetName,
                String instructions, String gifUrl, String attribution) {
            this(id, sourceExerciseId, name, nameEn, categoryCode, categoryName, chestRegions, backRegions,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), muscleGroup, equipment, targetName, instructions, gifUrl,
                    attribution);
        }
    }

    public record WorkoutHistoryItem(Long id, String name, LocalDateTime completedAt, Integer durationMinutes,
            Integer exerciseCount, Integer completedSetCount, BigDecimal totalVolumeKg) {

        public FormattedWorkout withFormattedDate() {
            LocalDate date = completedAt.toLocalDate();
            return new FormattedWorkout(id, name, completedAt, durationMinutes, exerciseCount, completedSetCount, totalVolumeKg,
                    DAY_FORMAT.format(date), MONTH_FORMAT.format(date));
        }
    }

    public record WorkoutHistoryDetail(Long id, String name, LocalDateTime completedAt, Integer durationMinutes,
            BigDecimal totalVolumeKg, List<WorkoutHistoryExercise> exercises) {
    }

    public record WorkoutHistoryExercise(Long id, String name, Integer exerciseOrder,
            List<WorkoutSetDetail> sets) {
    }

    public record WorkoutSetDetail(Integer setNumber, BigDecimal weightKg, Integer repetitions,
            Integer restDurationSeconds, String status, LocalDateTime completedAt) {
    }

    public record FormattedWorkout(Long id, String name, LocalDateTime completedAt, Integer durationMinutes,
            Integer exerciseCount, Integer completedSetCount, BigDecimal totalVolumeKg, String dateDay, String dateMonth) {
    }
}

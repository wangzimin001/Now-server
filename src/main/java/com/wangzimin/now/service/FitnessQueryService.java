package com.wangzimin.now.service;

import java.math.BigDecimal;
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
        PlanRow plan = jdbcClient.sql("""
                        SELECT wp.id, wp.name, wp.estimated_minutes AS estimatedMinutes,
                               wp.weekly_target AS weeklyTarget, COUNT(pe.id) AS exerciseCount
                        FROM workout_plan wp
                        LEFT JOIN plan_exercise pe ON pe.plan_id = wp.id
                        WHERE wp.is_active = TRUE
                        GROUP BY wp.id, wp.name, wp.estimated_minutes, wp.weekly_target
                        ORDER BY wp.id
                        LIMIT 1
                        """)
                .query(PlanRow.class)
                .optional()
                .orElse(new PlanRow(0L, "暂无训练模板", 0, 3, 0));

        int completedThisWeek = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM workout_session
                        WHERE status = 'COMPLETED'
                          AND ended_at >= DATE_SUB(CURRENT_DATE, INTERVAL WEEKDAY(CURRENT_DATE) DAY)
                        """)
                .query(Integer.class)
                .single();

        WorkoutHistoryItem recentWorkout = history().stream()
                .findFirst()
                .orElse(new WorkoutHistoryItem(0L, "暂无训练", LocalDateTime.now(), 0, 0, BigDecimal.ZERO));

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
        List<WorkoutPlanRow> plans = jdbcClient.sql("""
                        SELECT wp.id, wp.name, wp.description,
                               wp.estimated_minutes AS estimatedMinutes, wp.level,
                               COALESCE(plan_usage.usage_count, 0) AS usageCount,
                               plan_usage.last_used_at AS lastUsedAt
                        FROM workout_plan wp
                        LEFT JOIN (
                            SELECT plan_id, COUNT(*) AS usage_count, MAX(ended_at) AS last_used_at
                            FROM workout_session
                            WHERE status = 'COMPLETED'
                            GROUP BY plan_id
                        ) plan_usage ON plan_usage.plan_id = wp.id
                        WHERE wp.is_active = TRUE
                        ORDER BY wp.id
                        """)
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
                        ORDER BY pe.plan_id, pe.exercise_order
                        """)
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
                        plan.level(),
                        plan.usageCount(),
                        plan.lastUsedAt(),
                        exercisesByPlan.getOrDefault(plan.id(), List.of())))
                .toList();
    }

    public WorkoutPlanResponse workoutPlan(Long planId) {
        return workoutPlans().stream()
                .filter(plan -> plan.id().equals(planId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "训练模板不存在"));
    }

    public ExercisePageResponse exercises(String category, String keyword, Integer page, Integer limit) {
        int safePage = page == null ? 1 : Math.max(page, 1);
        int safeLimit = limit == null ? 20 : Math.min(Math.max(limit, 1), 50);
        int offset = (safePage - 1) * safeLimit;
        String categoryFilter = category == null ? "" : category.trim();
        String keywordFilter = keyword == null ? "" : keyword.trim();

        String whereSql = """
                FROM exercise
                WHERE source = 'exercise-dataset'
                  AND (:category = '' OR category_code = :category)
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
                .param("keyword", keywordFilter)
                .query(Long.class)
                .single();

        List<ExerciseResponse> data = jdbcClient.sql("""
                        SELECT id, source_exercise_id AS sourceExerciseId, name, name_en AS nameEn,
                               category_code AS categoryCode, category_name AS categoryName,
                               muscle_group AS muscleGroup, equipment, target_name AS targetName,
                               instructions, gif_url AS gifUrl, attribution
                        """ + whereSql + """
                        ORDER BY category_sort, popularity_rank, id
                        LIMIT :limit OFFSET :offset
                        """)
                .param("category", categoryFilter)
                .param("keyword", keywordFilter)
                .param("limit", safeLimit)
                .param("offset", offset)
                .query(ExerciseResponse.class)
                .list();

        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeLimit);
        return new ExercisePageResponse(data, total, safePage, safeLimit, totalPages);
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
        return jdbcClient.sql("""
                        SELECT ws.id, ws.name_snapshot AS name, ws.ended_at AS completedAt,
                               ws.duration_minutes AS durationMinutes,
                               COUNT(se.id) AS exerciseCount,
                               ws.total_volume_kg AS totalVolumeKg
                        FROM workout_session ws
                        LEFT JOIN session_exercise se ON se.session_id = ws.id
                        WHERE ws.status = 'COMPLETED'
                        GROUP BY ws.id, ws.name_snapshot, ws.ended_at, ws.duration_minutes, ws.total_volume_kg
                        ORDER BY ws.ended_at DESC
                        LIMIT 20
                        """)
                .query(WorkoutHistoryItem.class)
                .list();
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

    record WorkoutPlanRow(Long id, String name, String description, Integer estimatedMinutes, String level,
            Long usageCount, LocalDateTime lastUsedAt) {
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
            String level, Long usageCount, LocalDateTime lastUsedAt, List<PlanExercise> exercises) {

        public WorkoutPlanResponse(Long id, String name, String description, Integer estimatedMinutes,
                String level, List<PlanExercise> exercises) {
            this(id, name, description, estimatedMinutes, level, 0L, null, exercises);
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
            String categoryCode, String categoryName, String muscleGroup, String equipment, String targetName,
            String instructions, String gifUrl, String attribution) {
    }

    public record WorkoutHistoryItem(Long id, String name, LocalDateTime completedAt, Integer durationMinutes,
            Integer exerciseCount, BigDecimal totalVolumeKg) {

        public FormattedWorkout withFormattedDate() {
            LocalDate date = completedAt.toLocalDate();
            return new FormattedWorkout(id, name, completedAt, durationMinutes, exerciseCount, totalVolumeKg,
                    DAY_FORMAT.format(date), MONTH_FORMAT.format(date));
        }
    }

    public record FormattedWorkout(Long id, String name, LocalDateTime completedAt, Integer durationMinutes,
            Integer exerciseCount, BigDecimal totalVolumeKg, String dateDay, String dateMonth) {
    }
}

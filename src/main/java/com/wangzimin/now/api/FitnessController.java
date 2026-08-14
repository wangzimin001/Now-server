package com.wangzimin.now.api;

import java.util.List;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.service.FitnessQueryService.DashboardResponse;
import com.wangzimin.now.service.FitnessQueryService.ExerciseCategory;
import com.wangzimin.now.service.FitnessQueryService.ExercisePageResponse;
import com.wangzimin.now.service.FitnessQueryService.WorkoutHistoryItem;
import com.wangzimin.now.service.FitnessQueryService.WorkoutHistoryDetail;
import com.wangzimin.now.service.FitnessQueryService.LatestExercisePerformance;
import com.wangzimin.now.service.FitnessQueryService.WorkoutPlanResponse;
import com.wangzimin.now.service.WorkoutService;
import com.wangzimin.now.service.WorkoutService.WorkoutCompletionRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutCompletionResponse;
import com.wangzimin.now.service.WorkoutService.WorkoutPlanRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FitnessController {

    private final FitnessQueryService fitnessQueryService;
    private final WorkoutService workoutService;

    public FitnessController(FitnessQueryService fitnessQueryService, WorkoutService workoutService) {
        this.fitnessQueryService = fitnessQueryService;
        this.workoutService = workoutService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return fitnessQueryService.dashboard(userId(jwt));
    }

    @GetMapping("/workout-plans")
    public List<WorkoutPlanResponse> workoutPlans(@AuthenticationPrincipal Jwt jwt) {
        return fitnessQueryService.workoutPlans(userId(jwt));
    }

    @PostMapping("/workout-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutPlanResponse createWorkoutPlan(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutPlanRequest request) {
        Long userId = userId(jwt);
        Long planId = workoutService.createPlan(userId, request);
        return fitnessQueryService.workoutPlan(planId, userId);
    }

    @PutMapping("/workout-plans/{planId}")
    public WorkoutPlanResponse updateWorkoutPlan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @Valid @RequestBody WorkoutPlanRequest request) {
        Long userId = userId(jwt);
        workoutService.updatePlan(userId, planId, request);
        return fitnessQueryService.workoutPlan(planId, userId);
    }

    @DeleteMapping("/workout-plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkoutPlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        workoutService.deletePlan(userId(jwt), planId);
    }

    @GetMapping("/exercises")
    public ExercisePageResponse exercises(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String chestRegion,
            @RequestParam(required = false) String backRegion,
            @RequestParam(required = false) String shoulderRegion,
            @RequestParam(required = false) String thighRegion,
            @RequestParam(required = false) String waistRegion,
            @RequestParam(required = false) String upperArmRegion,
            @RequestParam(required = false) String calfRegion,
            @RequestParam(required = false) String forearmRegion,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return fitnessQueryService.exercises(category, chestRegion, backRegion, shoulderRegion, thighRegion,
                waistRegion, upperArmRegion, calfRegion, forearmRegion, keyword, page, limit);
    }

    // 保留旧的直接调用签名，兼容现有单元测试和仓库内调用；HTTP 仍只暴露上面的完整参数入口。
    ExercisePageResponse exercises(String category, String chestRegion, String backRegion,
            String keyword, Integer page, Integer limit) {
        return fitnessQueryService.exercises(category, chestRegion, backRegion, keyword, page, limit);
    }

    @GetMapping("/exercise-categories")
    public List<ExerciseCategory> exerciseCategories() {
        return fitnessQueryService.exerciseCategories();
    }

    @GetMapping("/workouts/history")
    public List<WorkoutHistoryItem> history(@AuthenticationPrincipal Jwt jwt) {
        return fitnessQueryService.history(userId(jwt));
    }

    @GetMapping("/workouts/history/{sessionId}")
    public WorkoutHistoryDetail historyDetail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) {
        return fitnessQueryService.historyDetail(sessionId, userId(jwt));
    }

    @GetMapping("/workouts/latest-performance")
    public List<LatestExercisePerformance> latestExercisePerformances(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam List<Long> exerciseIds) {
        return fitnessQueryService.latestExercisePerformances(userId(jwt), exerciseIds);
    }

    @PostMapping("/workouts")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutCompletionResponse completeWorkout(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutCompletionRequest request) {
        return workoutService.completeWorkout(userId(jwt), request);
    }

    private Long userId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}

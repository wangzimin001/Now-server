package com.wangzimin.now.api;

import java.util.List;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.service.FitnessQueryService.DashboardResponse;
import com.wangzimin.now.service.FitnessQueryService.ExerciseCategory;
import com.wangzimin.now.service.FitnessQueryService.ExercisePageResponse;
import com.wangzimin.now.service.FitnessQueryService.WorkoutHistoryItem;
import com.wangzimin.now.service.FitnessQueryService.WorkoutPlanResponse;
import com.wangzimin.now.service.WorkoutService;
import com.wangzimin.now.service.WorkoutService.WorkoutCompletionRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutCompletionResponse;
import com.wangzimin.now.service.WorkoutService.WorkoutPlanRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
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
    public DashboardResponse dashboard() {
        return fitnessQueryService.dashboard();
    }

    @GetMapping("/workout-plans")
    public List<WorkoutPlanResponse> workoutPlans() {
        return fitnessQueryService.workoutPlans();
    }

    @PostMapping("/workout-plans")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutPlanResponse createWorkoutPlan(@Valid @RequestBody WorkoutPlanRequest request) {
        Long planId = workoutService.createPlan(request);
        return fitnessQueryService.workoutPlan(planId);
    }

    @PutMapping("/workout-plans/{planId}")
    public WorkoutPlanResponse updateWorkoutPlan(
            @PathVariable Long planId,
            @Valid @RequestBody WorkoutPlanRequest request) {
        workoutService.updatePlan(planId, request);
        return fitnessQueryService.workoutPlan(planId);
    }

    @DeleteMapping("/workout-plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkoutPlan(@PathVariable Long planId) {
        workoutService.deletePlan(planId);
    }

    @GetMapping("/exercises")
    public ExercisePageResponse exercises(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer limit) {
        return fitnessQueryService.exercises(category, keyword, page, limit);
    }

    @GetMapping("/exercise-categories")
    public List<ExerciseCategory> exerciseCategories() {
        return fitnessQueryService.exerciseCategories();
    }

    @GetMapping("/workouts/history")
    public List<WorkoutHistoryItem> history() {
        return fitnessQueryService.history();
    }

    @PostMapping("/workouts")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutCompletionResponse completeWorkout(@Valid @RequestBody WorkoutCompletionRequest request) {
        return workoutService.completeWorkout(request);
    }
}

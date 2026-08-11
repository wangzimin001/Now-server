package com.wangzimin.now.api;

import java.util.List;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.service.FitnessQueryService.DashboardResponse;
import com.wangzimin.now.service.FitnessQueryService.ExerciseCategory;
import com.wangzimin.now.service.FitnessQueryService.ExercisePageResponse;
import com.wangzimin.now.service.FitnessQueryService.WorkoutHistoryItem;
import com.wangzimin.now.service.FitnessQueryService.WorkoutPlanResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FitnessController {

    private final FitnessQueryService fitnessQueryService;

    public FitnessController(FitnessQueryService fitnessQueryService) {
        this.fitnessQueryService = fitnessQueryService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return fitnessQueryService.dashboard();
    }

    @GetMapping("/workout-plans")
    public List<WorkoutPlanResponse> workoutPlans() {
        return fitnessQueryService.workoutPlans();
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
}

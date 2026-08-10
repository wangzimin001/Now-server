package com.wangzimin.now.api;

import java.util.List;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.service.FitnessQueryService.DashboardResponse;
import com.wangzimin.now.service.FitnessQueryService.ExerciseResponse;
import com.wangzimin.now.service.FitnessQueryService.WorkoutHistoryItem;
import com.wangzimin.now.service.FitnessQueryService.WorkoutPlanResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public List<ExerciseResponse> exercises() {
        return fitnessQueryService.exercises();
    }

    @GetMapping("/workouts/history")
    public List<WorkoutHistoryItem> history() {
        return fitnessQueryService.history();
    }
}

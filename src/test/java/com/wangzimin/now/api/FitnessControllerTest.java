package com.wangzimin.now.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.service.FitnessQueryService.ExercisePageResponse;
import com.wangzimin.now.service.FitnessQueryService.ExerciseResponse;
import com.wangzimin.now.service.FitnessQueryService.PlanExercise;
import com.wangzimin.now.service.FitnessQueryService.WorkoutPlanResponse;
import com.wangzimin.now.service.WorkoutService;
import com.wangzimin.now.service.WorkoutService.PlanExerciseRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutCompletionRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutCompletionResponse;
import com.wangzimin.now.service.WorkoutService.WorkoutExerciseRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutPlanRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutSetRequest;

import org.junit.jupiter.api.Test;

class FitnessControllerTest {

    @Test
    void delegatesExerciseQueriesToService() {
        FitnessQueryService service = mock(FitnessQueryService.class);
        WorkoutService workoutService = mock(WorkoutService.class);
        List<ExerciseResponse> expected = List.of(
                new ExerciseResponse(100025L, "0025", "杠铃卧推", "barbell bench press", "chest", "胸部",
                        "肱三头肌", "杠铃", "胸肌", "保持肩胛稳定。",
                        "/static/exercises/gifs/0025-EIeI8Vf.gif", "© Gym visual — https://gymvisual.com/"));
        ExercisePageResponse response = new ExercisePageResponse(expected, 1L, 1, 20, 1);
        when(service.exercises("chest", "卧推", 1, 20)).thenReturn(response);

        FitnessController controller = new FitnessController(service, workoutService);

        assertSame(response, controller.exercises("chest", "卧推", 1, 20));
    }

    @Test
    void supportsWorkoutPlanLifecycle() {
        FitnessQueryService queryService = mock(FitnessQueryService.class);
        WorkoutService workoutService = mock(WorkoutService.class);
        WorkoutPlanRequest request = new WorkoutPlanRequest(
                "胸背训练", "推拉组合", 50, "进阶",
                List.of(new PlanExerciseRequest(100025L, 4, 8, 120)));
        WorkoutPlanResponse response = new WorkoutPlanResponse(
                9L, "胸背训练", "推拉组合", 50, "进阶",
                List.of(new PlanExercise(9L, 100025L, "杠铃卧推", "胸部", 4, 8, 120)));
        when(workoutService.createPlan(request)).thenReturn(9L);
        when(queryService.workoutPlan(9L)).thenReturn(response);

        FitnessController controller = new FitnessController(queryService, workoutService);

        assertSame(response, controller.createWorkoutPlan(request));
        assertSame(response, controller.updateWorkoutPlan(9L, request));
        controller.deleteWorkoutPlan(9L);
        verify(workoutService).updatePlan(9L, request);
        verify(workoutService).deletePlan(9L);
    }

    @Test
    void delegatesCompletedWorkout() {
        FitnessQueryService queryService = mock(FitnessQueryService.class);
        WorkoutService workoutService = mock(WorkoutService.class);
        WorkoutCompletionRequest request = new WorkoutCompletionRequest(
                null,
                "自由训练",
                Instant.parse("2026-08-11T02:00:00Z"),
                Instant.parse("2026-08-11T02:40:00Z"),
                40,
                List.of(new WorkoutExerciseRequest(
                        100025L,
                        "杠铃卧推",
                        List.of(new WorkoutSetRequest(1, BigDecimal.valueOf(60), 8, true)))));
        WorkoutCompletionResponse response = new WorkoutCompletionResponse(201L, BigDecimal.valueOf(480));
        when(workoutService.completeWorkout(request)).thenReturn(response);

        FitnessController controller = new FitnessController(queryService, workoutService);

        assertSame(response, controller.completeWorkout(request));
    }
}

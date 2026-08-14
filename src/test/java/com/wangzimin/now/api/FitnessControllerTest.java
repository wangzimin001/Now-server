package com.wangzimin.now.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.service.FitnessQueryService.ExercisePageResponse;
import com.wangzimin.now.service.FitnessQueryService.ExerciseResponse;
import com.wangzimin.now.service.FitnessQueryService.ExerciseSubcategory;
import com.wangzimin.now.service.FitnessQueryService.LatestExercisePerformance;
import com.wangzimin.now.service.FitnessQueryService.LatestPerformanceSet;
import com.wangzimin.now.service.FitnessQueryService.PlanExercise;
import com.wangzimin.now.service.FitnessQueryService.WorkoutHistoryItem;
import com.wangzimin.now.service.FitnessQueryService.WorkoutHistoryDetail;
import com.wangzimin.now.service.FitnessQueryService.WorkoutPlanResponse;
import com.wangzimin.now.service.WorkoutService;
import com.wangzimin.now.service.WorkoutService.PlanExerciseRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutCompletionRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutCompletionResponse;
import com.wangzimin.now.service.WorkoutService.WorkoutHistoryExerciseUpdate;
import com.wangzimin.now.service.WorkoutService.WorkoutHistoryUpdateRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutExerciseRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutPlanRequest;
import com.wangzimin.now.service.WorkoutService.WorkoutSetRequest;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class FitnessControllerTest {

    @Test
    void templateAndHistoryResponsesDoNotExposeDifficulty() {
        assertFalse(hasField(WorkoutPlanResponse.class, "level"));
        assertFalse(hasField(WorkoutPlanResponse.class, "difficulty"));
        assertFalse(hasField(WorkoutHistoryItem.class, "level"));
        assertFalse(hasField(WorkoutHistoryItem.class, "difficulty"));
        assertTrue(hasField(WorkoutHistoryItem.class, "completedSetCount"));
    }

    private boolean hasField(Class<?> recordType, String fieldName) {
        return Stream.of(recordType.getRecordComponents())
                .anyMatch(component -> component.getName().equals(fieldName));
    }

    @Test
    void delegatesExerciseQueriesToService() {
        FitnessQueryService service = mock(FitnessQueryService.class);
        WorkoutService workoutService = mock(WorkoutService.class);
        List<ExerciseResponse> expected = List.of(
                new ExerciseResponse(100025L, "0025", "杠铃卧推", "barbell bench press", "chest", "胸部",
                        List.of(new ExerciseSubcategory(2L, "middle-chest", "中胸")),
                        "肱三头肌", "杠铃", "胸肌", "保持肩胛稳定。",
                        "/static/exercises/gifs/0025-EIeI8Vf.gif", "© Gym visual — https://gymvisual.com/"));
        ExercisePageResponse response = new ExercisePageResponse(expected, 1L, 1, 20, 1);
        when(service.exercises("chest", "middle-chest", "卧推", 1, 20)).thenReturn(response);

        FitnessController controller = new FitnessController(service, workoutService);

        assertSame(response, controller.exercises("chest", "middle-chest", "卧推", 1, 20));
    }

    @Test
    void supportsWorkoutPlanLifecycle() {
        FitnessQueryService queryService = mock(FitnessQueryService.class);
        WorkoutService workoutService = mock(WorkoutService.class);
        WorkoutPlanRequest request = new WorkoutPlanRequest(
                "胸背训练", "推拉组合", 50,
                List.of(new PlanExerciseRequest(100025L, 4, 8, 120)));
        WorkoutPlanResponse response = new WorkoutPlanResponse(
                9L, "胸背训练", "推拉组合", 50,
                List.of(new PlanExercise(9L, 100025L, "杠铃卧推", "胸部", 4, 8, 120)));
        Jwt jwt = jwt(7L);
        when(workoutService.createPlan(7L, request)).thenReturn(9L);
        when(queryService.workoutPlan(9L, 7L)).thenReturn(response);

        FitnessController controller = new FitnessController(queryService, workoutService);

        assertSame(response, controller.createWorkoutPlan(jwt, request));
        assertSame(response, controller.updateWorkoutPlan(jwt, 9L, request));
        controller.deleteWorkoutPlan(jwt, 9L);
        verify(workoutService).updatePlan(7L, 9L, request);
        verify(workoutService).deletePlan(7L, 9L);
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
                        List.of(new WorkoutSetRequest(1, BigDecimal.valueOf(60), 8, 90, true)))));
        WorkoutCompletionResponse response = new WorkoutCompletionResponse(201L, BigDecimal.valueOf(480));
        Jwt jwt = jwt(7L);
        when(workoutService.completeWorkout(7L, request)).thenReturn(response);

        FitnessController controller = new FitnessController(queryService, workoutService);

        // 组间歇必须作为组记录的一部分穿过控制器边界，不能在委托前被丢弃。
        assertEquals(90, request.exercises().get(0).sets().get(0).restDurationSeconds());
        assertSame(response, controller.completeWorkout(jwt, request));
        verify(workoutService).completeWorkout(7L, request);
    }

    @Test
    void delegatesLatestExercisePerformanceForCurrentUser() {
        FitnessQueryService queryService = mock(FitnessQueryService.class);
        WorkoutService workoutService = mock(WorkoutService.class);
        Jwt jwt = jwt(7L);
        List<Long> exerciseIds = List.of(100025L, 100026L);
        List<LatestExercisePerformance> expected = List.of(new LatestExercisePerformance(
                100025L,
                java.time.LocalDateTime.of(2026, 8, 14, 12, 0),
                List.of(new LatestPerformanceSet(1, BigDecimal.valueOf(60), 8))));
        when(queryService.latestExercisePerformances(7L, exerciseIds)).thenReturn(expected);

        FitnessController controller = new FitnessController(queryService, workoutService);

        assertSame(expected, controller.latestExercisePerformances(jwt, exerciseIds));
        verify(queryService).latestExercisePerformances(7L, exerciseIds);
    }

    @Test
    void delegatesHistoryCorrectionAndDeletionForCurrentUser() {
        FitnessQueryService queryService = mock(FitnessQueryService.class);
        WorkoutService workoutService = mock(WorkoutService.class);
        Jwt jwt = jwt(7L);
        WorkoutHistoryUpdateRequest request = new WorkoutHistoryUpdateRequest(List.of(
                new WorkoutHistoryExerciseUpdate(301L, List.of(
                        new WorkoutSetRequest(1, BigDecimal.valueOf(62.5), 8, 95, true)))));
        WorkoutHistoryDetail detail = new WorkoutHistoryDetail(
                201L, "胸部训练", java.time.LocalDateTime.of(2026, 8, 14, 12, 0),
                40, BigDecimal.valueOf(500), List.of());
        when(queryService.historyDetail(201L, 7L)).thenReturn(detail);

        FitnessController controller = new FitnessController(queryService, workoutService);

        assertSame(detail, controller.updateHistoryDetail(jwt, 201L, request));
        controller.deleteHistory(jwt, 201L);
        verify(workoutService).updateWorkoutHistory(7L, 201L, request);
        verify(workoutService).deleteWorkoutHistory(7L, 201L);
    }

    private Jwt jwt(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(String.valueOf(userId));
        return jwt;
    }
}

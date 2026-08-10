package com.wangzimin.now.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.service.FitnessQueryService.ExerciseResponse;

import org.junit.jupiter.api.Test;

class FitnessControllerTest {

    @Test
    void delegatesExerciseQueriesToService() {
        FitnessQueryService service = mock(FitnessQueryService.class);
        List<ExerciseResponse> expected = List.of(
                new ExerciseResponse(1L, "杠铃卧推", "胸部", "杠铃", "基础", "保持肩胛稳定。"));
        when(service.exercises()).thenReturn(expected);

        FitnessController controller = new FitnessController(service);

        assertSame(expected, controller.exercises());
    }
}

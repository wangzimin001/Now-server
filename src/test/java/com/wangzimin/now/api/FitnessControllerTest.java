package com.wangzimin.now.api;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.service.FitnessQueryService.ExercisePageResponse;
import com.wangzimin.now.service.FitnessQueryService.ExerciseResponse;

import org.junit.jupiter.api.Test;

class FitnessControllerTest {

    @Test
    void delegatesExerciseQueriesToService() {
        FitnessQueryService service = mock(FitnessQueryService.class);
        List<ExerciseResponse> expected = List.of(
                new ExerciseResponse(100025L, "0025", "杠铃卧推", "barbell bench press", "chest", "胸部",
                        "肱三头肌", "杠铃", "胸肌", "保持肩胛稳定。",
                        "/static/exercises/gifs/0025-EIeI8Vf.gif", "© Gym visual — https://gymvisual.com/"));
        ExercisePageResponse response = new ExercisePageResponse(expected, 1L, 1, 20, 1);
        when(service.exercises("chest", "卧推", 1, 20)).thenReturn(response);

        FitnessController controller = new FitnessController(service);

        assertSame(response, controller.exercises("chest", "卧推", 1, 20));
    }
}

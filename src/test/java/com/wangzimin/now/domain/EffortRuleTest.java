package com.wangzimin.now.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** 验证 RIR 与数据库 RPE 的保守换算边界。 */
class EffortRuleTest {

    /** 完整验证空值、整数刻度和旧半级数据的向下取整策略。 */
    @Test
    void convertsRirAndRpeWithoutOverestimatingReserve() {
        assertNull(EffortRule.toRpe(null));
        assertNull(EffortRule.toRepsInReserve(null));
        assertEquals(BigDecimal.valueOf(8), EffortRule.toRpe(2));
        assertEquals(2, EffortRule.toRepsInReserve(BigDecimal.valueOf(8)));
        assertEquals(1, EffortRule.toRepsInReserve(BigDecimal.valueOf(8.5)));
        assertEquals(3, EffortRule.toRepsInReserve(BigDecimal.valueOf(6)));
        assertEquals(0, EffortRule.toRepsInReserve(BigDecimal.valueOf(10.5)));
    }
}

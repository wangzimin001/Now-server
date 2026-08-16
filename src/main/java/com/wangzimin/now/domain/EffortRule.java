package com.wangzimin.now.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 集中描述训练主观余力与 RPE 刻度之间的换算规则。
 *
 * <p>移动端使用更容易理解的 RIR（还能标准完成几次），数据库沿用既有 RPE 字段。
 * 用户选择的“3+”按 3 保存，因为渐进建议只需要判断是否至少保留两次余力，
 * 不应根据更乐观的主观估计进一步放大推荐重量。</p>
 */
public enum EffortRule {

    REPS_IN_RESERVE_MIN(0),
    REPS_IN_RESERVE_MAX(3),
    RPE_SCALE_MAX(10),
    RECENT_PERFORMANCE_SESSION_LIMIT(2);

    private final int value;

    /**
     * 创建一个主观用力程度规则。
     *
     * @param value 校验、换算或查询使用的整数值
     */
    EffortRule(int value) {
        this.value = value;
    }

    /**
     * 返回规则的整数表达。
     *
     * @return 规则数值
     */
    public int value() {
        return value;
    }

    /**
     * 把客户端 RIR 转换为数据库 RPE；空值表示用户无法可靠判断。
     *
     * @param repsInReserve 用户估计还能标准完成的次数
     * @return RPE 刻度，或未记录时的空值
     */
    public static BigDecimal toRpe(Integer repsInReserve) {
        if (repsInReserve == null) {
            return null;
        }
        return BigDecimal.valueOf(RPE_SCALE_MAX.value() - repsInReserve);
    }

    /**
     * 把数据库 RPE 还原为保守的整数 RIR。
     *
     * <p>旧数据若包含半级 RPE，先向下取整 RIR，避免把模糊估计解释得更轻松；
     * 结果始终限制在移动端支持的 0 到 3+ 范围。</p>
     *
     * @param rpe 数据库存储的主观用力程度
     * @return 规范化 RIR，或未记录时的空值
     */
    public static Integer toRepsInReserve(BigDecimal rpe) {
        if (rpe == null) {
            return null;
        }
        int estimated = BigDecimal.valueOf(RPE_SCALE_MAX.value())
                .subtract(rpe)
                .setScale(BusinessRule.ZERO_COUNT.value(), RoundingMode.FLOOR)
                .intValue();
        return Math.max(REPS_IN_RESERVE_MIN.value(), Math.min(REPS_IN_RESERVE_MAX.value(), estimated));
    }
}

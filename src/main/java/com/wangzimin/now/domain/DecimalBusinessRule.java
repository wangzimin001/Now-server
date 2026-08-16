package com.wangzimin.now.domain;

import java.math.BigDecimal;

/**
 * 集中保存必须以十进制定点数表达的业务规则。
 *
 * <p>所有常量都从十进制字符串创建，避免二进制浮点转换污染重量、容量和估算指标。
 * 新增十进制规则时应继续使用字符串构造，并在调用处明确舍入方向。</p>
 */
public enum DecimalBusinessRule {

    WEIGHT_STEP_KG("0.25");

    private final BigDecimal value;

    /**
     * 创建精确的十进制业务规则。
     *
     * @param value 十进制字符串
     */
    DecimalBusinessRule(String value) {
        this.value = new BigDecimal(value);
    }

    /**
     * 返回可直接参与精确计算的规则值。
     *
     * @return 十进制定点值
     */
    public BigDecimal value() {
        return value;
    }

    /**
     * 判断候选值是否精确落在当前十进制步进上。
     *
     * @param candidate 待校验十进制值
     * @return 非空且能被当前步进整除时返回真
     */
    public boolean isMultiple(BigDecimal candidate) {
        return candidate != null && candidate.remainder(value).compareTo(BigDecimal.ZERO) == 0;
    }
}

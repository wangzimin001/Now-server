package com.wangzimin.now.domain;

/**
 * 描述训练完成后可以产生的个人纪录类型。
 *
 * <p>类型编码供客户端稳定识别，名称、单位和说明用于直接构造响应。
 * 业务服务只负责比较数值，不再重复维护同一组展示字符串。</p>
 */
public enum AchievementType {
    MAX_WEIGHT("MAX_WEIGHT", "最大重量", "kg", ""),
    ESTIMATED_ONE_REP_MAX("ESTIMATED_1RM", "估算 1RM", "kg", "综合重量与次数估算"),
    MAX_SET_VOLUME("MAX_SET_VOLUME", "单组最大容量", "kg", "单组重量 × 次数"),
    EXERCISE_VOLUME("EXERCISE_VOLUME", "动作最大容量", "kg", "本次该动作全部完成组"),
    EXERCISE_REPETITIONS("EXERCISE_REPS", "动作最多次数", "次", "本次该动作累计次数"),
    SAME_WEIGHT_REPETITIONS("SAME_WEIGHT_REPS", "同重量最多次数", "次", "");

    private final String code;
    private final String label;
    private final String unit;
    private final String detail;

    /**
     * 创建个人纪录类型定义。
     *
     * @param code 客户端稳定编码
     * @param label 中文展示名称
     * @param unit 数值单位
     * @param detail 计算口径说明
     */
    AchievementType(String code, String label, String unit, String detail) {
        this.code = code;
        this.label = label;
        this.unit = unit;
        this.detail = detail;
    }

    /** @return 客户端稳定编码 */
    public String code() {
        return code;
    }

    /** @return 中文展示名称 */
    public String label() {
        return label;
    }

    /** @return 数值单位 */
    public String unit() {
        return unit;
    }

    /** @return 计算口径说明 */
    public String detail() {
        return detail;
    }
}

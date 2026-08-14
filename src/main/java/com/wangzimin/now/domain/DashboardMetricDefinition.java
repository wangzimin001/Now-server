package com.wangzimin.now.domain;

/**
 * 定义训练看板尚未接入传感器时展示的指标元数据。
 *
 * <p>这些内容是产品配置而不是查询逻辑，集中为枚举后可以避免控制器和服务层出现多组魔法字符串。</p>
 */
public enum DashboardMetricDefinition {
    CALORIES("calories", "0", "活动消耗", "450 kcal"),
    WATER("water", "0", "饮水", "2,000 ml"),
    SLEEP("sleep", "--", "睡眠", "待记录");

    private final String key;
    private final String value;
    private final String label;
    private final String target;

    /**
     * 创建一个看板指标定义。
     *
     * @param key 前端稳定键
     * @param value 当前占位值
     * @param label 中文名称
     * @param target 目标或提示文本
     */
    DashboardMetricDefinition(String key, String value, String label, String target) {
        this.key = key;
        this.value = value;
        this.label = label;
        this.target = target;
    }

    /** @return 前端稳定键 */
    public String key() {
        return key;
    }

    /** @return 当前展示值 */
    public String value() {
        return value;
    }

    /** @return 指标中文名称 */
    public String label() {
        return label;
    }

    /** @return 指标目标文本 */
    public String target() {
        return target;
    }
}

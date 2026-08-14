package com.wangzimin.now.domain;

import java.time.DayOfWeek;

/**
 * 定义训练看板一周七天的顺序、中文标签和默认状态。
 *
 * <p>当前示例计划默认在周一和周三完成训练，其余日期展示休息。
 * 使用枚举后，服务层不再依赖数组位置或匿名数字判断星期。</p>
 */
public enum WeekDayDefinition {
    MONDAY(DayOfWeek.MONDAY, "一", WeekState.DONE),
    TUESDAY(DayOfWeek.TUESDAY, "二", WeekState.REST),
    WEDNESDAY(DayOfWeek.WEDNESDAY, "三", WeekState.DONE),
    THURSDAY(DayOfWeek.THURSDAY, "四", WeekState.REST),
    FRIDAY(DayOfWeek.FRIDAY, "五", WeekState.REST),
    SATURDAY(DayOfWeek.SATURDAY, "六", WeekState.REST),
    SUNDAY(DayOfWeek.SUNDAY, "日", WeekState.REST);

    private final DayOfWeek dayOfWeek;
    private final String label;
    private final WeekState defaultState;

    /**
     * 创建周视图日期定义。
     *
     * @param dayOfWeek Java 日期枚举
     * @param label 中文单字标签
     * @param defaultState 非今日时的默认显示状态
     */
    WeekDayDefinition(DayOfWeek dayOfWeek, String label, WeekState defaultState) {
        this.dayOfWeek = dayOfWeek;
        this.label = label;
        this.defaultState = defaultState;
    }

    /** @return Java 日期枚举 */
    public DayOfWeek dayOfWeek() {
        return dayOfWeek;
    }

    /** @return 中文单字标签 */
    public String label() {
        return label;
    }

    /** @return 非今日时的默认状态 */
    public WeekState defaultState() {
        return defaultState;
    }
}

package com.wangzimin.now.domain;

/**
 * 定义训练看板中日期单元格的显示状态。
 *
 * <p>枚举外部值保持现有前端 CSS 类名不变，服务端不再在循环中拼写状态字符串。</p>
 */
public enum WeekState {
    TODAY("today"),
    DONE("done"),
    REST("rest");

    private final String externalValue;

    /**
     * 创建一个周视图状态。
     *
     * @param externalValue 前端识别的状态值
     */
    WeekState(String externalValue) {
        this.externalValue = externalValue;
    }

    /**
     * 返回前端周视图使用的状态值。
     *
     * @return 稳定的 CSS 状态名
     */
    public String externalValue() {
        return externalValue;
    }
}

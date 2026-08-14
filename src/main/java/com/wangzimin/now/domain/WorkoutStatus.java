package com.wangzimin.now.domain;

/**
 * 表示训练会话和训练组在数据库中的业务状态。
 *
 * <p>SQL 参数、写入逻辑和响应判断统一使用本枚举，避免散落的状态字符串产生拼写差异。
 * 枚举名称与数据库值保持一致，便于日志排查和历史数据兼容。</p>
 */
public enum WorkoutStatus {
    COMPLETED,
    SKIPPED,
    DELETED;

    /**
     * 返回写入数据库和绑定 SQL 参数时使用的稳定值。
     *
     * @return 大写状态名称
     */
    public String databaseValue() {
        return name();
    }
}

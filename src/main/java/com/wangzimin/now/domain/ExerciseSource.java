package com.wangzimin.now.domain;

/**
 * 标识动作数据的来源类型。
 *
 * <p>动作库查询只读取标准数据集，而用户或系统手工动作可以继续使用其他来源。
 * 将来源值封装为枚举后，查询代码不再依赖重复的字符串字面量。</p>
 */
public enum ExerciseSource {
    STANDARD_DATASET("exercise-dataset");

    private final String databaseValue;

    /**
     * 创建动作来源枚举项。
     *
     * @param databaseValue 数据库中保存的稳定来源值
     */
    ExerciseSource(String databaseValue) {
        this.databaseValue = databaseValue;
    }

    /**
     * 返回数据库查询参数使用的来源值。
     *
     * @return 稳定来源字符串
     */
    public String databaseValue() {
        return databaseValue;
    }
}

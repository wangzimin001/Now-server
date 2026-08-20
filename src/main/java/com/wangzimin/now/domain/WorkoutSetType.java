package com.wangzimin.now.domain;

import java.util.Arrays;

/**
 * 定义训练组在一次动作中的业务用途。
 *
 * <p>普通组同时参与容量与个人纪录；热身组仅保留训练事实，不进入负荷统计；
 * 递减组计入训练容量，但不能被当作主工作组推导最大重量或渐进建议。</p>
 */
public enum WorkoutSetType {

    STANDARD("STANDARD", true, true, true),
    WARM_UP("WARM_UP", false, false, true),
    DROP_SET("DROP_SET", true, false, false);

    private final String databaseValue;
    private final boolean contributesToVolume;
    private final boolean contributesToPerformance;
    private final boolean countsAsPrimaryGroup;

    /**
     * 创建组类型及其统计语义。
     *
     * @param databaseValue 数据库存储值和接口稳定编码
     * @param contributesToVolume 是否计入训练容量与累计次数
     * @param contributesToPerformance 是否计入主工作组纪录与渐进证据
     * @param countsAsPrimaryGroup 是否在进度和摘要中作为独立组计数
     */
    WorkoutSetType(String databaseValue, boolean contributesToVolume, boolean contributesToPerformance,
            boolean countsAsPrimaryGroup) {
        this.databaseValue = databaseValue;
        this.contributesToVolume = contributesToVolume;
        this.contributesToPerformance = contributesToPerformance;
        this.countsAsPrimaryGroup = countsAsPrimaryGroup;
    }

    /**
     * 返回数据库和 JSON 接口使用的稳定编码。
     *
     * @return 组类型编码
     */
    public String databaseValue() {
        return databaseValue;
    }

    /**
     * 判断该组是否计入训练容量。
     *
     * @return 普通组与递减组返回真
     */
    public boolean contributesToVolume() {
        return contributesToVolume;
    }

    /**
     * 判断该组是否能作为主工作组表现证据。
     *
     * @return 仅普通组返回真
     */
    public boolean contributesToPerformance() {
        return contributesToPerformance;
    }

    /**
     * 判断该记录是否在训练进度和摘要里作为一组独立计数。
     *
     * @return 普通组和热身组返回真，附属递减组返回假
     */
    public boolean countsAsPrimaryGroup() {
        return countsAsPrimaryGroup;
    }

    /**
     * 把可空数据库值恢复为领域枚举，兼容迁移前的空值。
     *
     * @param value 数据库存储值
     * @return 对应组类型；空值返回普通组
     */
    public static WorkoutSetType fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return STANDARD;
        }
        return Arrays.stream(values())
                .filter(type -> type.databaseValue.equals(value))
                .findFirst()
                .orElse(STANDARD);
    }

    /**
     * 把旧客户端未提交的可空类型收敛为普通组。
     *
     * @param value 请求中的可空类型
     * @return 非空组类型
     */
    public static WorkoutSetType normalize(WorkoutSetType value) {
        return value == null ? STANDARD : value;
    }
}

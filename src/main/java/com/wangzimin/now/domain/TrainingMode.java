package com.wangzimin.now.domain;

import java.util.Arrays;

/**
 * 描述用户可选择的训练配置模式。
 *
 * <p>外部接口继续使用小写值以兼容现有客户端，服务端通过枚举完成校验和持久化转换。
 * 新增训练模式时只需扩展本枚举及相应业务实现，不再修改正则表达式。</p>
 */
public enum TrainingMode {
    FREE("free"),
    CYCLE("cycle");

    private final String externalValue;

    /**
     * 创建一个具有稳定外部值的训练模式。
     *
     * @param externalValue API 和数据库共同使用的小写值
     */
    TrainingMode(String externalValue) {
        this.externalValue = externalValue;
    }

    /**
     * 将客户端字符串转换为受控枚举。
     *
     * @param value 客户端提交的训练模式
     * @return 匹配的训练模式；不存在时返回空值
     */
    public static TrainingMode fromExternalValue(String value) {
        return Arrays.stream(values())
                .filter(mode -> mode.externalValue.equals(value))
                .findFirst()
                .orElse(null);
    }

    /**
     * 返回 API 和数据库所使用的稳定值。
     *
     * @return 小写训练模式
     */
    public String externalValue() {
        return externalValue;
    }
}

package com.wangzimin.now.domain;

/**
 * 集中保存基础设施层使用的稳定文本值。
 *
 * <p>这些字符串需要跨鉴权、哈希、日期格式化和空状态响应复用。
 * 将它们枚举化可以让用途显式化，并避免算法名称或 JWT 声明名称出现拼写分叉。</p>
 */
public enum SystemText {
    EMPTY(""),
    JWT_ISSUER("now-server"),
    JWT_USERNAME_CLAIM("username"),
    JWT_DISPLAY_NAME_CLAIM("displayName"),
    JWT_TOKEN_TYPE("JWT"),
    HASH_ALGORITHM("SHA-256"),
    HASH_BYTE_FORMAT("%02x"),
    SIGNING_KEY_ALGORITHM("HmacSHA256"),
    USERNAME_PATTERN("[a-zA-Z0-9_]{4,30}"),
    EMPTY_JSON("null"),
    DAY_FORMAT("dd"),
    MONTH_FORMAT("M月"),
    EMPTY_PLAN_NAME("暂无训练模板"),
    EMPTY_WORKOUT_NAME("暂无训练"),
    SAME_WEIGHT_DETAIL_SUFFIX("kg 下完成"),
    SERVICE_NAME("now-server"),
    HEALTH_UP("UP"),
    REQUEST_FAILED("请求未能完成"),
    ACCOUNT_KEY_MISSING("创建账号后未返回主键"),
    PLAN_KEY_MISSING("创建训练模板后未返回主键"),
    WORKOUT_KEY_MISSING("保存训练后未返回主键"),
    SESSION_EXERCISE_KEY_MISSING("保存训练动作后未返回主键"),
    TRAINING_CONFIG_UNREADABLE("训练配置保存后无法读取"),
    TRAINING_CONFIG_JSON_CORRUPTED("训练配置 JSON 无法解析"),
    JWT_SECRET_TOO_SHORT("AUTH_JWT_SECRET 长度未达到安全规则");

    private final String value;

    /**
     * 创建基础设施稳定文本。
     *
     * @param value 被框架、数据库或客户端识别的文本
     */
    SystemText(String value) {
        this.value = value;
    }

    /**
     * 返回稳定文本值。
     *
     * @return 不随请求变化的配置文本
     */
    public String value() {
        return value;
    }
}

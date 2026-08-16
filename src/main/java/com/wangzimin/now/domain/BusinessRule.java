package com.wangzimin.now.domain;

/**
 * 集中保存服务端使用的数值型业务规则。
 *
 * <p>枚举项为数值提供业务名称，调用方不再直接书写难以理解的数字。
 * 同一规则只能在此处定义，修改分页、鉴权或训练限制时可以统一审查影响范围。
 * 这些值属于应用策略，不包含数据库主键、用户输入或运行时计算结果。</p>
 */
public enum BusinessRule {

    ZERO_COUNT(0),
    DEFAULT_WEEKLY_TARGET(3),
    EXERCISE_DEFAULT_PAGE(1),
    EXERCISE_DEFAULT_LIMIT(20),
    EXERCISE_MAX_LIMIT(50),
    LATEST_PERFORMANCE_MAX_EXERCISES(50),
    EXERCISE_PROGRESS_SESSION_LIMIT(50),
    HISTORY_RESULT_LIMIT(200),
    BCRYPT_STRENGTH(12),
    JWT_SECRET_MIN_BYTES(32),
    ACCESS_TOKEN_SECONDS(15 * 60),
    REFRESH_TOKEN_DAYS(30),
    REFRESH_TOKEN_BYTES(48),
    TRAINING_PLAN_MAX_BYTES(64 * 1024),
    TRAINING_CYCLE_MIN_DAYS(1),
    TRAINING_CYCLE_MAX_DAYS(30),
    INITIAL_REVISION(1),
    EMPTY_REVISION(0),
    COLLECTION_MIN_SIZE(1),
    ORDER_INDEX_OFFSET(1),
    ONE_REP_MAX_DIVISOR(30),
    METRIC_CALCULATION_SCALE(6),
    METRIC_RESULT_SCALE(2),
    PERCENT_MULTIPLIER(100),
    HASH_HEX_CAPACITY(64),
    PUBLIC_ID_RANDOM_LENGTH(9),
    PUBLIC_ID_GENERATION_ATTEMPTS(5),
    SOCIAL_DEFAULT_PAGE_LIMIT(30),
    SOCIAL_MAX_PAGE_LIMIT(50),
    SOCIAL_INTERACTION_PREVIEW_LIMIT(10),
    SOCIAL_NOTIFICATION_AVATAR_PREVIEW_LIMIT(3),
    SOCIAL_POST_MAX_IMAGE_COUNT(9),
    SOCIAL_POST_MAX_VIDEO_COUNT(1),
    SOCIAL_GROUP_MIN_MEMBER_COUNT(2),
    SOCIAL_GROUP_MAX_MEMBER_COUNT(100),
    SOCIAL_VIDEO_POSTER_MAX_EDGE(720),
    AVATAR_UPLOAD_MAX_BYTES(5 * 1024 * 1024),
    SOCIAL_UPLOAD_MAX_BYTES(20 * 1024 * 1024),
    SOCIAL_VIDEO_UPLOAD_MAX_BYTES(100 * 1024 * 1024);

    private final int value;

    /**
     * 创建一个带有明确业务含义的数值规则。
     *
     * @param value 规则对应的整数值
     */
    BusinessRule(int value) {
        this.value = value;
    }

    /**
     * 返回规则的整数表达。
     *
     * @return 可用于分页、校验或计算的整数
     */
    public int value() {
        return value;
    }

    /**
     * 返回规则的长整数表达。
     *
     * @return 可用于时间长度等长整数 API 的数值
     */
    public long longValue() {
        return value;
    }
}

package com.wangzimin.now.domain;

/**
 * 提供 Jakarta Validation 注解可引用的编译期常量。
 *
 * <p>Java 注解不能调用枚举实例方法，因此这些边界作为枚举类中的编译期字段暴露。
 * 它们仍集中在枚举领域层，业务记录类不得再次复制相同数字。</p>
 */
public enum ValidationRule {
    ;

    public static final String EXERCISE_DEFAULT_PAGE = "1";
    public static final String EXERCISE_DEFAULT_LIMIT = "20";
    public static final String MIN_WEIGHT_KG = "0.0";
    public static final int USERNAME_MIN_LENGTH = 4;
    public static final int USERNAME_MAX_LENGTH = 30;
    public static final int PASSWORD_MIN_LENGTH = 8;
    public static final int PASSWORD_MAX_LENGTH = 72;
    public static final int DISPLAY_NAME_MIN_LENGTH = 1;
    public static final int DISPLAY_NAME_MAX_LENGTH = 40;
    public static final int PLAN_NAME_MAX_LENGTH = 80;
    public static final int PLAN_DESCRIPTION_MAX_LENGTH = 500;
    public static final int PLAN_DURATION_MIN_MINUTES = 1;
    public static final int PLAN_DURATION_MAX_MINUTES = 600;
    public static final int PLAN_EXERCISE_MIN_COUNT = 1;
    public static final int PLAN_EXERCISE_MAX_COUNT = 50;
    public static final int TARGET_SET_MIN_COUNT = 1;
    public static final int TARGET_SET_MAX_COUNT = 20;
    public static final int REPETITION_MIN_COUNT = 0;
    public static final int REPETITION_MAX_COUNT = 999;
    public static final int TARGET_REPETITION_MIN_COUNT = 1;
    public static final int REST_MIN_SECONDS = 0;
    public static final int REST_MAX_SECONDS = 600;
    public static final int ACTUAL_REST_MAX_SECONDS = 86_400;
    public static final int REPS_IN_RESERVE_MIN_COUNT = 0;
    public static final int REPS_IN_RESERVE_MAX_COUNT = 3;
    public static final int WORKOUT_DURATION_MIN_MINUTES = 1;
    public static final int WORKOUT_DURATION_MAX_MINUTES = 1_440;
    public static final int WORKOUT_EXERCISE_MIN_COUNT = 1;
    public static final int WORKOUT_EXERCISE_MAX_COUNT = 100;
    public static final int WORKOUT_SET_MIN_COUNT = 1;
    public static final int WORKOUT_SET_MAX_COUNT = 50;
    public static final int CLIENT_RECORD_ID_MAX_LENGTH = 80;
    public static final int PUBLIC_ID_MAX_LENGTH = 20;
    public static final int FRIEND_REQUEST_MESSAGE_MAX_LENGTH = 120;
    public static final int FRIEND_REMARK_MAX_LENGTH = 40;
    public static final int SOCIAL_MESSAGE_MAX_LENGTH = 2_000;
    public static final int SOCIAL_POST_MAX_LENGTH = 2_000;
    public static final int SOCIAL_COMMENT_MAX_LENGTH = 500;
    public static final int SOCIAL_GROUP_NAME_MIN_LENGTH = 1;
    public static final int SOCIAL_GROUP_NAME_MAX_LENGTH = 60;
    public static final int SOCIAL_POST_MEDIA_MAX_COUNT = 9;
}

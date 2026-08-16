package com.wangzimin.now.domain;

/**
 * 集中保存控制器映射和安全白名单使用的 API 路径。
 *
 * <p>注解参数要求编译期常量，因此路径以枚举类中的常量字段提供。
 * 路由和鉴权配置引用同一来源，可以避免新增接口后因路径拼写不同而产生权限错误。</p>
 */
public enum ApiPath {
    ;

    public static final String API_ROOT = "/api/v1";
    public static final String AUTH_ROOT = API_ROOT + "/auth";
    public static final String AUTH_REGISTER = AUTH_ROOT + "/register";
    public static final String AUTH_LOGIN = AUTH_ROOT + "/login";
    public static final String AUTH_REFRESH = AUTH_ROOT + "/refresh";
    public static final String AUTH_LOGOUT = AUTH_ROOT + "/logout";
    public static final String HEALTH = API_ROOT + "/health";
    public static final String EXERCISES = API_ROOT + "/exercises/**";
    public static final String EXERCISE_CATEGORIES = API_ROOT + "/exercise-categories";
    public static final String DASHBOARD = API_ROOT + "/dashboard";
    public static final String WORKOUT_PLANS = API_ROOT + "/workout-plans";
    public static final String TRAINING_CONFIG = API_ROOT + "/training-config";
    public static final String SOCIAL_ROOT = API_ROOT + "/social";
    public static final String SOCIAL_FILES = SOCIAL_ROOT + "/files/**";
    public static final String DEMO_MEDIA = "/demo-media/**";
    public static final String ACTUATOR_HEALTH = "/actuator/health";
    public static final String ERROR = "/error";
    public static final String REGISTER_SEGMENT = "/register";
    public static final String LOGIN_SEGMENT = "/login";
    public static final String REFRESH_SEGMENT = "/refresh";
    public static final String LOGOUT_SEGMENT = "/logout";
    public static final String CURRENT_USER_SEGMENT = "/me";
    public static final String AVATAR_SEGMENT = "/avatar";
    public static final String DASHBOARD_SEGMENT = "/dashboard";
    public static final String WORKOUT_PLANS_SEGMENT = "/workout-plans";
    public static final String WORKOUT_PLAN_SEGMENT = "/workout-plans/{planId}";
    public static final String EXERCISES_SEGMENT = "/exercises";
    public static final String EXERCISE_CATEGORIES_SEGMENT = "/exercise-categories";
    public static final String WORKOUT_HISTORY_SEGMENT = "/workouts/history";
    public static final String WORKOUT_HISTORY_DETAIL_SEGMENT = "/workouts/history/{sessionId}";
    public static final String LATEST_PERFORMANCE_SEGMENT = "/workouts/latest-performance";
    public static final String EXERCISE_PROGRESS_SEGMENT = "/workouts/exercises/{exerciseId}/progress";
    public static final String WORKOUTS_SEGMENT = "/workouts";
    public static final String SOCIAL_USERS_SEGMENT = "/users/{publicId}";
    public static final String FRIEND_REQUESTS_SEGMENT = "/friend-requests";
    public static final String FRIEND_REQUEST_ACCEPT_SEGMENT = "/friend-requests/{requestId}/accept";
    public static final String FRIEND_REQUEST_REJECT_SEGMENT = "/friend-requests/{requestId}/reject";
    public static final String FRIEND_REQUEST_CANCEL_SEGMENT = "/friend-requests/{requestId}";
    public static final String FRIENDS_SEGMENT = "/friends";
    public static final String FRIEND_SEGMENT = "/friends/{publicId}";
    public static final String FRIEND_REMARK_SEGMENT = "/friends/{publicId}/remark";
    public static final String FRIEND_WORKOUTS_SEGMENT = "/friends/{publicId}/workouts";
    public static final String CONVERSATIONS_SEGMENT = "/conversations";
    public static final String DIRECT_CONVERSATION_SEGMENT = "/conversations/direct/{publicId}";
    public static final String CONVERSATION_MESSAGES_SEGMENT = "/conversations/{conversationId}/messages";
    public static final String CONVERSATION_READ_SEGMENT = "/conversations/{conversationId}/read";
    public static final String ATTACHMENTS_SEGMENT = "/attachments";
    public static final String SOCIAL_FILE_SEGMENT = "/files/{storedName}";
    public static final String SOCIAL_FILE_POSTER_SEGMENT = "/files/{storedName}/poster";
    public static final String GROUPS_SEGMENT = "/groups";
    public static final String GROUP_SEGMENT = "/groups/{conversationId}";
    public static final String GROUP_MEMBERS_SEGMENT = "/groups/{conversationId}/members";
    public static final String GROUP_MEMBER_SEGMENT = "/groups/{conversationId}/members/{publicId}";
    public static final String GROUP_LEAVE_SEGMENT = "/groups/{conversationId}/leave";
    public static final String MOMENTS_SEGMENT = "/moments";
    public static final String MOMENT_SEGMENT = "/moments/{postId}";
    public static final String MOMENT_LIKE_SEGMENT = "/moments/{postId}/like";
    public static final String MOMENT_COMMENTS_SEGMENT = "/moments/{postId}/comments";
    public static final String MOMENT_COMMENT_SEGMENT = "/moments/{postId}/comments/{commentId}";
    public static final String SOCIAL_UNREAD_SUMMARY_SEGMENT = "/unread-summary";
    public static final String MOMENT_NOTIFICATIONS_SEGMENT = "/moment-notifications";
    public static final String MOMENT_NOTIFICATIONS_READ_SEGMENT = "/moment-notifications/read";
    public static final String MULTIPART_FILE_PART = "file";
}

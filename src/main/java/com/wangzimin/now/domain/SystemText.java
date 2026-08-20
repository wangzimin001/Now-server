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
    FRIEND_REQUEST_KEY_MISSING("创建好友申请后未返回主键"),
    CONVERSATION_KEY_MISSING("创建会话后未返回主键"),
    MESSAGE_KEY_MISSING("保存消息后未返回主键"),
    ATTACHMENT_KEY_MISSING("保存附件后未返回主键"),
    POST_KEY_MISSING("发布朋友圈后未返回主键"),
    COMMENT_KEY_MISSING("保存评论后未返回主键"),
    PLAN_KEY_MISSING("创建训练模板后未返回主键"),
    WORKOUT_KEY_MISSING("保存训练后未返回主键"),
    SESSION_EXERCISE_KEY_MISSING("保存训练动作后未返回主键"),
    TRAINING_CONFIG_UNREADABLE("训练配置保存后无法读取"),
    TRAINING_CONFIG_JSON_CORRUPTED("训练配置 JSON 无法解析"),
    JWT_SECRET_TOO_SHORT("AUTH_JWT_SECRET 长度未达到安全规则"),
    PUBLIC_ID_PREFIX("N"),
    PUBLIC_ID_ALPHABET("23456789ABCDEFGHJKLMNPQRSTUVWXYZ"),
    IMAGE_MIME_PREFIX("image/"),
    VIDEO_MIME_PREFIX("video/"),
    ISO_BASE_MEDIA_SIGNATURE("ftyp"),
    BYTE_RANGE_UNIT("bytes"),
    JPEG_FORMAT("jpg"),
    JPEG_MIME_TYPE("image/jpeg"),
    VIDEO_POSTER_SUFFIX(".poster.jpg"),
    SOCIAL_POSTER_ROUTE_SUFFIX("/poster"),
    PUBLIC_CACHE_ONE_DAY("public, max-age=86400"),
    BINARY_MIME_TYPE("application/octet-stream"),
    SOCIAL_UPLOAD_DIRECTORY("uploads/social"),
    SOCIAL_FILE_ROUTE("/api/v1/social/files/"),
    SOCIAL_FILE_FIELD("file"),
    FILE_NAME_FALLBACK("file"),
    GROUP_DEFAULT_NAME("训练群聊"),
    MESSAGE_DELETED("消息已删除"),
    SYSTEM_GROUP_CREATED("群聊已创建"),
    SYSTEM_GROUP_RENAMED_PREFIX("群聊名称已改为："),
    SYSTEM_MEMBER_JOINED("加入了群聊"),
    SYSTEM_MEMBER_LEFT("退出了群聊"),
    SYSTEM_MEMBER_REMOVED("被移出群聊");

    public static final String SOCIAL_UPLOAD_CONFIG = "${social.upload.directory:uploads/social}";

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

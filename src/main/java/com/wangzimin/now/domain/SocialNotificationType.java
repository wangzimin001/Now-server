package com.wangzimin.now.domain;

/**
 * 定义朋友圈互动通知类型及其幂等键前缀。
 *
 * <p>同一用户对同一帖子重复点赞只保留一条通知；每条评论按评论主键独立记录。</p>
 */
public enum SocialNotificationType {
    POST_LIKE("POST_LIKE", "LIKE"),
    POST_COMMENT("POST_COMMENT", "COMMENT");

    private final String databaseValue;
    private final String keyPrefix;

    /**
     * 创建可持久化通知类型。
     *
     * @param databaseValue 数据库存储值
     * @param keyPrefix 幂等互动键前缀
     */
    SocialNotificationType(String databaseValue, String keyPrefix) {
        this.databaseValue = databaseValue;
        this.keyPrefix = keyPrefix;
    }

    /**
     * 返回数据库状态值。
     *
     * @return 稳定通知类型
     */
    public String databaseValue() {
        return databaseValue;
    }

    /**
     * 生成点赞或评论的稳定幂等键。
     *
     * @param postId 帖子主键
     * @param commentId 评论主键；点赞时为空
     * @return 当前互动唯一键
     */
    public String interactionKey(Long postId, Long commentId) {
        Long resourceId = this == POST_COMMENT ? commentId : postId;
        return keyPrefix + ":" + resourceId;
    }
}

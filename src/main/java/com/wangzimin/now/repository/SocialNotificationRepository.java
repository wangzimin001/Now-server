package com.wangzimin.now.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.wangzimin.now.domain.SocialNotificationType;

/**
 * 持久化朋友圈点赞、评论通知及其已读状态。
 *
 * <p>通知只发给帖子作者，查询展示名称时优先使用接收者给互动人的好友备注。</p>
 */
@Repository
public class SocialNotificationRepository {

    private final JdbcClient jdbcClient;

    /**
     * 创建朋友圈通知仓储。
     *
     * @param jdbcClient Spring JDBC 客户端
     */
    public SocialNotificationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 写入或重新激活点赞通知。
     *
     * @param recipientUserId 帖子作者
     * @param actorUserId 点赞用户
     * @param postId 帖子主键
     */
    public void upsertLike(Long recipientUserId, Long actorUserId, Long postId) {
        upsertNotification(recipientUserId, actorUserId, SocialNotificationType.POST_LIKE,
                postId, null);
    }

    /**
     * 写入一条评论通知。
     *
     * @param recipientUserId 帖子作者
     * @param actorUserId 评论用户
     * @param postId 帖子主键
     * @param commentId 评论主键
     */
    public void insertComment(Long recipientUserId, Long actorUserId, Long postId, Long commentId) {
        upsertNotification(recipientUserId, actorUserId, SocialNotificationType.POST_COMMENT,
                postId, commentId);
    }

    /**
     * 统一写入带幂等键的互动通知；重新点赞会重新变为未读。
     *
     * @param recipientUserId 接收者
     * @param actorUserId 互动人
     * @param type 通知类型
     * @param postId 帖子主键
     * @param commentId 可空评论主键
     */
    private void upsertNotification(Long recipientUserId, Long actorUserId,
            SocialNotificationType type, Long postId, Long commentId) {
        jdbcClient.sql("""
                        INSERT INTO social_notification
                            (recipient_user_id, actor_user_id, notification_type,
                             interaction_key, post_id, comment_id)
                        VALUES
                            (:recipientUserId, :actorUserId, :notificationType,
                             :interactionKey, :postId, :commentId)
                        ON DUPLICATE KEY UPDATE
                            read_at = NULL,
                            created_at = CURRENT_TIMESTAMP
                        """)
                .param("recipientUserId", recipientUserId)
                .param("actorUserId", actorUserId)
                .param("notificationType", type.databaseValue())
                .param("interactionKey", type.interactionKey(postId, commentId))
                .param("postId", postId)
                .param("commentId", commentId)
                .update();
    }

    /**
     * 取消点赞时移除对应通知。
     *
     * @param recipientUserId 帖子作者
     * @param actorUserId 点赞用户
     * @param postId 帖子主键
     */
    public void deleteLike(Long recipientUserId, Long actorUserId, Long postId) {
        jdbcClient.sql("""
                        DELETE FROM social_notification
                        WHERE recipient_user_id = :recipientUserId
                          AND actor_user_id = :actorUserId
                          AND notification_type = :notificationType
                          AND post_id = :postId
                        """)
                .param("recipientUserId", recipientUserId)
                .param("actorUserId", actorUserId)
                .param("notificationType", SocialNotificationType.POST_LIKE.databaseValue())
                .param("postId", postId)
                .update();
    }

    /**
     * 删除评论时同步移除其通知。
     *
     * @param commentId 评论主键
     */
    public void deleteComment(Long commentId) {
        jdbcClient.sql("DELETE FROM social_notification WHERE comment_id = :commentId")
                .param("commentId", commentId)
                .update();
    }

    /**
     * 删除帖子前清理全部关联通知。
     *
     * @param postId 帖子主键
     */
    public void deletePostNotifications(Long postId) {
        jdbcClient.sql("DELETE FROM social_notification WHERE post_id = :postId")
                .param("postId", postId)
                .update();
    }

    /**
     * 统计未读朋友圈互动。
     *
     * @param userId 当前用户主键
     * @return 未读点赞与评论总数
     */
    public int countUnread(Long userId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM social_notification
                        WHERE recipient_user_id = :userId AND read_at IS NULL
                        """)
                .param("userId", userId)
                .query(Integer.class)
                .single();
    }

    /**
     * 查询最近未读互动人的头像，用于朋友圈新消息入口。
     *
     * @param userId 当前用户主键
     * @param limit 最大头像数量
     * @return 去重后的头像地址
     */
    public List<String> listUnreadActorAvatars(Long userId, int limit) {
        return jdbcClient.sql("""
                        SELECT avatarUrl
                        FROM (
                            SELECT MAX(n.id) AS latestId, u.avatar_url AS avatarUrl
                            FROM social_notification n
                            JOIN app_user u ON u.id = n.actor_user_id
                            WHERE n.recipient_user_id = :userId AND n.read_at IS NULL
                              AND u.avatar_url IS NOT NULL
                            GROUP BY n.actor_user_id, u.avatar_url
                            ORDER BY latestId DESC
                            LIMIT :limit
                        ) recent_actor
                        ORDER BY latestId DESC
                        """)
                .param("userId", userId)
                .param("limit", limit)
                .query(String.class)
                .list();
    }

    /**
     * 查询当前用户最近的朋友圈互动通知。
     *
     * @param userId 当前用户主键
     * @param limit 最大数量
     * @return 按时间倒序的通知列表
     */
    public List<NotificationRow> listNotifications(Long userId, int limit) {
        return jdbcClient.sql("""
                        SELECT n.id, n.notification_type AS notificationType,
                               n.post_id AS postId, n.comment_id AS commentId,
                               actor.public_id AS actorPublicId,
                               COALESCE(NULLIF(f.remark, ''), actor.display_name) AS actorDisplayName,
                               actor.avatar_url AS actorAvatarUrl,
                               comment.content AS commentContent, post.content AS postContent,
                               (SELECT attachment.attachment_type
                                FROM social_post_attachment post_attachment
                                JOIN social_attachment attachment
                                  ON attachment.id = post_attachment.attachment_id
                                WHERE post_attachment.post_id = post.id
                                ORDER BY post_attachment.display_order ASC LIMIT 1) AS postMediaType,
                               (SELECT COALESCE(attachment.poster_url, attachment.public_url)
                                FROM social_post_attachment post_attachment
                                JOIN social_attachment attachment
                                  ON attachment.id = post_attachment.attachment_id
                                WHERE post_attachment.post_id = post.id
                                ORDER BY post_attachment.display_order ASC LIMIT 1) AS postPreviewUrl,
                               n.read_at AS readAt, n.created_at AS createdAt
                        FROM social_notification n
                        JOIN app_user actor ON actor.id = n.actor_user_id
                        JOIN social_post post ON post.id = n.post_id AND post.deleted_at IS NULL
                        LEFT JOIN social_post_comment comment
                          ON comment.id = n.comment_id AND comment.deleted_at IS NULL
                        LEFT JOIN friendship f
                          ON f.user_id = :userId AND f.friend_user_id = actor.id
                        WHERE n.recipient_user_id = :userId
                          AND (n.comment_id IS NULL OR comment.id IS NOT NULL)
                        ORDER BY n.created_at DESC, n.id DESC
                        LIMIT :limit
                        """)
                .param("userId", userId)
                .param("limit", limit)
                .query(NotificationRow.class)
                .list();
    }

    /**
     * 将当前用户全部朋友圈互动推进为已读。
     *
     * @param userId 当前用户主键
     */
    public void markAllRead(Long userId) {
        jdbcClient.sql("""
                        UPDATE social_notification
                        SET read_at = CURRENT_TIMESTAMP
                        WHERE recipient_user_id = :userId AND read_at IS NULL
                        """)
                .param("userId", userId)
                .update();
    }

    /**
     * 描述一条朋友圈互动消息。
     *
     * @param id 通知主键
     * @param notificationType 点赞或评论
     * @param postId 帖子主键
     * @param commentId 可空评论主键
     * @param actorPublicId 互动人公开 ID
     * @param actorDisplayName 备注优先展示名
     * @param actorAvatarUrl 互动人头像
     * @param commentContent 可空评论正文
     * @param postContent 帖子正文
     * @param postMediaType 首个媒体类型
     * @param postPreviewUrl 图片或视频封面地址
     * @param readAt 可空已读时间
     * @param createdAt 互动时间
     */
    public record NotificationRow(Long id, String notificationType, Long postId, Long commentId,
            String actorPublicId, String actorDisplayName, String actorAvatarUrl,
            String commentContent, String postContent, String postMediaType,
            String postPreviewUrl, LocalDateTime readAt, LocalDateTime createdAt) {
    }
}

package com.wangzimin.now.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.domain.WorkoutStatus;

/**
 * 持久化朋友圈内容、训练分享、图片、点赞和评论。
 *
 * <p>可见内容由当前用户自己及其好友发布。互动查询不附加互动者好友过滤，
 * 因而共同好友帖子中的陌生点赞和评论也会完整进入预览统计。</p>
 */
@Repository
public class SocialMomentRepository {

    private final JdbcClient jdbcClient;

    /**
     * 创建朋友圈仓储。
     *
     * @param jdbcClient Spring JDBC 客户端
     */
    public SocialMomentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 查询当前用户可见的一页朋友圈内容。
     *
     * @param userId 当前用户主键
     * @param beforeId 只读取该主键之前的内容
     * @param limit 最大数量
     * @return 帖子基础资料
     */
    public List<PostRow> listVisiblePosts(Long userId, Long beforeId, int limit) {
        return jdbcClient.sql(postSelect() + """
                        WHERE p.deleted_at IS NULL
                          AND (:beforeId = :emptyCursor OR (p.created_at, p.id) < (
                              SELECT cursor_post.created_at, cursor_post.id
                              FROM social_post cursor_post
                              WHERE cursor_post.id = :beforeId
                          ))
                          AND (p.author_user_id = :userId OR EXISTS (
                              SELECT 1 FROM friendship f
                              WHERE f.user_id = :userId
                                AND f.friend_user_id = p.author_user_id
                          ))
                        ORDER BY p.created_at DESC, p.id DESC
                        LIMIT :limit
                        """)
                .param("userId", userId)
                .param("beforeId", beforeId)
                .param("emptyCursor", BusinessRule.ZERO_COUNT.longValue())
                .param("limit", limit)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .query(PostRow.class)
                .list();
    }

    /**
     * 查询一条未删除朋友圈内容。
     *
     * @param postId 帖子主键
     * @return 可空帖子
     */
    public Optional<PostRow> findPost(Long postId) {
        return jdbcClient.sql(postSelect() + " WHERE p.id = :postId AND p.deleted_at IS NULL")
                .param("postId", postId)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .query(PostRow.class)
                .optional();
    }

    /**
     * 判断当前用户是否可以看到指定帖子。
     *
     * @param userId 当前用户主键
     * @param postId 帖子主键
     * @return 自己或好友的未删除帖子返回真
     */
    public boolean canViewPost(Long userId, Long postId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM social_post p
                        WHERE p.id = :postId AND p.deleted_at IS NULL
                          AND (p.author_user_id = :userId OR EXISTS (
                              SELECT 1 FROM friendship f
                              WHERE f.user_id = :userId
                                AND f.friend_user_id = p.author_user_id
                          ))
                        """)
                .param("userId", userId)
                .param("postId", postId)
                .query(Integer.class)
                .single() > 0;
    }

    /**
     * 创建一条朋友圈内容。
     *
     * @param authorUserId 作者主键
     * @param content 可空正文
     * @param workoutSessionId 可空训练主键
     * @return 新帖子主键
     */
    public long insertPost(Long authorUserId, String content, Long workoutSessionId) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO social_post (author_user_id, content, workout_session_id)
                        VALUES (:authorUserId, :content, :workoutSessionId)
                        """)
                .param("authorUserId", authorUserId)
                .param("content", content)
                .param("workoutSessionId", workoutSessionId)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(SystemText.POST_KEY_MISSING.value());
        }
        return key.longValue();
    }

    /**
     * 为帖子关联一项已上传图片或视频。
     *
     * @param postId 帖子主键
     * @param attachmentId 附件主键
     * @param displayOrder 显示顺序
     */
    public void insertPostAttachment(Long postId, Long attachmentId, int displayOrder) {
        jdbcClient.sql("""
                        INSERT INTO social_post_attachment (post_id, attachment_id, display_order)
                        VALUES (:postId, :attachmentId, :displayOrder)
                        """)
                .param("postId", postId)
                .param("attachmentId", attachmentId)
                .param("displayOrder", displayOrder)
                .update();
    }

    /**
     * 查询帖子按顺序排列的媒体附件。
     *
     * @param postId 帖子主键
     * @return 图片或视频列表
     */
    public List<PostAttachmentRow> listPostAttachments(Long postId) {
        return jdbcClient.sql("""
                        SELECT a.id, a.attachment_type AS attachmentType,
                               a.original_name AS originalName,
                               a.mime_type AS mimeType, a.size_bytes AS sizeBytes,
                               a.public_url AS publicUrl, a.poster_url AS posterUrl,
                               pa.display_order AS displayOrder
                        FROM social_post_attachment pa
                        JOIN social_attachment a ON a.id = pa.attachment_id
                        WHERE pa.post_id = :postId
                        ORDER BY pa.display_order ASC
                        """)
                .param("postId", postId)
                .query(PostAttachmentRow.class)
                .list();
    }

    /**
     * 校验训练属于当前用户且已经完成。
     *
     * @param userId 当前用户主键
     * @param workoutSessionId 训练主键
     * @return 可分享时为真
     */
    public boolean isShareableWorkout(Long userId, Long workoutSessionId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM workout_session
                        WHERE id = :workoutSessionId
                          AND owner_user_id = :userId
                          AND status = :completedStatus
                        """)
                .param("userId", userId)
                .param("workoutSessionId", workoutSessionId)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .query(Integer.class)
                .single() > 0;
    }

    /**
     * 软删除作者自己的朋友圈内容。
     *
     * @param postId 帖子主键
     * @param authorUserId 当前用户主键
     * @return 更新行数
     */
    public int deletePost(Long postId, Long authorUserId) {
        return jdbcClient.sql("""
                        UPDATE social_post
                        SET deleted_at = CURRENT_TIMESTAMP
                        WHERE id = :postId AND author_user_id = :authorUserId
                          AND deleted_at IS NULL
                        """)
                .param("postId", postId)
                .param("authorUserId", authorUserId)
                .update();
    }

    /**
     * 幂等添加当前用户的点赞。
     *
     * @param postId 帖子主键
     * @param userId 当前用户主键
     * @return 首次点赞为一，重复点赞为零
     */
    public int addLike(Long postId, Long userId) {
        return jdbcClient.sql("""
                        INSERT IGNORE INTO social_post_like (post_id, user_id)
                        VALUES (:postId, :userId)
                        """)
                .param("postId", postId)
                .param("userId", userId)
                .update();
    }

    /**
     * 幂等取消当前用户的点赞。
     *
     * @param postId 帖子主键
     * @param userId 当前用户主键
     */
    public void removeLike(Long postId, Long userId) {
        jdbcClient.sql("""
                        DELETE FROM social_post_like
                        WHERE post_id = :postId AND user_id = :userId
                        """)
                .param("postId", postId)
                .param("userId", userId)
                .update();
    }

    /**
     * 判断当前用户是否已点赞。
     *
     * @param postId 帖子主键
     * @param userId 当前用户主键
     * @return 已点赞时为真
     */
    public boolean hasLiked(Long postId, Long userId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM social_post_like
                        WHERE post_id = :postId AND user_id = :userId
                        """)
                .param("postId", postId)
                .param("userId", userId)
                .query(Integer.class)
                .single() > 0;
    }

    /**
     * 查询最早的若干点赞用户，好友名称优先使用查看者设置的备注。
     *
     * @param postId 帖子主键
     * @param viewerUserId 当前查看者主键
     * @param limit 预览数量
     * @return 点赞用户资料
     */
    public List<InteractionUserRow> listLikes(Long postId, Long viewerUserId, int limit) {
        return jdbcClient.sql("""
                        SELECT u.public_id AS publicId,
                               COALESCE(NULLIF(f.remark, ''), u.display_name) AS displayName,
                               u.avatar_url AS avatarUrl
                        FROM social_post_like l
                        JOIN app_user u ON u.id = l.user_id
                        LEFT JOIN friendship f
                          ON f.user_id = :viewerUserId AND f.friend_user_id = u.id
                        WHERE l.post_id = :postId
                        ORDER BY l.created_at ASC
                        LIMIT :limit
                        """)
                .param("postId", postId)
                .param("viewerUserId", viewerUserId)
                .param("limit", limit)
                .query(InteractionUserRow.class)
                .list();
    }

    /**
     * 统计帖子全部点赞数量。
     *
     * @param postId 帖子主键
     * @return 点赞总数
     */
    public int countLikes(Long postId) {
        return jdbcClient.sql("SELECT COUNT(*) FROM social_post_like WHERE post_id = :postId")
                .param("postId", postId)
                .query(Integer.class)
                .single();
    }

    /**
     * 新增一条评论。
     *
     * @param postId 帖子主键
     * @param authorUserId 评论人
     * @param content 评论正文
     * @return 新评论主键
     */
    public long insertComment(Long postId, Long authorUserId, String content) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO social_post_comment (post_id, author_user_id, content)
                        VALUES (:postId, :authorUserId, :content)
                        """)
                .param("postId", postId)
                .param("authorUserId", authorUserId)
                .param("content", content)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(SystemText.COMMENT_KEY_MISSING.value());
        }
        return key.longValue();
    }

    /**
     * 查询帖子最早的若干可见评论，好友作者优先使用查看者设置的备注。
     *
     * @param postId 帖子主键
     * @param viewerUserId 当前查看者主键
     * @param limit 预览数量
     * @return 评论预览
     */
    public List<CommentRow> listComments(Long postId, Long viewerUserId, int limit) {
        return jdbcClient.sql("""
                        SELECT c.id, c.post_id AS postId, c.author_user_id AS authorUserId,
                               u.public_id AS authorPublicId,
                               COALESCE(NULLIF(f.remark, ''), u.display_name) AS authorDisplayName,
                               u.avatar_url AS authorAvatarUrl, c.content, c.created_at AS createdAt
                        FROM social_post_comment c
                        JOIN app_user u ON u.id = c.author_user_id
                        LEFT JOIN friendship f
                          ON f.user_id = :viewerUserId AND f.friend_user_id = u.id
                        WHERE c.post_id = :postId AND c.deleted_at IS NULL
                        ORDER BY c.id ASC
                        LIMIT :limit
                        """)
                .param("postId", postId)
                .param("viewerUserId", viewerUserId)
                .param("limit", limit)
                .query(CommentRow.class)
                .list();
    }

    /**
     * 按主键查询一条未删除评论。
     *
     * @param commentId 评论主键
     * @return 可空评论
     */
    public Optional<CommentRow> findComment(Long commentId) {
        return jdbcClient.sql(commentSelect() + " WHERE c.id = :commentId AND c.deleted_at IS NULL")
                .param("commentId", commentId)
                .query(CommentRow.class)
                .optional();
    }

    /**
     * 统计帖子全部未删除评论。
     *
     * @param postId 帖子主键
     * @return 评论总数
     */
    public int countComments(Long postId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*) FROM social_post_comment
                        WHERE post_id = :postId AND deleted_at IS NULL
                        """)
                .param("postId", postId)
                .query(Integer.class)
                .single();
    }

    /**
     * 软删除评论人自己的评论。
     *
     * @param commentId 评论主键
     * @param authorUserId 当前用户主键
     * @return 更新行数
     */
    public int deleteComment(Long commentId, Long authorUserId) {
        return jdbcClient.sql("""
                        UPDATE social_post_comment
                        SET deleted_at = CURRENT_TIMESTAMP
                        WHERE id = :commentId AND author_user_id = :authorUserId
                          AND deleted_at IS NULL
                        """)
                .param("commentId", commentId)
                .param("authorUserId", authorUserId)
                .update();
    }

    /**
     * 返回帖子基础查询及可空训练摘要字段。
     *
     * @return 不带筛选条件的 SQL
     */
    private String postSelect() {
        return """
                SELECT p.id, p.author_user_id AS authorUserId,
                       u.public_id AS authorPublicId, u.display_name AS authorDisplayName,
                       u.avatar_url AS authorAvatarUrl, p.content,
                       p.workout_session_id AS workoutSessionId,
                       CASE WHEN ws.status = :completedStatus THEN ws.name_snapshot ELSE NULL END AS workoutName,
                       CASE WHEN ws.status = :completedStatus THEN ws.ended_at ELSE NULL END AS workoutCompletedAt,
                       CASE WHEN ws.status = :completedStatus THEN ws.duration_minutes ELSE NULL END AS workoutDurationMinutes,
                       CASE WHEN ws.status = :completedStatus THEN ws.total_volume_kg ELSE NULL END AS workoutTotalVolumeKg,
                       CASE WHEN ws.status = :completedStatus THEN
                           (SELECT COUNT(*) FROM session_exercise se WHERE se.session_id = ws.id)
                           ELSE NULL END AS workoutExerciseCount,
                       CASE WHEN ws.status = :completedStatus THEN
                           (SELECT COUNT(*) FROM set_record sr
                            JOIN session_exercise se ON se.id = sr.session_exercise_id
                            WHERE se.session_id = ws.id AND sr.status = :completedStatus)
                           ELSE NULL END AS workoutSetCount,
                       p.created_at AS createdAt
                FROM social_post p
                JOIN app_user u ON u.id = p.author_user_id
                LEFT JOIN workout_session ws ON ws.id = p.workout_session_id
                """;
    }

    /**
     * 返回评论及作者资料的统一查询字段。
     *
     * @return 不带筛选条件的 SQL
     */
    private String commentSelect() {
        return """
                SELECT c.id, c.post_id AS postId, c.author_user_id AS authorUserId,
                       u.public_id AS authorPublicId, u.display_name AS authorDisplayName,
                       u.avatar_url AS authorAvatarUrl, c.content, c.created_at AS createdAt
                FROM social_post_comment c
                JOIN app_user u ON u.id = c.author_user_id
                """;
    }

    /**
     * 描述朋友圈帖子及训练分享摘要。
     *
     * @param id 帖子主键
     * @param authorUserId 作者主键
     * @param authorPublicId 作者公开 ID
     * @param authorDisplayName 作者名称
     * @param authorAvatarUrl 作者头像
     * @param content 正文
     * @param workoutSessionId 训练主键
     * @param workoutName 训练名称
     * @param workoutCompletedAt 训练完成时间
     * @param workoutDurationMinutes 训练时长
     * @param workoutTotalVolumeKg 总容量
     * @param workoutExerciseCount 动作数
     * @param workoutSetCount 完成组数
     * @param createdAt 发布时间
     */
    public record PostRow(Long id, Long authorUserId, String authorPublicId,
            String authorDisplayName, String authorAvatarUrl, String content,
            Long workoutSessionId, String workoutName, LocalDateTime workoutCompletedAt,
            Integer workoutDurationMinutes, BigDecimal workoutTotalVolumeKg,
            Integer workoutExerciseCount, Integer workoutSetCount, LocalDateTime createdAt) {
    }

    /**
     * 描述朋友圈图片或视频。
     *
     * @param id 附件主键
     * @param attachmentType 图片或视频类型
     * @param originalName 原文件名
     * @param mimeType 媒体类型
     * @param sizeBytes 文件大小
     * @param publicUrl 读取地址
     * @param posterUrl 视频首帧封面地址
     * @param displayOrder 显示顺序
     */
    public record PostAttachmentRow(Long id, String attachmentType,
            String originalName, String mimeType,
            Long sizeBytes, String publicUrl, String posterUrl, Integer displayOrder) {
    }

    /**
     * 描述点赞用户公开资料。
     *
     * @param publicId 公开 ID
     * @param displayName 展示名称
     * @param avatarUrl 头像地址
     */
    public record InteractionUserRow(String publicId, String displayName, String avatarUrl) {
    }

    /**
     * 描述一条朋友圈评论。
     *
     * @param id 评论主键
     * @param postId 帖子主键
     * @param authorUserId 作者主键
     * @param authorPublicId 作者公开 ID
     * @param authorDisplayName 作者名称
     * @param authorAvatarUrl 作者头像
     * @param content 正文
     * @param createdAt 发布时间
     */
    public record CommentRow(Long id, Long postId, Long authorUserId, String authorPublicId,
            String authorDisplayName, String authorAvatarUrl, String content, LocalDateTime createdAt) {
    }
}

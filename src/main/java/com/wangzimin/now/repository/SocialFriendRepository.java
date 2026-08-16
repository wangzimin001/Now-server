package com.wangzimin.now.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.wangzimin.now.domain.FriendRequestStatus;
import com.wangzimin.now.domain.FriendSortMode;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.domain.WorkoutStatus;

/**
 * 持久化公开用户资料、好友申请和双向好友关系。
 *
 * <p>好友关系以两条有方向的记录保存，使每个用户都能维护独立备注。
 * 申请表额外保存规范化用户对，数据库唯一约束负责阻止并发重复申请。</p>
 */
@Repository
public class SocialFriendRepository {

    private final JdbcClient jdbcClient;

    /**
     * 创建好友数据仓储。
     *
     * @param jdbcClient Spring JDBC 客户端
     */
    public SocialFriendRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 按公开 ID 精确查询启用账号。
     *
     * @param publicId 用户对外展示的稳定 ID
     * @return 可空的用户资料
     */
    public Optional<SocialUserRow> findUserByPublicId(String publicId) {
        return jdbcClient.sql("""
                        SELECT id, public_id AS publicId, username, display_name AS displayName,
                               avatar_url AS avatarUrl
                        FROM app_user
                        WHERE public_id = :publicId AND enabled = TRUE
                        """)
                .param("publicId", publicId)
                .query(SocialUserRow.class)
                .optional();
    }

    /**
     * 按数据库主键查询启用账号。
     *
     * @param userId 用户主键
     * @return 可空的用户资料
     */
    public Optional<SocialUserRow> findUserById(Long userId) {
        return jdbcClient.sql("""
                        SELECT id, public_id AS publicId, username, display_name AS displayName,
                               avatar_url AS avatarUrl
                        FROM app_user
                        WHERE id = :userId AND enabled = TRUE
                        """)
                .param("userId", userId)
                .query(SocialUserRow.class)
                .optional();
    }

    /**
     * 判断两个账号之间是否仍存在好友关系。
     *
     * @param userId 当前用户
     * @param friendUserId 对方用户
     * @return 存在有向好友记录时为真
     */
    public boolean areFriends(Long userId, Long friendUserId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM friendship
                        WHERE user_id = :userId AND friend_user_id = :friendUserId
                        """)
                .param("userId", userId)
                .param("friendUserId", friendUserId)
                .query(Integer.class)
                .single() > 0;
    }

    /**
     * 查询一对用户现有的唯一好友申请。
     *
     * @param pairLowUserId 较小用户主键
     * @param pairHighUserId 较大用户主键
     * @return 可空申请行
     */
    public Optional<FriendRequestRow> findRequestByPair(Long pairLowUserId, Long pairHighUserId) {
        return jdbcClient.sql(requestSelect() + """
                        WHERE fr.pair_low_user_id = :pairLowUserId
                          AND fr.pair_high_user_id = :pairHighUserId
                        """)
                .param("pairLowUserId", pairLowUserId)
                .param("pairHighUserId", pairHighUserId)
                .query(FriendRequestRow.class)
                .optional();
    }

    /**
     * 按主键查询好友申请及双方资料。
     *
     * @param requestId 好友申请主键
     * @return 可空申请行
     */
    public Optional<FriendRequestRow> findRequestById(Long requestId) {
        return jdbcClient.sql(requestSelect() + " WHERE fr.id = :requestId")
                .param("requestId", requestId)
                .query(FriendRequestRow.class)
                .optional();
    }

    /**
     * 创建一条待处理好友申请。
     *
     * @param requesterUserId 发起人
     * @param recipientUserId 接收人
     * @param pairLowUserId 较小用户主键
     * @param pairHighUserId 较大用户主键
     * @param message 可空验证消息
     * @return 新申请主键
     */
    public long insertRequest(Long requesterUserId, Long recipientUserId, Long pairLowUserId,
            Long pairHighUserId, String message) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO friend_request
                            (requester_user_id, recipient_user_id, pair_low_user_id,
                             pair_high_user_id, request_message, status)
                        VALUES
                            (:requesterUserId, :recipientUserId, :pairLowUserId,
                             :pairHighUserId, :message, :status)
                        """)
                .param("requesterUserId", requesterUserId)
                .param("recipientUserId", recipientUserId)
                .param("pairLowUserId", pairLowUserId)
                .param("pairHighUserId", pairHighUserId)
                .param("message", message)
                .param("status", FriendRequestStatus.PENDING.databaseValue())
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(SystemText.FRIEND_REQUEST_KEY_MISSING.value());
        }
        return key.longValue();
    }

    /**
     * 将历史申请重置为由当前用户发起的新待处理申请。
     *
     * @param requestId 现有申请主键
     * @param requesterUserId 新发起人
     * @param recipientUserId 新接收人
     * @param message 新验证消息
     */
    public void reopenRequest(Long requestId, Long requesterUserId, Long recipientUserId, String message) {
        jdbcClient.sql("""
                        UPDATE friend_request
                        SET requester_user_id = :requesterUserId,
                            recipient_user_id = :recipientUserId,
                            request_message = :message,
                            status = :status,
                            created_at = CURRENT_TIMESTAMP,
                            updated_at = CURRENT_TIMESTAMP
                        WHERE id = :requestId
                        """)
                .param("requestId", requestId)
                .param("requesterUserId", requesterUserId)
                .param("recipientUserId", recipientUserId)
                .param("message", message)
                .param("status", FriendRequestStatus.PENDING.databaseValue())
                .update();
    }

    /**
     * 仅在申请仍待处理时更新状态。
     *
     * @param requestId 申请主键
     * @param status 目标终态
     * @return 实际更新行数
     */
    public int updateRequestStatus(Long requestId, FriendRequestStatus status) {
        return jdbcClient.sql("""
                        UPDATE friend_request
                        SET status = :status, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :requestId AND status = :pendingStatus
                        """)
                .param("requestId", requestId)
                .param("status", status.databaseValue())
                .param("pendingStatus", FriendRequestStatus.PENDING.databaseValue())
                .update();
    }

    /**
     * 查询与当前用户有关的所有好友申请。
     *
     * <p>前端同时展示收到和发出的申请，按最近处理时间倒序排列。</p>
     *
     * @param userId 当前用户
     * @return 申请列表
     */
    public List<FriendRequestRow> listRequests(Long userId) {
        return jdbcClient.sql(requestSelect() + """
                        WHERE fr.requester_user_id = :userId OR fr.recipient_user_id = :userId
                        ORDER BY fr.updated_at DESC, fr.id DESC
                        """)
                .param("userId", userId)
                .query(FriendRequestRow.class)
                .list();
    }

    /**
     * 统计当前用户尚未处理的好友申请。
     *
     * @param userId 当前用户主键
     * @return 收到且状态仍为待处理的申请数
     */
    public int countIncomingPendingRequests(Long userId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM friend_request
                        WHERE recipient_user_id = :userId AND status = :status
                        """)
                .param("userId", userId)
                .param("status", FriendRequestStatus.PENDING.databaseValue())
                .query(Integer.class)
                .single();
    }

    /**
     * 幂等写入一个方向的好友关系。
     *
     * @param userId 关系所有者
     * @param friendUserId 好友用户
     */
    public void insertFriendship(Long userId, Long friendUserId) {
        jdbcClient.sql("""
                        INSERT IGNORE INTO friendship (user_id, friend_user_id)
                        VALUES (:userId, :friendUserId)
                        """)
                .param("userId", userId)
                .param("friendUserId", friendUserId)
                .update();
    }

    /**
     * 删除双方各自持有的好友关系。
     *
     * @param userId 当前用户
     * @param friendUserId 好友用户
     * @return 被删除的有向记录数量
     */
    public int deleteFriendshipPair(Long userId, Long friendUserId) {
        return jdbcClient.sql("""
                        DELETE FROM friendship
                        WHERE (user_id = :userId AND friend_user_id = :friendUserId)
                           OR (user_id = :friendUserId AND friend_user_id = :userId)
                        """)
                .param("userId", userId)
                .param("friendUserId", friendUserId)
                .update();
    }

    /**
     * 更新当前用户为好友设置的备注。
     *
     * @param userId 当前用户
     * @param friendUserId 好友用户
     * @param remark 可空备注
     * @return 更新行数
     */
    public int updateRemark(Long userId, Long friendUserId, String remark) {
        return jdbcClient.sql("""
                        UPDATE friendship
                        SET remark = :remark
                        WHERE user_id = :userId AND friend_user_id = :friendUserId
                        """)
                .param("userId", userId)
                .param("friendUserId", friendUserId)
                .param("remark", remark)
                .update();
    }

    /**
     * 查询好友资料，并按名称或最近一次训练排序。
     *
     * @param userId 当前用户
     * @param sortMode 排序方式
     * @return 带最近训练时间的好友列表
     */
    public List<FriendRow> listFriends(Long userId, FriendSortMode sortMode) {
        String orderClause = sortMode == FriendSortMode.RECENT_ACTIVITY
                ? "lastWorkoutAt IS NULL, lastWorkoutAt DESC, effectiveName ASC"
                : "effectiveName ASC, u.public_id ASC";
        return jdbcClient.sql("""
                        SELECT u.id, u.public_id AS publicId, u.username,
                               u.display_name AS displayName, u.avatar_url AS avatarUrl,
                               f.remark, COALESCE(NULLIF(f.remark, ''), u.display_name) AS effectiveName,
                               MAX(ws.ended_at) AS lastWorkoutAt, f.created_at AS friendshipSince
                        FROM friendship f
                        JOIN app_user u ON u.id = f.friend_user_id AND u.enabled = TRUE
                        LEFT JOIN workout_session ws
                               ON ws.owner_user_id = u.id AND ws.status = :completedStatus
                        WHERE f.user_id = :userId
                        GROUP BY u.id, u.public_id, u.username, u.display_name, u.avatar_url,
                                 f.remark, f.created_at
                        """ + "ORDER BY " + orderClause)
                .param("userId", userId)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .query(FriendRow.class)
                .list();
    }

    /**
     * 查询好友最近完成的训练摘要。
     *
     * @param friendUserId 好友主键
     * @param limit 最大返回数量
     * @return 训练摘要列表
     */
    public List<FriendWorkoutRow> listFriendWorkouts(Long friendUserId, int limit) {
        return jdbcClient.sql("""
                        SELECT ws.id, ws.name_snapshot AS name, ws.ended_at AS completedAt,
                               ws.duration_minutes AS durationMinutes,
                               ws.total_volume_kg AS totalVolumeKg,
                               COUNT(DISTINCT se.id) AS exerciseCount,
                               COUNT(sr.id) AS setCount
                        FROM workout_session ws
                        LEFT JOIN session_exercise se ON se.session_id = ws.id
                        LEFT JOIN set_record sr ON sr.session_exercise_id = se.id
                                                   AND sr.status = :completedStatus
                        WHERE ws.owner_user_id = :friendUserId AND ws.status = :completedStatus
                        GROUP BY ws.id, ws.name_snapshot, ws.ended_at,
                                 ws.duration_minutes, ws.total_volume_kg
                        ORDER BY ws.ended_at DESC
                        LIMIT :limit
                        """)
                .param("friendUserId", friendUserId)
                .param("completedStatus", WorkoutStatus.COMPLETED.databaseValue())
                .param("limit", limit)
                .query(FriendWorkoutRow.class)
                .list();
    }

    /**
     * 复用申请查询的字段和联表定义。
     *
     * @return 不含 WHERE 条件的 SQL 片段
     */
    private String requestSelect() {
        return """
                SELECT fr.id, fr.requester_user_id AS requesterUserId,
                       requester.public_id AS requesterPublicId,
                       requester.display_name AS requesterDisplayName,
                       requester.avatar_url AS requesterAvatarUrl,
                       fr.recipient_user_id AS recipientUserId,
                       recipient.public_id AS recipientPublicId,
                       recipient.display_name AS recipientDisplayName,
                       recipient.avatar_url AS recipientAvatarUrl,
                       fr.request_message AS requestMessage, fr.status,
                       fr.created_at AS createdAt, fr.updated_at AS updatedAt
                FROM friend_request fr
                JOIN app_user requester ON requester.id = fr.requester_user_id
                JOIN app_user recipient ON recipient.id = fr.recipient_user_id
                """;
    }

    /**
     * 描述可用于社交业务的最小用户资料。
     *
     * @param id 数据库主键
     * @param publicId 公开 ID
     * @param username 登录名
     * @param displayName 展示名称
     * @param avatarUrl 可空头像地址
     */
    public record SocialUserRow(Long id, String publicId, String username, String displayName, String avatarUrl) {
    }

    /**
     * 描述好友申请及双方展示资料。
     *
     * @param id 申请主键
     * @param requesterUserId 发起人主键
     * @param requesterPublicId 发起人公开 ID
     * @param requesterDisplayName 发起人名称
     * @param requesterAvatarUrl 发起人头像
     * @param recipientUserId 接收人主键
     * @param recipientPublicId 接收人公开 ID
     * @param recipientDisplayName 接收人名称
     * @param recipientAvatarUrl 接收人头像
     * @param requestMessage 验证消息
     * @param status 当前状态
     * @param createdAt 创建时间
     * @param updatedAt 最近处理时间
     */
    public record FriendRequestRow(Long id, Long requesterUserId, String requesterPublicId,
            String requesterDisplayName, String requesterAvatarUrl, Long recipientUserId,
            String recipientPublicId, String recipientDisplayName, String recipientAvatarUrl,
            String requestMessage, String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /**
     * 描述一个好友列表项。
     *
     * @param id 好友主键
     * @param publicId 好友公开 ID
     * @param username 登录名
     * @param displayName 原始展示名称
     * @param avatarUrl 头像地址
     * @param remark 当前用户设置的备注
     * @param effectiveName 实际展示名称
     * @param lastWorkoutAt 最近完成训练时间
     * @param friendshipSince 成为好友时间
     */
    public record FriendRow(Long id, String publicId, String username, String displayName,
            String avatarUrl, String remark, String effectiveName, LocalDateTime lastWorkoutAt,
            LocalDateTime friendshipSince) {
    }

    /**
     * 描述可向好友展示的训练摘要。
     *
     * @param id 训练主键
     * @param name 训练名称
     * @param completedAt 完成时间
     * @param durationMinutes 时长
     * @param totalVolumeKg 总容量
     * @param exerciseCount 动作数量
     * @param setCount 完成组数
     */
    public record FriendWorkoutRow(Long id, String name, LocalDateTime completedAt,
            Integer durationMinutes, BigDecimal totalVolumeKg, Integer exerciseCount, Integer setCount) {
    }
}

package com.wangzimin.now.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.domain.SocialConversationType;
import com.wangzimin.now.domain.SocialMemberRole;
import com.wangzimin.now.domain.SocialMessageType;
import com.wangzimin.now.domain.SystemText;

/**
 * 持久化私聊、群聊、成员、消息、已读位置和附件元数据。
 *
 * <p>私聊与群聊共享统一会话主表，成员表负责权限和退出状态。
 * 消息按自增主键分页，移动端既可读取最近消息，也可使用 afterId 短轮询增量。</p>
 */
@Repository
public class SocialConversationRepository {

    private final JdbcClient jdbcClient;

    /**
     * 创建会话仓储。
     *
     * @param jdbcClient Spring JDBC 客户端
     */
    public SocialConversationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 查找一对用户已有的私聊会话。
     *
     * @param lowUserId 较小用户主键
     * @param highUserId 较大用户主键
     * @return 可空会话主键
     */
    public Optional<Long> findDirectConversationId(Long lowUserId, Long highUserId) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM social_conversation
                        WHERE conversation_type = :conversationType
                          AND direct_low_user_id = :lowUserId
                          AND direct_high_user_id = :highUserId
                          AND dissolved_at IS NULL
                        """)
                .param("conversationType", SocialConversationType.DIRECT.databaseValue())
                .param("lowUserId", lowUserId)
                .param("highUserId", highUserId)
                .query(Long.class)
                .optional();
    }

    /**
     * 创建私聊会话主记录。
     *
     * @param lowUserId 较小用户主键
     * @param highUserId 较大用户主键
     * @return 新会话主键
     */
    public long insertDirectConversation(Long lowUserId, Long highUserId) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO social_conversation
                            (conversation_type, direct_low_user_id, direct_high_user_id)
                        VALUES (:conversationType, :lowUserId, :highUserId)
                        """)
                .param("conversationType", SocialConversationType.DIRECT.databaseValue())
                .param("lowUserId", lowUserId)
                .param("highUserId", highUserId)
                .update(keyHolder, "id");
        return requiredKey(keyHolder, SystemText.CONVERSATION_KEY_MISSING);
    }

    /**
     * 创建群聊会话主记录。
     *
     * @param ownerUserId 群主
     * @param name 群名称
     * @return 新会话主键
     */
    public long insertGroupConversation(Long ownerUserId, String name) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO social_conversation (conversation_type, name, owner_user_id)
                        VALUES (:conversationType, :name, :ownerUserId)
                        """)
                .param("conversationType", SocialConversationType.GROUP.databaseValue())
                .param("name", name)
                .param("ownerUserId", ownerUserId)
                .update(keyHolder, "id");
        return requiredKey(keyHolder, SystemText.CONVERSATION_KEY_MISSING);
    }

    /**
     * 新增或重新激活会话成员。
     *
     * @param conversationId 会话主键
     * @param userId 用户主键
     * @param role 成员角色
     */
    public void upsertMember(Long conversationId, Long userId, SocialMemberRole role) {
        jdbcClient.sql("""
                        INSERT INTO social_conversation_member
                            (conversation_id, user_id, member_role, joined_at, left_at)
                        VALUES (:conversationId, :userId, :memberRole, CURRENT_TIMESTAMP, NULL)
                        ON DUPLICATE KEY UPDATE
                            member_role = VALUES(member_role),
                            joined_at = CURRENT_TIMESTAMP,
                            left_at = NULL
                        """)
                .param("conversationId", conversationId)
                .param("userId", userId)
                .param("memberRole", role.databaseValue())
                .update();
    }

    /**
     * 查询当前用户对会话的有效访问关系。
     *
     * @param conversationId 会话主键
     * @param userId 当前用户主键
     * @return 可空访问快照
     */
    public Optional<ConversationAccessRow> findAccess(Long conversationId, Long userId) {
        return jdbcClient.sql("""
                        SELECT c.id, c.conversation_type AS conversationType, c.name,
                               c.owner_user_id AS ownerUserId, m.member_role AS memberRole,
                               CASE WHEN c.direct_low_user_id = :userId
                                    THEN c.direct_high_user_id ELSE c.direct_low_user_id END AS counterpartUserId
                        FROM social_conversation c
                        JOIN social_conversation_member m
                          ON m.conversation_id = c.id AND m.user_id = :userId AND m.left_at IS NULL
                        WHERE c.id = :conversationId AND c.dissolved_at IS NULL
                        """)
                .param("conversationId", conversationId)
                .param("userId", userId)
                .query(ConversationAccessRow.class)
                .optional();
    }

    /**
     * 查询当前用户的全部活跃会话概览。
     *
     * <p>私聊标题优先使用当前用户为好友设置的备注；群聊显示群名。
     * 未读数只统计其他成员发送且尚未删除的消息。</p>
     *
     * @param userId 当前用户主键
     * @return 按最后活动时间倒序排列的会话
     */
    public List<ConversationRow> listConversations(Long userId) {
        return jdbcClient.sql("""
                        SELECT c.id, c.conversation_type AS conversationType,
                               CASE WHEN c.conversation_type = :directType
                                    THEN COALESCE(NULLIF(f.remark, ''), other_user.display_name)
                                    ELSE c.name END AS displayName,
                               CASE WHEN c.conversation_type = :directType
                                    THEN other_user.avatar_url ELSE NULL END AS avatarUrl,
                               CASE WHEN c.conversation_type = :directType
                                    THEN other_user.public_id ELSE NULL END AS counterpartPublicId,
                               (SELECT sm.message_type FROM social_message sm
                                WHERE sm.conversation_id = c.id AND sm.deleted_at IS NULL
                                ORDER BY sm.id DESC LIMIT 1) AS lastMessageType,
                               (SELECT sm.message_text FROM social_message sm
                                WHERE sm.conversation_id = c.id AND sm.deleted_at IS NULL
                                ORDER BY sm.id DESC LIMIT 1) AS lastMessageText,
                               (SELECT sm.created_at FROM social_message sm
                                WHERE sm.conversation_id = c.id AND sm.deleted_at IS NULL
                                ORDER BY sm.id DESC LIMIT 1) AS lastMessageAt,
                               (SELECT COUNT(*) FROM social_message unread
                                WHERE unread.conversation_id = c.id
                                  AND unread.id > COALESCE(self_member.last_read_message_id, 0)
                                  AND unread.sender_user_id <> :userId
                                  AND unread.deleted_at IS NULL) AS unreadCount,
                               (SELECT COUNT(*) FROM social_conversation_member active_member
                                WHERE active_member.conversation_id = c.id
                                  AND active_member.left_at IS NULL) AS memberCount
                        FROM social_conversation_member self_member
                        JOIN social_conversation c
                          ON c.id = self_member.conversation_id AND c.dissolved_at IS NULL
                        LEFT JOIN social_conversation_member other_member
                          ON c.conversation_type = :directType
                         AND other_member.conversation_id = c.id
                         AND other_member.user_id <> :userId
                         AND other_member.left_at IS NULL
                        LEFT JOIN app_user other_user ON other_user.id = other_member.user_id
                        LEFT JOIN friendship f
                          ON f.user_id = :userId AND f.friend_user_id = other_user.id
                        WHERE self_member.user_id = :userId AND self_member.left_at IS NULL
                        ORDER BY COALESCE(lastMessageAt, c.updated_at) DESC, c.id DESC
                        """)
                .param("userId", userId)
                .param("directType", SocialConversationType.DIRECT.databaseValue())
                .query(ConversationRow.class)
                .list();
    }

    /**
     * 汇总当前用户全部活跃会话中的未读消息。
     *
     * @param userId 当前用户主键
     * @return 其他成员发送且超过已读位置的消息数
     */
    public int countUnreadMessages(Long userId) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM social_conversation_member self_member
                        JOIN social_conversation c
                          ON c.id = self_member.conversation_id AND c.dissolved_at IS NULL
                        JOIN social_message unread
                          ON unread.conversation_id = c.id
                         AND unread.id > COALESCE(self_member.last_read_message_id, 0)
                         AND unread.sender_user_id <> :userId
                         AND unread.deleted_at IS NULL
                        WHERE self_member.user_id = :userId AND self_member.left_at IS NULL
                        """)
                .param("userId", userId)
                .query(Integer.class)
                .single();
    }

    /**
     * 查询会话中的当前活跃成员。
     *
     * @param conversationId 会话主键
     * @return 成员资料列表
     */
    public List<ConversationMemberRow> listMembers(Long conversationId) {
        return jdbcClient.sql("""
                        SELECT u.id AS userId, u.public_id AS publicId,
                               u.display_name AS displayName, u.avatar_url AS avatarUrl,
                               m.member_role AS memberRole, m.muted, m.joined_at AS joinedAt
                        FROM social_conversation_member m
                        JOIN app_user u ON u.id = m.user_id AND u.enabled = TRUE
                        WHERE m.conversation_id = :conversationId AND m.left_at IS NULL
                        ORDER BY CASE m.member_role
                                     WHEN :ownerRole THEN 0
                                     WHEN :adminRole THEN 1
                                     ELSE 2
                                 END,
                                 m.joined_at ASC
                        """)
                .param("conversationId", conversationId)
                .param("ownerRole", SocialMemberRole.OWNER.databaseValue())
                .param("adminRole", SocialMemberRole.ADMIN.databaseValue())
                .query(ConversationMemberRow.class)
                .list();
    }

    /**
     * 查询最近一页消息，并按时间正序返回给聊天界面。
     *
     * @param conversationId 会话主键
     * @param beforeId 只读取该主键之前的消息
     * @param limit 最大数量
     * @return 消息列表
     */
    public List<MessageRow> listMessagesBefore(Long conversationId, Long beforeId, int limit) {
        return jdbcClient.sql("""
                        SELECT page.id, page.conversation_id AS conversationId,
                               page.sender_user_id AS senderUserId, u.public_id AS senderPublicId,
                               u.display_name AS senderDisplayName, u.avatar_url AS senderAvatarUrl,
                               page.message_type AS messageType, page.message_text AS messageText,
                               page.attachment_id AS attachmentId, a.original_name AS attachmentName,
                               a.mime_type AS attachmentMimeType, a.size_bytes AS attachmentSizeBytes,
                               a.public_url AS attachmentUrl, a.poster_url AS attachmentPosterUrl,
                               page.created_at AS createdAt
                        FROM (
                            SELECT m.id, m.conversation_id, m.sender_user_id, m.message_type,
                                   m.message_text, m.attachment_id, m.created_at
                            FROM social_message m
                            WHERE m.conversation_id = :conversationId
                              AND m.id < :beforeId AND m.deleted_at IS NULL
                            ORDER BY m.id DESC
                            LIMIT :limit
                        ) page
                        JOIN app_user u ON u.id = page.sender_user_id
                        LEFT JOIN social_attachment a ON a.id = page.attachment_id
                        ORDER BY page.id ASC
                        """)
                .param("conversationId", conversationId)
                .param("beforeId", beforeId)
                .param("limit", limit)
                .query(MessageRow.class)
                .list();
    }

    /**
     * 查询指定消息主键之后的新消息，用于聊天页短轮询。
     *
     * @param conversationId 会话主键
     * @param afterId 客户端已有的最后消息主键
     * @param limit 最大数量
     * @return 按主键正序排列的增量消息
     */
    public List<MessageRow> listMessagesAfter(Long conversationId, Long afterId, int limit) {
        return jdbcClient.sql(messageSelect() + """
                        WHERE m.conversation_id = :conversationId
                          AND m.id > :afterId AND m.deleted_at IS NULL
                        ORDER BY m.id ASC
                        LIMIT :limit
                        """)
                .param("conversationId", conversationId)
                .param("afterId", afterId)
                .param("limit", limit)
                .query(MessageRow.class)
                .list();
    }

    /**
     * 插入一条聊天消息。
     *
     * @param conversationId 会话主键
     * @param senderUserId 发送人
     * @param type 消息类型
     * @param text 可空消息文本
     * @param attachmentId 可空附件主键
     * @return 新消息主键
     */
    public long insertMessage(Long conversationId, Long senderUserId, SocialMessageType type,
            String text, Long attachmentId) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO social_message
                            (conversation_id, sender_user_id, message_type, message_text, attachment_id)
                        VALUES (:conversationId, :senderUserId, :messageType, :messageText, :attachmentId)
                        """)
                .param("conversationId", conversationId)
                .param("senderUserId", senderUserId)
                .param("messageType", type.name())
                .param("messageText", text)
                .param("attachmentId", attachmentId)
                .update(keyHolder, "id");
        return requiredKey(keyHolder, SystemText.MESSAGE_KEY_MISSING);
    }

    /**
     * 按主键查询一条可见消息。
     *
     * @param messageId 消息主键
     * @return 可空消息
     */
    public Optional<MessageRow> findMessage(Long messageId) {
        return jdbcClient.sql(messageSelect() + " WHERE m.id = :messageId AND m.deleted_at IS NULL")
                .param("messageId", messageId)
                .query(MessageRow.class)
                .optional();
    }

    /**
     * 把成员已读位置单调推进到指定消息。
     *
     * @param conversationId 会话主键
     * @param userId 当前用户
     * @param messageId 已读到的消息主键
     */
    public void markRead(Long conversationId, Long userId, Long messageId) {
        jdbcClient.sql("""
                        UPDATE social_conversation_member
                        SET last_read_message_id = CASE
                                WHEN last_read_message_id IS NULL OR last_read_message_id < :messageId
                                THEN :messageId ELSE last_read_message_id END
                        WHERE conversation_id = :conversationId
                          AND user_id = :userId AND left_at IS NULL
                        """)
                .param("conversationId", conversationId)
                .param("userId", userId)
                .param("messageId", messageId)
                .update();
    }

    /**
     * 刷新会话排序所依赖的最近活动时间。
     *
     * @param conversationId 会话主键
     */
    public void touchConversation(Long conversationId) {
        jdbcClient.sql("""
                        UPDATE social_conversation
                        SET updated_at = CURRENT_TIMESTAMP
                        WHERE id = :conversationId
                        """)
                .param("conversationId", conversationId)
                .update();
    }

    /**
     * 保存已经写入磁盘的附件元数据。
     *
     * @param ownerUserId 上传者
     * @param type 附件类型
     * @param originalName 原文件名
     * @param storedName 随机存储名
     * @param mimeType 媒体类型
     * @param sizeBytes 文件大小
     * @param publicUrl 读取地址
     * @param posterUrl 视频首帧封面地址
     * @return 新附件主键
     */
    public long insertAttachment(Long ownerUserId, SocialAttachmentType type, String originalName,
            String storedName, String mimeType, long sizeBytes, String publicUrl, String posterUrl) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO social_attachment
                            (owner_user_id, attachment_type, original_name, stored_name,
                             mime_type, size_bytes, public_url, poster_url)
                        VALUES
                            (:ownerUserId, :attachmentType, :originalName, :storedName,
                             :mimeType, :sizeBytes, :publicUrl, :posterUrl)
                        """)
                .param("ownerUserId", ownerUserId)
                .param("attachmentType", type.name())
                .param("originalName", originalName)
                .param("storedName", storedName)
                .param("mimeType", mimeType)
                .param("sizeBytes", sizeBytes)
                .param("publicUrl", publicUrl)
                .param("posterUrl", posterUrl)
                .update(keyHolder, "id");
        return requiredKey(keyHolder, SystemText.ATTACHMENT_KEY_MISSING);
    }

    /**
     * 按主键查询附件元数据。
     *
     * @param attachmentId 附件主键
     * @return 可空附件
     */
    public Optional<AttachmentRow> findAttachment(Long attachmentId) {
        return jdbcClient.sql(attachmentSelect() + " WHERE id = :attachmentId")
                .param("attachmentId", attachmentId)
                .query(AttachmentRow.class)
                .optional();
    }

    /**
     * 按随机存储名查询公开读取所需元数据。
     *
     * @param storedName 随机存储名
     * @return 可空附件
     */
    public Optional<AttachmentRow> findAttachmentByStoredName(String storedName) {
        return jdbcClient.sql(attachmentSelect() + " WHERE stored_name = :storedName")
                .param("storedName", storedName)
                .query(AttachmentRow.class)
                .optional();
    }

    /**
     * 修改群聊名称。
     *
     * @param conversationId 群聊主键
     * @param name 新名称
     */
    public void renameGroup(Long conversationId, String name) {
        jdbcClient.sql("""
                        UPDATE social_conversation
                        SET name = :name, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :conversationId AND conversation_type = :groupType
                        """)
                .param("conversationId", conversationId)
                .param("name", name)
                .param("groupType", SocialConversationType.GROUP.databaseValue())
                .update();
    }

    /**
     * 将一个群成员标记为已退出。
     *
     * @param conversationId 群聊主键
     * @param userId 成员主键
     * @return 更新行数
     */
    public int leaveMember(Long conversationId, Long userId) {
        return jdbcClient.sql("""
                        UPDATE social_conversation_member
                        SET left_at = CURRENT_TIMESTAMP
                        WHERE conversation_id = :conversationId
                          AND user_id = :userId AND left_at IS NULL
                        """)
                .param("conversationId", conversationId)
                .param("userId", userId)
                .update();
    }

    /**
     * 解散群聊并让全部成员退出。
     *
     * @param conversationId 群聊主键
     */
    public void dissolveGroup(Long conversationId) {
        jdbcClient.sql("""
                        UPDATE social_conversation
                        SET dissolved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                        WHERE id = :conversationId AND conversation_type = :groupType
                        """)
                .param("conversationId", conversationId)
                .param("groupType", SocialConversationType.GROUP.databaseValue())
                .update();
        jdbcClient.sql("""
                        UPDATE social_conversation_member
                        SET left_at = CURRENT_TIMESTAMP
                        WHERE conversation_id = :conversationId AND left_at IS NULL
                        """)
                .param("conversationId", conversationId)
                .update();
    }

    /**
     * 返回消息查询统一字段和附件联表。
     *
     * @return 不带筛选条件的 SQL
     */
    private String messageSelect() {
        return """
                SELECT m.id, m.conversation_id AS conversationId,
                       m.sender_user_id AS senderUserId, u.public_id AS senderPublicId,
                       u.display_name AS senderDisplayName, u.avatar_url AS senderAvatarUrl,
                       m.message_type AS messageType, m.message_text AS messageText,
                       m.attachment_id AS attachmentId, a.original_name AS attachmentName,
                       a.mime_type AS attachmentMimeType, a.size_bytes AS attachmentSizeBytes,
                       a.public_url AS attachmentUrl, a.poster_url AS attachmentPosterUrl,
                       m.created_at AS createdAt
                FROM social_message m
                JOIN app_user u ON u.id = m.sender_user_id
                LEFT JOIN social_attachment a ON a.id = m.attachment_id
                """;
    }

    /**
     * 返回附件查询的统一字段。
     *
     * @return 不带筛选条件的 SQL
     */
    private String attachmentSelect() {
        return """
                SELECT id, owner_user_id AS ownerUserId, attachment_type AS attachmentType,
                       original_name AS originalName, stored_name AS storedName,
                       mime_type AS mimeType, size_bytes AS sizeBytes, public_url AS publicUrl,
                       poster_url AS posterUrl,
                       created_at AS createdAt
                FROM social_attachment
                """;
    }

    /**
     * 从键持有器读取数据库生成主键。
     *
     * @param keyHolder Spring 主键持有器
     * @param missingText 缺少主键时的稳定错误文本
     * @return 数据库主键
     */
    private long requiredKey(GeneratedKeyHolder keyHolder, SystemText missingText) {
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(missingText.value());
        }
        return key.longValue();
    }

    /**
     * 描述当前用户对会话的访问快照。
     *
     * @param id 会话主键
     * @param conversationType 私聊或群聊
     * @param name 群聊名称
     * @param ownerUserId 群主主键
     * @param memberRole 当前用户角色
     * @param counterpartUserId 私聊对方主键
     */
    public record ConversationAccessRow(Long id, String conversationType, String name,
            Long ownerUserId, String memberRole, Long counterpartUserId) {
    }

    /**
     * 描述消息列表中的会话概览。
     *
     * @param id 会话主键
     * @param conversationType 会话类型
     * @param displayName 展示名称
     * @param avatarUrl 私聊头像
     * @param counterpartPublicId 私聊对方公开 ID
     * @param lastMessageType 最后消息类型
     * @param lastMessageText 最后消息文本
     * @param lastMessageAt 最后消息时间
     * @param unreadCount 未读消息数
     * @param memberCount 当前成员数
     */
    public record ConversationRow(Long id, String conversationType, String displayName,
            String avatarUrl, String counterpartPublicId, String lastMessageType,
            String lastMessageText, LocalDateTime lastMessageAt, Integer unreadCount, Integer memberCount) {
    }

    /**
     * 描述会话成员公开资料和角色。
     *
     * @param userId 用户主键
     * @param publicId 公开 ID
     * @param displayName 展示名称
     * @param avatarUrl 头像地址
     * @param memberRole 成员角色
     * @param muted 是否免打扰
     * @param joinedAt 加入时间
     */
    public record ConversationMemberRow(Long userId, String publicId, String displayName,
            String avatarUrl, String memberRole, Boolean muted, LocalDateTime joinedAt) {
    }

    /**
     * 描述一条聊天消息及其可空附件。
     *
     * @param id 消息主键
     * @param conversationId 会话主键
     * @param senderUserId 发送人主键
     * @param senderPublicId 发送人公开 ID
     * @param senderDisplayName 发送人名称
     * @param senderAvatarUrl 发送人头像
     * @param messageType 消息类型
     * @param messageText 消息文本
     * @param attachmentId 附件主键
     * @param attachmentName 原文件名
     * @param attachmentMimeType 媒体类型
     * @param attachmentSizeBytes 文件大小
     * @param attachmentUrl 读取地址
     * @param attachmentPosterUrl 视频首帧封面地址
     * @param createdAt 发送时间
     */
    public record MessageRow(Long id, Long conversationId, Long senderUserId,
            String senderPublicId, String senderDisplayName, String senderAvatarUrl,
            String messageType, String messageText, Long attachmentId, String attachmentName,
            String attachmentMimeType, Long attachmentSizeBytes, String attachmentUrl,
            String attachmentPosterUrl, LocalDateTime createdAt) {
    }

    /**
     * 描述一个已持久化附件。
     *
     * @param id 附件主键
     * @param ownerUserId 上传人
     * @param attachmentType 图片或文件
     * @param originalName 原文件名
     * @param storedName 随机存储名
     * @param mimeType 媒体类型
     * @param sizeBytes 文件大小
     * @param publicUrl 读取地址
     * @param posterUrl 视频首帧封面地址；非视频为空
     * @param createdAt 上传时间
     */
    public record AttachmentRow(Long id, Long ownerUserId, String attachmentType,
            String originalName, String storedName, String mimeType, Long sizeBytes,
            String publicUrl, String posterUrl, LocalDateTime createdAt) {
    }
}

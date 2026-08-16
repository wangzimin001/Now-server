package com.wangzimin.now.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.domain.SocialConversationType;
import com.wangzimin.now.domain.SocialMemberRole;
import com.wangzimin.now.domain.SocialMessageType;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.domain.ValidationRule;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.repository.SocialConversationRepository.ConversationAccessRow;
import com.wangzimin.now.repository.SocialConversationRepository.ConversationMemberRow;
import com.wangzimin.now.repository.SocialConversationRepository.ConversationRow;
import com.wangzimin.now.repository.SocialConversationRepository.MessageRow;
import com.wangzimin.now.repository.SocialFriendRepository.SocialUserRow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 实现私聊、群聊、消息校验、已读位置和群成员生命周期。
 *
 * <p>私聊发送时实时检查好友关系，删除好友不会抹除历史消息。
 * 群聊由群主维护名称和成员，普通成员可退出但群主必须先解散。</p>
 */
@Service
public class SocialConversationService {

    private final SocialConversationRepository repository;
    private final SocialFriendService friendService;

    /**
     * 创建会话服务。
     *
     * @param repository 会话仓储
     * @param friendService 好友关系服务
     */
    public SocialConversationService(SocialConversationRepository repository,
            SocialFriendService friendService) {
        this.repository = repository;
        this.friendService = friendService;
    }

    /**
     * 查询当前用户全部活跃会话。
     *
     * @param userId 当前用户主键
     * @return 消息首页会话列表
     */
    public List<ConversationRow> conversations(Long userId) {
        return repository.listConversations(userId);
    }

    /**
     * 获取或创建当前用户与好友的一对一会话。
     *
     * @param userId 当前用户主键
     * @param friendPublicId 好友公开 ID
     * @return 私聊会话概览
     */
    @Transactional
    public ConversationView directConversation(Long userId, String friendPublicId) {
        SocialUserRow friend = friendService.requireFriend(userId, friendPublicId);
        Long lowUserId = Math.min(userId, friend.id());
        Long highUserId = Math.max(userId, friend.id());
        Long conversationId = repository.findDirectConversationId(lowUserId, highUserId).orElse(null);
        if (conversationId == null) {
            try {
                conversationId = repository.insertDirectConversation(lowUserId, highUserId);
                repository.upsertMember(conversationId, lowUserId, SocialMemberRole.MEMBER);
                repository.upsertMember(conversationId, highUserId, SocialMemberRole.MEMBER);
            } catch (DuplicateKeyException exception) {
                conversationId = repository.findDirectConversationId(lowUserId, highUserId)
                        .orElseThrow(ApiErrorCode.CONVERSATION_NOT_FOUND::exception);
            }
        }
        return new ConversationView(conversationId, SocialConversationType.DIRECT,
                friend.displayName(), friend.avatarUrl(), friend.publicId());
    }

    /**
     * 查询历史消息或指定主键之后的增量消息。
     *
     * @param userId 当前用户主键
     * @param conversationId 会话主键
     * @param beforeId 历史分页上界
     * @param afterId 增量轮询下界
     * @param requestedLimit 客户端请求数量
     * @return 按发送顺序排列的消息
     */
    public List<MessageRow> messages(Long userId, Long conversationId, Long beforeId,
            Long afterId, Integer requestedLimit) {
        requireAccess(conversationId, userId);
        int limit = normalizeLimit(requestedLimit);
        if (afterId != null && afterId > BusinessRule.ZERO_COUNT.longValue()) {
            return repository.listMessagesAfter(conversationId, afterId, limit);
        }
        long upperBound = beforeId == null || beforeId <= BusinessRule.ZERO_COUNT.longValue()
                ? Long.MAX_VALUE : beforeId;
        return repository.listMessagesBefore(conversationId, upperBound, limit);
    }

    /**
     * 校验并发送一条文本、表情、图片、视频或文件消息。
     *
     * @param userId 当前用户主键
     * @param conversationId 会话主键
     * @param request 消息内容
     * @return 已保存消息
     */
    @Transactional
    public MessageRow sendMessage(Long userId, Long conversationId, SendMessageRequest request) {
        ConversationAccessRow access = requireAccess(conversationId, userId);
        enforceDirectFriendship(userId, access);
        SocialMessageType type = parseMessageType(request.type(), false);
        String text = normalizeOptional(request.text());
        AttachmentRow attachment = validateMessageContent(userId, type, text, request.attachmentId());
        Long messageId = repository.insertMessage(conversationId, userId, type, text,
                attachment == null ? null : attachment.id());
        repository.touchConversation(conversationId);
        repository.markRead(conversationId, userId, messageId);
        return repository.findMessage(messageId)
                .orElseThrow(ApiErrorCode.CONVERSATION_NOT_FOUND::exception);
    }

    /**
     * 将会话已读位置推进到客户端确认的最后一条消息。
     *
     * @param userId 当前用户主键
     * @param conversationId 会话主键
     * @param request 最后已读消息
     */
    public void markRead(Long userId, Long conversationId, MarkReadRequest request) {
        requireAccess(conversationId, userId);
        MessageRow message = repository.findMessage(request.messageId())
                .orElseThrow(ApiErrorCode.CONVERSATION_NOT_FOUND::exception);
        if (!conversationId.equals(message.conversationId())) {
            throw ApiErrorCode.CONVERSATION_NOT_FOUND.exception();
        }
        repository.markRead(conversationId, userId, message.id());
    }

    /**
     * 创建一个由当前用户担任群主的群聊。
     *
     * @param userId 当前用户主键
     * @param request 群名称和初始好友 ID
     * @return 新群聊详情
     */
    @Transactional
    public GroupView createGroup(Long userId, CreateGroupRequest request) {
        String name = normalizeGroupName(request.name());
        Set<String> publicIds = normalizedPublicIds(request.memberPublicIds());
        int memberCount = publicIds.size() + BusinessRule.COLLECTION_MIN_SIZE.value();
        validateGroupMemberCount(memberCount);
        List<SocialUserRow> members = publicIds.stream()
                .map(publicId -> friendService.requireFriend(userId, publicId))
                .toList();
        long conversationId = repository.insertGroupConversation(userId, name);
        repository.upsertMember(conversationId, userId, SocialMemberRole.OWNER);
        members.forEach(member -> repository.upsertMember(
                conversationId, member.id(), SocialMemberRole.MEMBER));
        return group(userId, conversationId);
    }

    /**
     * 查询当前成员可见的群聊资料和成员列表。
     *
     * @param userId 当前用户主键
     * @param conversationId 群聊主键
     * @return 群聊详情
     */
    public GroupView group(Long userId, Long conversationId) {
        ConversationAccessRow access = requireGroupAccess(conversationId, userId);
        return new GroupView(access.id(), access.name(), access.ownerUserId(),
                access.memberRole(), repository.listMembers(conversationId));
    }

    /**
     * 由群主修改群聊名称。
     *
     * @param userId 当前用户主键
     * @param conversationId 群聊主键
     * @param request 新群名
     */
    public void renameGroup(Long userId, Long conversationId, RenameGroupRequest request) {
        requireOwner(conversationId, userId);
        repository.renameGroup(conversationId, normalizeGroupName(request.name()));
    }

    /**
     * 由群主邀请自己的好友加入群聊。
     *
     * @param userId 当前用户主键
     * @param conversationId 群聊主键
     * @param request 待添加好友公开 ID
     */
    @Transactional
    public void addGroupMembers(Long userId, Long conversationId, AddGroupMembersRequest request) {
        requireOwner(conversationId, userId);
        Set<String> publicIds = normalizedPublicIds(request.memberPublicIds());
        int memberCount = repository.listMembers(conversationId).size() + publicIds.size();
        validateGroupMemberCount(memberCount);
        for (String publicId : publicIds) {
            SocialUserRow friend = friendService.requireFriend(userId, publicId);
            boolean alreadyMember = repository.listMembers(conversationId).stream()
                    .anyMatch(member -> member.userId().equals(friend.id()));
            if (alreadyMember) {
                throw ApiErrorCode.GROUP_MEMBER_EXISTS.exception();
            }
            repository.upsertMember(conversationId, friend.id(), SocialMemberRole.MEMBER);
            insertSystemMessage(conversationId, userId,
                    friend.displayName() + SystemText.SYSTEM_MEMBER_JOINED.value());
        }
    }

    /**
     * 由群主移除一个普通群成员。
     *
     * @param userId 当前用户主键
     * @param conversationId 群聊主键
     * @param memberPublicId 待移除成员公开 ID
     */
    @Transactional
    public void removeGroupMember(Long userId, Long conversationId, String memberPublicId) {
        ConversationAccessRow access = requireOwner(conversationId, userId);
        SocialUserRow member = friendService.requireUser(memberPublicId);
        if (member.id().equals(access.ownerUserId())) {
            throw ApiErrorCode.GROUP_OWNER_REQUIRED.exception();
        }
        if (repository.leaveMember(conversationId, member.id()) == 0) {
            throw ApiErrorCode.GROUP_MEMBER_NOT_FOUND.exception();
        }
        insertSystemMessage(conversationId, userId,
                member.displayName() + SystemText.SYSTEM_MEMBER_REMOVED.value());
    }

    /**
     * 普通成员主动退出群聊。
     *
     * @param userId 当前用户主键
     * @param conversationId 群聊主键
     */
    @Transactional
    public void leaveGroup(Long userId, Long conversationId) {
        ConversationAccessRow access = requireGroupAccess(conversationId, userId);
        if (userId.equals(access.ownerUserId())) {
            throw ApiErrorCode.GROUP_OWNER_CANNOT_LEAVE.exception();
        }
        SocialUserRow user = friendService.requireUser(repository.listMembers(conversationId).stream()
                .filter(member -> member.userId().equals(userId))
                .findFirst().orElseThrow(ApiErrorCode.GROUP_MEMBER_NOT_FOUND::exception).publicId());
        insertSystemMessage(conversationId, userId,
                user.displayName() + SystemText.SYSTEM_MEMBER_LEFT.value());
        repository.leaveMember(conversationId, userId);
    }

    /**
     * 群主解散群聊并关闭全部成员访问。
     *
     * @param userId 当前用户主键
     * @param conversationId 群聊主键
     */
    @Transactional
    public void dissolveGroup(Long userId, Long conversationId) {
        requireOwner(conversationId, userId);
        repository.dissolveGroup(conversationId);
    }

    /**
     * 要求当前用户是会话中的活跃成员。
     *
     * @param conversationId 会话主键
     * @param userId 当前用户主键
     * @return 会话访问快照
     */
    private ConversationAccessRow requireAccess(Long conversationId, Long userId) {
        return repository.findAccess(conversationId, userId)
                .orElseThrow(ApiErrorCode.CONVERSATION_MEMBER_REQUIRED::exception);
    }

    /**
     * 要求会话为群聊且当前用户仍在群内。
     *
     * @param conversationId 会话主键
     * @param userId 当前用户主键
     * @return 群聊访问快照
     */
    private ConversationAccessRow requireGroupAccess(Long conversationId, Long userId) {
        ConversationAccessRow access = requireAccess(conversationId, userId);
        if (!SocialConversationType.GROUP.name().equals(access.conversationType())) {
            throw ApiErrorCode.GROUP_NOT_FOUND.exception();
        }
        return access;
    }

    /**
     * 要求当前用户是指定群聊的群主。
     *
     * @param conversationId 群聊主键
     * @param userId 当前用户主键
     * @return 群聊访问快照
     */
    private ConversationAccessRow requireOwner(Long conversationId, Long userId) {
        ConversationAccessRow access = requireGroupAccess(conversationId, userId);
        if (!userId.equals(access.ownerUserId())
                || !SocialMemberRole.OWNER.name().equals(access.memberRole())) {
            throw ApiErrorCode.GROUP_OWNER_REQUIRED.exception();
        }
        return access;
    }

    /**
     * 删除好友后阻止继续发送私聊，但历史消息仍可读取。
     *
     * @param userId 当前用户主键
     * @param access 会话访问快照
     */
    private void enforceDirectFriendship(Long userId, ConversationAccessRow access) {
        if (SocialConversationType.DIRECT.name().equals(access.conversationType())
                && !friendService.areFriends(userId, access.counterpartUserId())) {
            throw ApiErrorCode.FRIEND_NOT_FOUND.exception();
        }
    }

    /**
     * 校验消息文本和附件组合，并返回已确认归属的附件。
     *
     * @param userId 当前用户主键
     * @param type 消息类型
     * @param text 规范化文本
     * @param attachmentId 可空附件主键
     * @return 可空附件
     */
    private AttachmentRow validateMessageContent(Long userId, SocialMessageType type,
            String text, Long attachmentId) {
        if (type.requiresText() && (text == null || text.isBlank())) {
            throw ApiErrorCode.MESSAGE_CONTENT_REQUIRED.exception();
        }
        if (!type.requiresAttachment()) {
            return null;
        }
        if (attachmentId == null) {
            throw ApiErrorCode.MESSAGE_CONTENT_REQUIRED.exception();
        }
        AttachmentRow attachment = repository.findAttachment(attachmentId)
                .orElseThrow(ApiErrorCode.ATTACHMENT_NOT_FOUND::exception);
        if (!userId.equals(attachment.ownerUserId())) {
            throw ApiErrorCode.ATTACHMENT_FORBIDDEN.exception();
        }
        SocialAttachmentType expected = type.requiredAttachmentType();
        if (!expected.name().equals(attachment.attachmentType())) {
            throw ApiErrorCode.ATTACHMENT_TYPE_MISMATCH.exception();
        }
        return attachment;
    }

    /**
     * 解析客户端消息类型，并禁止客户端伪造系统消息。
     *
     * @param value 客户端类型文本
     * @param allowSystem 是否允许系统类型
     * @return 消息类型
     */
    private SocialMessageType parseMessageType(String value, boolean allowSystem) {
        try {
            SocialMessageType type = SocialMessageType.valueOf(value.trim().toUpperCase());
            if (!allowSystem && type == SocialMessageType.SYSTEM) {
                throw ApiErrorCode.MESSAGE_TYPE_INVALID.exception();
            }
            return type;
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw ApiErrorCode.MESSAGE_TYPE_INVALID.exception();
        }
    }

    /**
     * 写入服务端生成的群事件消息。
     *
     * @param conversationId 群聊主键
     * @param actorUserId 操作者
     * @param text 事件文案
     */
    private void insertSystemMessage(Long conversationId, Long actorUserId, String text) {
        repository.insertMessage(conversationId, actorUserId, SocialMessageType.SYSTEM, text, null);
        repository.touchConversation(conversationId);
    }

    /**
     * 将分页数量限制在统一范围。
     *
     * @param requestedLimit 客户端请求数量
     * @return 安全分页数量
     */
    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit < BusinessRule.COLLECTION_MIN_SIZE.value()) {
            return BusinessRule.SOCIAL_DEFAULT_PAGE_LIMIT.value();
        }
        return Math.min(requestedLimit, BusinessRule.SOCIAL_MAX_PAGE_LIMIT.value());
    }

    /**
     * 清理可空文本，空白值统一为 null。
     *
     * @param value 原始文本
     * @return 规范化文本
     */
    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 校验并规范化群聊名称。
     *
     * @param value 原始名称
     * @return 非空群聊名称
     */
    private String normalizeGroupName(String value) {
        if (value == null || value.isBlank()) {
            throw ApiErrorCode.GROUP_NAME_REQUIRED.exception();
        }
        return value.trim();
    }

    /**
     * 对成员公开 ID 去空、转大写并去重。
     *
     * @param values 原始公开 ID 列表
     * @return 保持选择顺序的唯一集合
     */
    private Set<String> normalizedPublicIds(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase()).forEach(result::add);
        return result;
    }

    /**
     * 校验群聊总成员数的上下界。
     *
     * @param count 包含群主的总人数
     */
    private void validateGroupMemberCount(int count) {
        if (count < BusinessRule.SOCIAL_GROUP_MIN_MEMBER_COUNT.value()
                || count > BusinessRule.SOCIAL_GROUP_MAX_MEMBER_COUNT.value()) {
            throw ApiErrorCode.GROUP_MEMBER_LIMIT.exception();
        }
    }

    /**
     * 描述私聊创建结果。
     *
     * @param id 会话主键
     * @param type 会话类型
     * @param displayName 对方名称
     * @param avatarUrl 对方头像
     * @param counterpartPublicId 对方公开 ID
     */
    public record ConversationView(Long id, SocialConversationType type, String displayName,
            String avatarUrl, String counterpartPublicId) {
    }

    /**
     * 描述发送消息请求。
     *
     * @param type 文本、表情、图片、视频或文件
     * @param text 可空消息文本
     * @param attachmentId 可空附件主键
     */
    public record SendMessageRequest(
            @NotBlank String type,
            @Size(max = ValidationRule.SOCIAL_MESSAGE_MAX_LENGTH) String text,
            Long attachmentId) {
    }

    /**
     * 描述已读位置更新请求。
     *
     * @param messageId 最后一条已读消息主键
     */
    public record MarkReadRequest(@jakarta.validation.constraints.NotNull Long messageId) {
    }

    /**
     * 描述创建群聊请求。
     *
     * @param name 群聊名称
     * @param memberPublicIds 初始好友公开 ID
     */
    public record CreateGroupRequest(
            @NotBlank @Size(min = ValidationRule.SOCIAL_GROUP_NAME_MIN_LENGTH,
                    max = ValidationRule.SOCIAL_GROUP_NAME_MAX_LENGTH) String name,
            List<String> memberPublicIds) {
    }

    /**
     * 描述群聊重命名请求。
     *
     * @param name 新群聊名称
     */
    public record RenameGroupRequest(
            @NotBlank @Size(min = ValidationRule.SOCIAL_GROUP_NAME_MIN_LENGTH,
                    max = ValidationRule.SOCIAL_GROUP_NAME_MAX_LENGTH) String name) {
    }

    /**
     * 描述群聊新增成员请求。
     *
     * @param memberPublicIds 待添加好友公开 ID
     */
    public record AddGroupMembersRequest(List<String> memberPublicIds) {
    }

    /**
     * 描述群聊详情。
     *
     * @param id 群聊主键
     * @param name 群聊名称
     * @param ownerUserId 群主主键
     * @param currentMemberRole 当前用户角色
     * @param members 当前成员
     */
    public record GroupView(Long id, String name, Long ownerUserId, String currentMemberRole,
            List<ConversationMemberRow> members) {
    }
}

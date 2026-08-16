package com.wangzimin.now.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.FriendRequestStatus;
import com.wangzimin.now.domain.FriendSortMode;
import com.wangzimin.now.domain.SocialRelationshipState;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.domain.ValidationRule;
import com.wangzimin.now.repository.SocialFriendRepository;
import com.wangzimin.now.repository.SocialFriendRepository.FriendRequestRow;
import com.wangzimin.now.repository.SocialFriendRepository.FriendRow;
import com.wangzimin.now.repository.SocialFriendRepository.FriendWorkoutRow;
import com.wangzimin.now.repository.SocialFriendRepository.SocialUserRow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理用户搜索、好友申请、双向好友关系和好友训练摘要。
 *
 * <p>服务层持有申请状态机和权限判断；仓储层只负责原子 SQL 操作。
 * 接受申请的状态更新与双向关系写入共享事务，避免出现单向好友。</p>
 */
@Service
public class SocialFriendService {

    private final SocialFriendRepository repository;

    /**
     * 创建好友业务服务。
     *
     * @param repository 好友数据仓储
     */
    public SocialFriendService(SocialFriendRepository repository) {
        this.repository = repository;
    }

    /**
     * 通过公开 ID 查询用户，并补充与当前用户的关系状态。
     *
     * @param userId 当前用户主键
     * @param publicId 待查询公开 ID
     * @return 可用于搜索结果页的用户资料
     */
    public UserSearchResult findUser(Long userId, String publicId) {
        SocialUserRow target = requireUser(publicId);
        SocialRelationshipState relationship = relationship(userId, target.id());
        return new UserSearchResult(target.publicId(), target.displayName(), target.avatarUrl(), relationship);
    }

    /**
     * 创建或重新打开一条好友申请。
     *
     * @param userId 当前用户主键
     * @param request 目标公开 ID 和验证消息
     * @return 最新好友申请
     */
    @Transactional
    public FriendRequestView sendRequest(Long userId, CreateFriendRequest request) {
        SocialUserRow target = requireUser(request.publicId());
        if (userId.equals(target.id())) {
            throw ApiErrorCode.FRIEND_SELF_REQUEST.exception();
        }
        if (repository.areFriends(userId, target.id())) {
            throw ApiErrorCode.FRIEND_ALREADY_EXISTS.exception();
        }
        Long pairLow = Math.min(userId, target.id());
        Long pairHigh = Math.max(userId, target.id());
        String message = normalizeOptional(request.message());
        FriendRequestRow existing = repository.findRequestByPair(pairLow, pairHigh).orElse(null);
        if (existing != null && FriendRequestStatus.PENDING.name().equals(existing.status())) {
            if (userId.equals(existing.requesterUserId())) {
                throw ApiErrorCode.FRIEND_REQUEST_EXISTS.exception();
            }
            throw ApiErrorCode.FRIEND_REQUEST_INCOMING.exception();
        }
        long requestId;
        try {
            if (existing == null) {
                requestId = repository.insertRequest(userId, target.id(), pairLow, pairHigh, message);
            } else {
                repository.reopenRequest(existing.id(), userId, target.id(), message);
                requestId = existing.id();
            }
        } catch (DuplicateKeyException exception) {
            throw ApiErrorCode.FRIEND_REQUEST_EXISTS.exception();
        }
        return toView(repository.findRequestById(requestId)
                .orElseThrow(ApiErrorCode.FRIEND_REQUEST_NOT_FOUND::exception), userId);
    }

    /**
     * 查询当前用户收到和发出的好友申请。
     *
     * @param userId 当前用户主键
     * @return 按最近更新时间倒序排列的申请
     */
    public List<FriendRequestView> listRequests(Long userId) {
        return repository.listRequests(userId).stream().map(row -> toView(row, userId)).toList();
    }

    /**
     * 接受发给当前用户的待处理申请并建立双向好友关系。
     *
     * @param userId 当前用户主键
     * @param requestId 申请主键
     * @return 已接受申请
     */
    @Transactional
    public FriendRequestView acceptRequest(Long userId, Long requestId) {
        FriendRequestRow row = requirePendingRequest(requestId);
        if (!userId.equals(row.recipientUserId())) {
            throw ApiErrorCode.FRIEND_REQUEST_FORBIDDEN.exception();
        }
        repository.insertFriendship(row.requesterUserId(), row.recipientUserId());
        repository.insertFriendship(row.recipientUserId(), row.requesterUserId());
        updatePendingStatus(row.id(), FriendRequestStatus.ACCEPTED);
        return toView(repository.findRequestById(row.id())
                .orElseThrow(ApiErrorCode.FRIEND_REQUEST_NOT_FOUND::exception), userId);
    }

    /**
     * 拒绝发给当前用户的待处理申请。
     *
     * @param userId 当前用户主键
     * @param requestId 申请主键
     */
    public void rejectRequest(Long userId, Long requestId) {
        FriendRequestRow row = requirePendingRequest(requestId);
        if (!userId.equals(row.recipientUserId())) {
            throw ApiErrorCode.FRIEND_REQUEST_FORBIDDEN.exception();
        }
        updatePendingStatus(row.id(), FriendRequestStatus.REJECTED);
    }

    /**
     * 取消当前用户自己发出的待处理申请。
     *
     * @param userId 当前用户主键
     * @param requestId 申请主键
     */
    public void cancelRequest(Long userId, Long requestId) {
        FriendRequestRow row = requirePendingRequest(requestId);
        if (!userId.equals(row.requesterUserId())) {
            throw ApiErrorCode.FRIEND_REQUEST_FORBIDDEN.exception();
        }
        updatePendingStatus(row.id(), FriendRequestStatus.CANCELED);
    }

    /**
     * 查询好友列表并应用客户端选择的排序方式。
     *
     * @param userId 当前用户主键
     * @param sortValue 排序参数
     * @return 好友列表
     */
    public List<FriendRow> listFriends(Long userId, String sortValue) {
        return repository.listFriends(userId, FriendSortMode.from(sortValue));
    }

    /**
     * 为好友更新当前用户私有的显示备注。
     *
     * @param userId 当前用户主键
     * @param publicId 好友公开 ID
     * @param request 新备注
     */
    public void updateRemark(Long userId, String publicId, UpdateRemarkRequest request) {
        SocialUserRow friend = requireFriend(userId, publicId);
        if (repository.updateRemark(userId, friend.id(), normalizeOptional(request.remark())) == 0) {
            throw ApiErrorCode.FRIEND_NOT_FOUND.exception();
        }
    }

    /**
     * 删除双方好友关系；既有聊天记录继续保留但不能再发送私聊。
     *
     * @param userId 当前用户主键
     * @param publicId 好友公开 ID
     */
    @Transactional
    public void deleteFriend(Long userId, String publicId) {
        SocialUserRow friend = requireUser(publicId);
        if (repository.deleteFriendshipPair(userId, friend.id()) == 0) {
            throw ApiErrorCode.FRIEND_NOT_FOUND.exception();
        }
    }

    /**
     * 查询好友最近的完成训练；非好友不能读取。
     *
     * @param userId 当前用户主键
     * @param publicId 好友公开 ID
     * @return 训练摘要列表
     */
    public List<FriendWorkoutRow> friendWorkouts(Long userId, String publicId) {
        SocialUserRow friend = requireFriend(userId, publicId);
        return repository.listFriendWorkouts(friend.id(), BusinessRule.SOCIAL_DEFAULT_PAGE_LIMIT.value());
    }

    /**
     * 要求目标用户存在且与当前用户保持好友关系。
     *
     * @param userId 当前用户主键
     * @param publicId 目标公开 ID
     * @return 好友资料
     */
    public SocialUserRow requireFriend(Long userId, String publicId) {
        SocialUserRow friend = requireUser(publicId);
        if (!repository.areFriends(userId, friend.id())) {
            throw ApiErrorCode.FRIEND_NOT_FOUND.exception();
        }
        return friend;
    }

    /**
     * 判断两个用户当前是否为好友。
     *
     * @param userId 当前用户主键
     * @param friendUserId 对方主键
     * @return 好友关系存在时为真
     */
    public boolean areFriends(Long userId, Long friendUserId) {
        return repository.areFriends(userId, friendUserId);
    }

    /**
     * 将公开 ID 规范化并要求启用用户存在。
     *
     * @param publicId 公开 ID
     * @return 用户资料
     */
    public SocialUserRow requireUser(String publicId) {
        String normalized = publicId == null ? SystemText.EMPTY.value() : publicId.trim().toUpperCase();
        return repository.findUserByPublicId(normalized)
                .orElseThrow(ApiErrorCode.SOCIAL_USER_NOT_FOUND::exception);
    }

    /**
     * 计算搜索结果中的关系状态。
     *
     * @param userId 当前用户主键
     * @param targetUserId 目标用户主键
     * @return 关系状态
     */
    private SocialRelationshipState relationship(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            return SocialRelationshipState.SELF;
        }
        if (repository.areFriends(userId, targetUserId)) {
            return SocialRelationshipState.FRIEND;
        }
        FriendRequestRow request = repository.findRequestByPair(
                Math.min(userId, targetUserId), Math.max(userId, targetUserId)).orElse(null);
        if (request != null && FriendRequestStatus.PENDING.name().equals(request.status())) {
            return userId.equals(request.requesterUserId())
                    ? SocialRelationshipState.OUTGOING_PENDING
                    : SocialRelationshipState.INCOMING_PENDING;
        }
        return SocialRelationshipState.NONE;
    }

    /**
     * 要求申请存在并仍处于待处理状态。
     *
     * @param requestId 申请主键
     * @return 待处理申请
     */
    private FriendRequestRow requirePendingRequest(Long requestId) {
        FriendRequestRow row = repository.findRequestById(requestId)
                .orElseThrow(ApiErrorCode.FRIEND_REQUEST_NOT_FOUND::exception);
        if (!FriendRequestStatus.PENDING.name().equals(row.status())) {
            throw ApiErrorCode.FRIEND_REQUEST_NOT_PENDING.exception();
        }
        return row;
    }

    /**
     * 以乐观条件更新待处理申请，防止重复点击产生并发状态覆盖。
     *
     * @param requestId 申请主键
     * @param status 目标状态
     */
    private void updatePendingStatus(Long requestId, FriendRequestStatus status) {
        if (repository.updateRequestStatus(requestId, status) == 0) {
            throw ApiErrorCode.FRIEND_REQUEST_NOT_PENDING.exception();
        }
    }

    /**
     * 将数据库申请行转换为面向当前用户的视图。
     *
     * @param row 申请行
     * @param userId 当前用户主键
     * @return 申请视图
     */
    private FriendRequestView toView(FriendRequestRow row, Long userId) {
        boolean incoming = userId.equals(row.recipientUserId());
        return new FriendRequestView(row.id(), incoming, row.status(), row.requestMessage(),
                incoming ? row.requesterPublicId() : row.recipientPublicId(),
                incoming ? row.requesterDisplayName() : row.recipientDisplayName(),
                incoming ? row.requesterAvatarUrl() : row.recipientAvatarUrl(),
                row.createdAt(), row.updatedAt());
    }

    /**
     * 清理可空短文本，空白值统一保存为 null。
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
     * 描述通过公开 ID 发起好友申请的请求。
     *
     * @param publicId 目标用户公开 ID
     * @param message 可空验证消息
     */
    public record CreateFriendRequest(
            @NotBlank @Size(max = ValidationRule.PUBLIC_ID_MAX_LENGTH) String publicId,
            @Size(max = ValidationRule.FRIEND_REQUEST_MESSAGE_MAX_LENGTH) String message) {
    }

    /**
     * 描述好友备注更新请求。
     *
     * @param remark 可空备注，清空后恢复对方原名
     */
    public record UpdateRemarkRequest(@Size(max = ValidationRule.FRIEND_REMARK_MAX_LENGTH) String remark) {
    }

    /**
     * 描述按公开 ID 搜索到的用户。
     *
     * @param publicId 公开 ID
     * @param displayName 展示名称
     * @param avatarUrl 头像地址
     * @param relationship 与当前用户的关系
     */
    public record UserSearchResult(String publicId, String displayName, String avatarUrl,
            SocialRelationshipState relationship) {
    }

    /**
     * 描述好友申请列表项。
     *
     * @param id 申请主键
     * @param incoming 是否为收到的申请
     * @param status 申请状态
     * @param message 验证消息
     * @param counterpartPublicId 对方公开 ID
     * @param counterpartDisplayName 对方展示名称
     * @param counterpartAvatarUrl 对方头像
     * @param createdAt 创建时间
     * @param updatedAt 最近处理时间
     */
    public record FriendRequestView(Long id, boolean incoming, String status, String message,
            String counterpartPublicId, String counterpartDisplayName, String counterpartAvatarUrl,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }
}

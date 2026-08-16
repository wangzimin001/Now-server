package com.wangzimin.now.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialFriendRepository;
import com.wangzimin.now.repository.SocialNotificationRepository;
import com.wangzimin.now.repository.SocialNotificationRepository.NotificationRow;

/**
 * 组合聊天、好友申请和朋友圈互动的统一未读状态。
 *
 * <p>底部社交菜单只请求这一接口即可获得三类数量，朋友圈互动列表仍由独立查询返回。</p>
 */
@Service
public class SocialNotificationService {

    private final SocialConversationRepository conversationRepository;
    private final SocialFriendRepository friendRepository;
    private final SocialNotificationRepository notificationRepository;

    /**
     * 创建社交通知服务。
     *
     * @param conversationRepository 会话仓储
     * @param friendRepository 好友仓储
     * @param notificationRepository 朋友圈互动仓储
     */
    public SocialNotificationService(SocialConversationRepository conversationRepository,
            SocialFriendRepository friendRepository,
            SocialNotificationRepository notificationRepository) {
        this.conversationRepository = conversationRepository;
        this.friendRepository = friendRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * 返回当前用户三类未读数量及最近互动头像。
     *
     * @param userId 当前用户主键
     * @return 可直接驱动社交徽标的汇总
     */
    public UnreadSummary unreadSummary(Long userId) {
        int messageCount = conversationRepository.countUnreadMessages(userId);
        int friendRequestCount = friendRepository.countIncomingPendingRequests(userId);
        int interactionCount = notificationRepository.countUnread(userId);
        int totalCount = messageCount + friendRequestCount + interactionCount;
        List<String> avatars = notificationRepository.listUnreadActorAvatars(userId,
                BusinessRule.SOCIAL_NOTIFICATION_AVATAR_PREVIEW_LIMIT.value());
        return new UnreadSummary(messageCount, friendRequestCount, interactionCount,
                totalCount, avatars);
    }

    /**
     * 查询最近一页朋友圈互动消息。
     *
     * @param userId 当前用户主键
     * @return 按互动时间倒序的通知
     */
    public List<NotificationRow> notifications(Long userId) {
        return notificationRepository.listNotifications(userId,
                BusinessRule.SOCIAL_MAX_PAGE_LIMIT.value());
    }

    /**
     * 将当前用户朋友圈互动全部标为已读。
     *
     * @param userId 当前用户主键
     */
    @Transactional
    public void markMomentNotificationsRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }

    /**
     * 描述底部社交菜单与三个页签共用的未读汇总。
     *
     * @param messageUnreadCount 未读聊天消息
     * @param friendRequestUnreadCount 待处理好友申请
     * @param momentInteractionUnreadCount 未读朋友圈互动
     * @param totalUnreadCount 三类总和
     * @param recentInteractionAvatarUrls 最近互动人头像
     */
    public record UnreadSummary(int messageUnreadCount, int friendRequestUnreadCount,
            int momentInteractionUnreadCount, int totalUnreadCount,
            List<String> recentInteractionAvatarUrls) {
    }
}

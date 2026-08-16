package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialFriendRepository;
import com.wangzimin.now.repository.SocialNotificationRepository;

class SocialNotificationServiceTest {

    /** 验证底部社交红点会汇总聊天、好友申请和朋友圈互动。 */
    @Test
    void unreadSummaryAggregatesAllSocialSources() {
        SocialConversationRepository conversationRepository = mock(SocialConversationRepository.class);
        SocialFriendRepository friendRepository = mock(SocialFriendRepository.class);
        SocialNotificationRepository notificationRepository = mock(SocialNotificationRepository.class);
        SocialNotificationService service = new SocialNotificationService(
                conversationRepository, friendRepository, notificationRepository);
        when(conversationRepository.countUnreadMessages(7L)).thenReturn(3);
        when(friendRepository.countIncomingPendingRequests(7L)).thenReturn(2);
        when(notificationRepository.countUnread(7L)).thenReturn(4);
        when(notificationRepository.listUnreadActorAvatars(7L,
                BusinessRule.SOCIAL_NOTIFICATION_AVATAR_PREVIEW_LIMIT.value()))
                .thenReturn(List.of("/avatar/one", "/avatar/two"));

        SocialNotificationService.UnreadSummary result = service.unreadSummary(7L);

        assertEquals(3, result.messageUnreadCount());
        assertEquals(2, result.friendRequestUnreadCount());
        assertEquals(4, result.momentInteractionUnreadCount());
        assertEquals(9, result.totalUnreadCount());
        assertEquals(List.of("/avatar/one", "/avatar/two"),
                result.recentInteractionAvatarUrls());
    }

    /** 验证打开朋友圈消息后会推进该用户全部互动的已读状态。 */
    @Test
    void markReadDelegatesToNotificationRepository() {
        SocialConversationRepository conversationRepository = mock(SocialConversationRepository.class);
        SocialFriendRepository friendRepository = mock(SocialFriendRepository.class);
        SocialNotificationRepository notificationRepository = mock(SocialNotificationRepository.class);
        SocialNotificationService service = new SocialNotificationService(
                conversationRepository, friendRepository, notificationRepository);

        service.markMomentNotificationsRead(11L);

        verify(notificationRepository).markAllRead(11L);
    }
}

package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.wangzimin.now.domain.SocialConversationType;
import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.domain.SocialMemberRole;
import com.wangzimin.now.domain.SocialMessageType;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.repository.SocialConversationRepository.ConversationAccessRow;
import com.wangzimin.now.repository.SocialConversationRepository.MessageRow;

class SocialConversationServiceTest {

    @Test
    void directConversationHistoryRemainsReadableButSendingRequiresCurrentFriendship() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        ConversationAccessRow access = new ConversationAccessRow(8L,
                SocialConversationType.DIRECT.name(), null, null,
                SocialMemberRole.MEMBER.name(), 2L);
        when(repository.findAccess(8L, 1L)).thenReturn(Optional.of(access));
        when(friendService.areFriends(1L, 2L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.sendMessage(1L, 8L,
                        new SocialConversationService.SendMessageRequest(
                                SocialMessageType.TEXT.name(), "继续训练", null)));

        assertEquals(404, exception.getStatusCode().value());
        verify(repository, never()).insertMessage(8L, 1L, SocialMessageType.TEXT, "继续训练", null);
    }

    /** 验证视频消息必须引用归当前用户所有的视频附件。 */
    @Test
    void videoMessageUsesVideoAttachmentType() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        ConversationAccessRow access = new ConversationAccessRow(8L,
                SocialConversationType.DIRECT.name(), null, null,
                SocialMemberRole.MEMBER.name(), 2L);
        AttachmentRow video = new AttachmentRow(12L, 1L, SocialAttachmentType.VIDEO.name(),
                "training.mp4", "stored-video", "video/mp4", 1024L,
                "/api/v1/social/files/stored-video",
                "/api/v1/social/files/stored-video/poster", LocalDateTime.now());
        MessageRow message = new MessageRow(19L, 8L, 1L, "N000000001", "训练者", null,
                SocialMessageType.VIDEO.name(), null, 12L, "training.mp4", "video/mp4",
                1024L, "/api/v1/social/files/stored-video",
                "/api/v1/social/files/stored-video/poster", LocalDateTime.now());
        when(repository.findAccess(8L, 1L)).thenReturn(Optional.of(access));
        when(friendService.areFriends(1L, 2L)).thenReturn(true);
        when(repository.findAttachment(12L)).thenReturn(Optional.of(video));
        when(repository.insertMessage(8L, 1L, SocialMessageType.VIDEO, null, 12L))
                .thenReturn(19L);
        when(repository.findMessage(19L)).thenReturn(Optional.of(message));

        MessageRow result = service.sendMessage(1L, 8L,
                new SocialConversationService.SendMessageRequest(
                        SocialMessageType.VIDEO.name(), null, 12L));

        assertEquals(SocialMessageType.VIDEO.name(), result.messageType());
        verify(repository).insertMessage(8L, 1L, SocialMessageType.VIDEO, null, 12L);
    }
}

package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.repository.SocialMomentRepository;
import com.wangzimin.now.repository.SocialNotificationRepository;
import com.wangzimin.now.repository.SocialMomentRepository.CommentRow;
import com.wangzimin.now.repository.SocialMomentRepository.InteractionUserRow;
import com.wangzimin.now.repository.SocialMomentRepository.PostRow;

class SocialMomentServiceTest {

    /** 验证互动预览按当前查看者解析并透传展示名。 */
    @Test
    void interactionPreviewUsesViewerResolvedNames() {
        SocialMomentRepository repository = mock(SocialMomentRepository.class);
        SocialConversationRepository attachmentRepository = mock(SocialConversationRepository.class);
        SocialMomentService service = new SocialMomentService(
                repository, attachmentRepository, mock(SocialNotificationRepository.class));
        PostRow post = new PostRow(7L, 2L, "N000000002", "原始昵称", null, "训练完成",
                null, null, null, null, null, null, null, LocalDateTime.now());
        InteractionUserRow like = new InteractionUserRow("N000000003", "卧推搭子", null);
        CommentRow comment = new CommentRow(11L, 7L, 3L, "N000000003",
                "卧推搭子", null, "动作很稳", LocalDateTime.now());
        int previewLimit = BusinessRule.SOCIAL_INTERACTION_PREVIEW_LIMIT.value();
        when(repository.listVisiblePosts(org.mockito.ArgumentMatchers.eq(1L), anyLong(), anyInt()))
                .thenReturn(List.of(post));
        when(repository.listLikes(7L, 1L, previewLimit)).thenReturn(List.of(like));
        when(repository.listComments(7L, 1L, previewLimit)).thenReturn(List.of(comment));
        when(repository.countLikes(7L)).thenReturn(1);
        when(repository.countComments(7L)).thenReturn(1);

        SocialMomentService.PostView result = service.feed(1L, null, null).get(0);

        assertEquals("卧推搭子", result.likes().get(0).displayName());
        assertEquals("卧推搭子", result.comments().get(0).authorDisplayName());
        verify(repository).listLikes(7L, 1L, previewLimit);
        verify(repository).listComments(7L, 1L, previewLimit);
    }

    /** 验证朋友圈图片不能引用其他用户上传的附件。 */
    @Test
    void postImagesMustBelongToPublishingUser() {
        SocialMomentRepository repository = mock(SocialMomentRepository.class);
        SocialConversationRepository attachmentRepository = mock(SocialConversationRepository.class);
        SocialMomentService service = new SocialMomentService(
                repository, attachmentRepository, mock(SocialNotificationRepository.class));
        AttachmentRow foreignImage = new AttachmentRow(9L, 2L,
                SocialAttachmentType.IMAGE.name(), "training.jpg", "stored", "image/jpeg",
                128L, "/api/v1/social/files/stored", null, LocalDateTime.now());
        when(attachmentRepository.findAttachment(9L)).thenReturn(Optional.of(foreignImage));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createPost(1L,
                        new SocialMomentService.CreatePostRequest("训练完成", List.of(9L), null)));

        assertEquals(403, exception.getStatusCode().value());
    }

    /** 验证一条仅包含视频的朋友圈可以正常写入关联。 */
    @Test
    void postAllowsOneOwnedVideo() {
        SocialMomentRepository repository = mock(SocialMomentRepository.class);
        SocialConversationRepository attachmentRepository = mock(SocialConversationRepository.class);
        SocialMomentService service = new SocialMomentService(
                repository, attachmentRepository, mock(SocialNotificationRepository.class));
        AttachmentRow video = new AttachmentRow(12L, 1L,
                SocialAttachmentType.VIDEO.name(), "training.mp4", "stored-video", "video/mp4",
                1024L, "/api/v1/social/files/stored-video",
                "/api/v1/social/files/stored-video/poster", LocalDateTime.now());
        PostRow post = new PostRow(17L, 1L, "N000000001", "训练者", null, "今日训练",
                null, null, null, null, null, null, null, LocalDateTime.now());
        when(attachmentRepository.findAttachment(12L)).thenReturn(Optional.of(video));
        when(repository.insertPost(1L, "今日训练", null)).thenReturn(17L);
        when(repository.findPost(17L)).thenReturn(Optional.of(post));

        SocialMomentService.PostView result = service.createPost(1L,
                new SocialMomentService.CreatePostRequest("今日训练", List.of(12L), null));

        assertEquals(17L, result.post().id());
        verify(repository).insertPostAttachment(17L, 12L, 0);
    }

    /** 验证朋友圈不能把图片和视频混在同一条动态中。 */
    @Test
    void postRejectsMixedImageAndVideoAttachments() {
        SocialMomentRepository repository = mock(SocialMomentRepository.class);
        SocialConversationRepository attachmentRepository = mock(SocialConversationRepository.class);
        SocialMomentService service = new SocialMomentService(
                repository, attachmentRepository, mock(SocialNotificationRepository.class));
        AttachmentRow image = new AttachmentRow(21L, 1L,
                SocialAttachmentType.IMAGE.name(), "training.jpg", "stored-image", "image/jpeg",
                512L, "/api/v1/social/files/stored-image", null, LocalDateTime.now());
        AttachmentRow video = new AttachmentRow(22L, 1L,
                SocialAttachmentType.VIDEO.name(), "training.mp4", "stored-video", "video/mp4",
                1024L, "/api/v1/social/files/stored-video",
                "/api/v1/social/files/stored-video/poster", LocalDateTime.now());
        when(attachmentRepository.findAttachment(21L)).thenReturn(Optional.of(image));
        when(attachmentRepository.findAttachment(22L)).thenReturn(Optional.of(video));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createPost(1L,
                        new SocialMomentService.CreatePostRequest(
                                "训练记录", List.of(21L, 22L), null)));

        assertEquals(ApiErrorCode.POST_MEDIA_MIXED.status(), exception.getStatusCode());
    }

    /** 验证一条朋友圈不能包含多个视频。 */
    @Test
    void postRejectsMoreThanOneVideo() {
        SocialMomentRepository repository = mock(SocialMomentRepository.class);
        SocialConversationRepository attachmentRepository = mock(SocialConversationRepository.class);
        SocialMomentService service = new SocialMomentService(
                repository, attachmentRepository, mock(SocialNotificationRepository.class));
        AttachmentRow first = new AttachmentRow(31L, 1L,
                SocialAttachmentType.VIDEO.name(), "first.mp4", "stored-first", "video/mp4",
                1024L, "/api/v1/social/files/stored-first",
                "/api/v1/social/files/stored-first/poster", LocalDateTime.now());
        AttachmentRow second = new AttachmentRow(32L, 1L,
                SocialAttachmentType.VIDEO.name(), "second.mp4", "stored-second", "video/mp4",
                1024L, "/api/v1/social/files/stored-second",
                "/api/v1/social/files/stored-second/poster", LocalDateTime.now());
        when(attachmentRepository.findAttachment(31L)).thenReturn(Optional.of(first));
        when(attachmentRepository.findAttachment(32L)).thenReturn(Optional.of(second));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.createPost(1L,
                        new SocialMomentService.CreatePostRequest(
                                "两段视频", List.of(31L, 32L), null)));

        assertEquals(ApiErrorCode.POST_VIDEO_LIMIT.status(), exception.getStatusCode());
    }

    /** 验证好友首次点赞会为帖子作者创建互动通知。 */
    @Test
    void firstLikeCreatesNotificationForPostAuthor() {
        SocialMomentRepository repository = mock(SocialMomentRepository.class);
        SocialConversationRepository attachmentRepository = mock(SocialConversationRepository.class);
        SocialNotificationRepository notificationRepository = mock(SocialNotificationRepository.class);
        SocialMomentService service = new SocialMomentService(
                repository, attachmentRepository, notificationRepository);
        PostRow post = new PostRow(51L, 8L, "N000000008", "作者", null, "训练完成",
                null, null, null, null, null, null, null, LocalDateTime.now());
        when(repository.canViewPost(9L, 51L)).thenReturn(true);
        when(repository.findPost(51L)).thenReturn(Optional.of(post));
        when(repository.addLike(51L, 9L)).thenReturn(1);

        service.like(9L, 51L);

        verify(notificationRepository).upsertLike(8L, 9L, 51L);
    }

    /** 验证好友评论会把评论主键写入帖子作者的互动通知。 */
    @Test
    void commentCreatesNotificationForPostAuthor() {
        SocialMomentRepository repository = mock(SocialMomentRepository.class);
        SocialConversationRepository attachmentRepository = mock(SocialConversationRepository.class);
        SocialNotificationRepository notificationRepository = mock(SocialNotificationRepository.class);
        SocialMomentService service = new SocialMomentService(
                repository, attachmentRepository, notificationRepository);
        PostRow post = new PostRow(61L, 12L, "N000000012", "作者", null, "深蹲训练",
                null, null, null, null, null, null, null, LocalDateTime.now());
        when(repository.canViewPost(13L, 61L)).thenReturn(true);
        when(repository.findPost(61L)).thenReturn(Optional.of(post));
        when(repository.insertComment(61L, 13L, "动作很稳")).thenReturn(71L);

        service.comment(13L, 61L,
                new SocialMomentService.CreateCommentRequest("动作很稳"));

        verify(notificationRepository).insertComment(12L, 13L, 61L, 71L);
    }
}

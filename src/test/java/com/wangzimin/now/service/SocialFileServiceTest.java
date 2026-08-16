package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;

class SocialFileServiceTest {

    @TempDir
    Path uploadRoot;

    @Test
    void avatarUploadRejectsNonImageBeforePersistingMetadata() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFileService service = new SocialFileService(
                repository, mock(VideoPosterService.class), uploadRoot.toString());
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "profile.txt", "text/plain", "not-an-image".getBytes());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> service.storeAvatar(7L, textFile));

        assertEquals(ApiErrorCode.AVATAR_IMAGE_REQUIRED.status(), error.getStatusCode());
        verify(repository, never()).insertAttachment(anyLong(), any(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), any());
    }

    @Test
    void avatarUploadRejectsSpoofedImageMediaType() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFileService service = new SocialFileService(
                repository, mock(VideoPosterService.class), uploadRoot.toString());
        MockMultipartFile fakeImage = new MockMultipartFile(
                "file", "profile.png", "image/png", "not-an-image".getBytes());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> service.storeAvatar(7L, fakeImage));

        assertEquals(ApiErrorCode.AVATAR_IMAGE_REQUIRED.status(), error.getStatusCode());
        verify(repository, never()).insertAttachment(anyLong(), any(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), any());
    }

    @Test
    void avatarUploadStoresImageAsSocialAttachment() throws IOException {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFileService service = new SocialFileService(
                repository, mock(VideoPosterService.class), uploadRoot.toString());
        byte[] imageBytes = createPngBytes();
        MockMultipartFile image = new MockMultipartFile(
                "file", "profile.png", "image/png", imageBytes);
        AttachmentRow stored = new AttachmentRow(31L, 7L, SocialAttachmentType.IMAGE.name(),
                "profile.png", "stored-avatar", "image/png", (long) imageBytes.length,
                "/api/v1/social/files/stored-avatar", null, null);
        when(repository.insertAttachment(anyLong(), any(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), any())).thenReturn(31L);
        when(repository.findAttachment(31L)).thenReturn(Optional.of(stored));

        AttachmentRow result = service.storeAvatar(7L, image);

        assertEquals(stored, result);
        assertEquals(SocialAttachmentType.IMAGE.name(), result.attachmentType());
    }

    /** 验证具有真实 ISO Base Media 文件头的视频会按视频附件保存。 */
    @Test
    void videoUploadStoresRecognizableMp4AsVideoAttachment() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFileService service = new SocialFileService(
                repository, mock(VideoPosterService.class), uploadRoot.toString());
        byte[] videoBytes = createMp4HeaderBytes();
        MockMultipartFile video = new MockMultipartFile(
                "file", "training.mp4", "video/mp4", videoBytes);
        AttachmentRow stored = new AttachmentRow(41L, 7L, SocialAttachmentType.VIDEO.name(),
                "training.mp4", "stored-video", "video/mp4", (long) videoBytes.length,
                "/api/v1/social/files/stored-video",
                "/api/v1/social/files/stored-video/poster", null);
        when(repository.insertAttachment(anyLong(), any(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), any())).thenReturn(41L);
        when(repository.findAttachment(41L)).thenReturn(Optional.of(stored));

        AttachmentRow result = service.store(7L, video);

        assertEquals(stored, result);
        assertEquals(SocialAttachmentType.VIDEO.name(), result.attachmentType());
    }

    /** 验证仅伪造 video MIME 的文本不会进入附件表。 */
    @Test
    void videoUploadRejectsSpoofedMediaType() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFileService service = new SocialFileService(
                repository, mock(VideoPosterService.class), uploadRoot.toString());
        MockMultipartFile fakeVideo = new MockMultipartFile(
                "file", "training.mp4", "video/mp4", "not-a-video".getBytes());

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class, () -> service.store(7L, fakeVideo));

        assertEquals(ApiErrorCode.VIDEO_FORMAT_UNSUPPORTED.status(), error.getStatusCode());
        verify(repository, never()).insertAttachment(anyLong(), any(), anyString(), anyString(),
                anyString(), anyLong(), anyString(), any());
    }

    /**
     * 创建由标准图片读取器生成的最小 PNG，避免测试依赖仓库外部资源。
     *
     * @return 可被 ImageIO 识别的 PNG 字节
     * @throws IOException 图片编码失败时抛出
     */
    private byte[] createPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    /**
     * 创建包含 ftyp 盒的最小测试头，不依赖外部视频资源。
     *
     * @return 可被附件服务识别的 MP4 头部字节
     */
    private byte[] createMp4HeaderBytes() {
        return new byte[] {
            0, 0, 0, 24,
            'f', 't', 'y', 'p',
            'i', 's', 'o', 'm',
            0, 0, 2, 0,
            'i', 's', 'o', 'm',
            'm', 'p', '4', '2'
        };
    }
}

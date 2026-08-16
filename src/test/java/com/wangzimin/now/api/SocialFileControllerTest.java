package com.wangzimin.now.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.service.SocialFileService;
import com.wangzimin.now.service.SocialFileService.DownloadableAttachment;

class SocialFileControllerTest {

    @TempDir
    Path mediaDirectory;

    /** 验证视频 Resource 响应支持播放器请求的标准字节区间。 */
    @Test
    void videoDownloadSupportsHttpByteRanges() throws Exception {
        byte[] mediaBytes = "0123456789".getBytes(StandardCharsets.UTF_8);
        Path mediaFile = Files.write(mediaDirectory.resolve("stored-video"), mediaBytes);
        AttachmentRow metadata = new AttachmentRow(3L, 1L, SocialAttachmentType.VIDEO.name(),
                "training.mp4", "stored-video", "video/mp4", (long) mediaBytes.length,
                "/api/v1/social/files/stored-video",
                "/api/v1/social/files/stored-video/poster", null);
        SocialFileService service = mock(SocialFileService.class);
        when(service.load("stored-video")).thenReturn(
                new DownloadableAttachment(metadata, new UrlResource(mediaFile.toUri())));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SocialFileController(service)).build();

        mockMvc.perform(get("/api/v1/social/files/stored-video")
                        .header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(content().bytes("2345".getBytes(StandardCharsets.UTF_8)));
    }
}

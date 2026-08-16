package com.wangzimin.now.api;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wangzimin.now.domain.ApiPath;
import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.service.SocialFileService;
import com.wangzimin.now.service.SocialFileService.DownloadableAttachment;
import com.wangzimin.now.service.SocialFileService.DownloadablePoster;

/**
 * 以随机不可猜地址提供聊天和朋友圈附件读取。
 *
 * <p>图片和视频使用内联响应供移动端组件展示，普通文件强制下载，避免浏览器执行上传内容。
 * 地址不包含原文件名或用户信息，附件上传接口本身仍要求 JWT 认证。</p>
 */
@RestController
@RequestMapping(ApiPath.SOCIAL_ROOT)
public class SocialFileController {

    private final SocialFileService fileService;

    /**
     * 创建附件读取控制器。
     *
     * @param fileService 附件服务
     */
    public SocialFileController(SocialFileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 返回图片预览、视频播放或普通文件下载响应。
     *
     * @param storedName 随机存储名
     * @return 带安全内容处置头的文件响应
     */
    @GetMapping(ApiPath.SOCIAL_FILE_SEGMENT)
    public ResponseEntity<Resource> download(@PathVariable String storedName) {
        DownloadableAttachment download = fileService.load(storedName);
        SocialAttachmentType attachmentType = SocialAttachmentType.valueOf(
                download.metadata().attachmentType());
        boolean inline = attachmentType.supportsInlinePreview();
        ContentDisposition disposition = inline
                ? ContentDisposition.inline().filename(
                        download.metadata().originalName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(
                        download.metadata().originalName(), StandardCharsets.UTF_8).build();
        MediaType mediaType = inline
                ? MediaType.parseMediaType(download.metadata().mimeType())
                : MediaType.APPLICATION_OCTET_STREAM;
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(download.metadata().sizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
        if (attachmentType == SocialAttachmentType.VIDEO) {
            response.header(HttpHeaders.ACCEPT_RANGES, SystemText.BYTE_RANGE_UNIT.value());
        }
        return response.body(download.resource());
    }

    /**
     * 返回视频第零帧 JPEG，供时间线和聊天列表作为静态封面。
     *
     * @param storedName 视频随机存储名
     * @return 可缓存的首帧图片
     */
    @GetMapping(ApiPath.SOCIAL_FILE_POSTER_SEGMENT)
    public ResponseEntity<Resource> poster(@PathVariable String storedName) {
        DownloadablePoster poster = fileService.loadPoster(storedName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(SystemText.JPEG_MIME_TYPE.value()))
                .contentLength(poster.sizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, SystemText.PUBLIC_CACHE_ONE_DAY.value())
                .body(poster.resource());
    }
}

package com.wangzimin.now.service;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.UUID;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;

/**
 * 负责社交附件的磁盘保存、元数据登记和公开读取。
 *
 * <p>文件使用随机 UUID 作为不可猜存储名，不信任客户端路径或文件名。
 * 数据库保留原始名称用于聊天展示，实际路径始终限制在配置的上传根目录内。</p>
 */
@Service
public class SocialFileService {

    private final SocialConversationRepository repository;
    private final VideoPosterService videoPosterService;
    private final Path uploadRoot;

    /**
     * 创建附件服务并规范化上传根目录。
     *
     * @param repository 会话和附件仓储
     * @param videoPosterService 视频首帧生成服务
     * @param uploadDirectory 配置的上传目录
     */
    public SocialFileService(SocialConversationRepository repository,
            VideoPosterService videoPosterService,
            @Value(SystemText.SOCIAL_UPLOAD_CONFIG) String uploadDirectory) {
        this.repository = repository;
        this.videoPosterService = videoPosterService;
        this.uploadRoot = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    /**
     * 校验并保存一个聊天或朋友圈附件。
     *
     * @param userId 当前用户主键
     * @param file 客户端上传文件
     * @return 已持久化的附件资料
     */
    public AttachmentRow store(Long userId, MultipartFile file) {
        return storeValidated(userId, file, null, null, null);
    }

    /**
     * 校验并保存当前用户头像，普通文件不能伪装成头像。
     *
     * @param userId 当前用户主键
     * @param file 客户端选择的头像图片
     * @return 已保存的图片附件
     */
    public AttachmentRow storeAvatar(Long userId, MultipartFile file) {
        return storeValidated(userId, file, BusinessRule.AVATAR_UPLOAD_MAX_BYTES,
                SocialAttachmentType.IMAGE, ApiErrorCode.AVATAR_TOO_LARGE);
    }

    /**
     * 复用文件校验、落盘和元数据登记流程，并可约束指定附件类型。
     *
     * @param userId 当前用户主键
     * @param file 客户端上传文件
     * @param maximumBytes 可空的用途级最大字节数；为空时按附件类型判断
     * @param requiredType 可空的强制附件类型
     * @param tooLargeError 当前用途对应的体积错误
     * @return 已持久化附件资料
     */
    private AttachmentRow storeValidated(Long userId, MultipartFile file, BusinessRule maximumBytes,
            SocialAttachmentType requiredType, ApiErrorCode tooLargeError) {
        if (file == null || file.isEmpty()) {
            throw ApiErrorCode.FILE_EMPTY.exception();
        }
        String originalName = normalizeOriginalName(file.getOriginalFilename());
        String mimeType = normalizeMimeType(file.getContentType(), originalName);
        SocialAttachmentType type = SocialAttachmentType.fromMimeType(mimeType);
        BusinessRule effectiveLimit = maximumBytes == null ? type.uploadLimit() : maximumBytes;
        ApiErrorCode effectiveTooLargeError = tooLargeError == null
                ? type.tooLargeError() : tooLargeError;
        if (file.getSize() > effectiveLimit.longValue()) {
            throw effectiveTooLargeError.exception();
        }
        if (requiredType != null && type != requiredType) {
            throw ApiErrorCode.AVATAR_IMAGE_REQUIRED.exception();
        }
        if (requiredType == SocialAttachmentType.IMAGE && !hasReadableImageHeader(file)) {
            throw ApiErrorCode.AVATAR_IMAGE_REQUIRED.exception();
        }
        if (type == SocialAttachmentType.VIDEO && !hasRecognizableVideoHeader(file)) {
            throw ApiErrorCode.VIDEO_FORMAT_UNSUPPORTED.exception();
        }
        String storedName = UUID.randomUUID().toString();
        Path target = uploadRoot.resolve(storedName).normalize();
        ensureInsideUploadRoot(target);
        Path posterTarget = posterPath(storedName);
        try {
            Files.createDirectories(uploadRoot);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            String posterUrl = null;
            if (type == SocialAttachmentType.VIDEO) {
                videoPosterService.createFirstFrame(target, posterTarget);
                posterUrl = SystemText.SOCIAL_FILE_ROUTE.value() + storedName
                        + SystemText.SOCIAL_POSTER_ROUTE_SUFFIX.value();
            }
            long attachmentId = repository.insertAttachment(userId, type, originalName, storedName,
                    mimeType, file.getSize(), SystemText.SOCIAL_FILE_ROUTE.value() + storedName,
                    posterUrl);
            return repository.findAttachment(attachmentId)
                    .orElseThrow(ApiErrorCode.ATTACHMENT_NOT_FOUND::exception);
        } catch (IOException exception) {
            deleteStoredFiles(target, posterTarget);
            throw ApiErrorCode.FILE_STORE_FAILED.exception();
        } catch (RuntimeException exception) {
            deleteStoredFiles(target, posterTarget);
            throw exception;
        }
    }

    /**
     * 通过标准图片读取器检查真实文件头和首帧尺寸，拒绝仅伪造媒体类型的普通文件。
     *
     * <p>这里只解析图片元数据，不解码完整像素，避免为格式校验分配大块图像内存。</p>
     *
     * @param file 待校验的头像文件
     * @return 至少一个标准读取器能够识别且首帧尺寸有效时返回 true
     */
    private boolean hasReadableImageHeader(MultipartFile file) {
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(file.getInputStream())) {
            if (imageInput == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                return reader.getWidth(BusinessRule.ZERO_COUNT.value()) > BusinessRule.ZERO_COUNT.value()
                        && reader.getHeight(BusinessRule.ZERO_COUNT.value()) > BusinessRule.ZERO_COUNT.value();
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * 检查手机常见 MP4、MOV 和 M4V 共用的 ISO Base Media 文件头。
     *
     * <p>服务端只读取固定长度头部，不扫描或解码完整视频。这样可以拒绝仅伪造
     * {@code video/*} MIME 的普通文件，同时不因大视频分配额外内存。</p>
     *
     * @param file 待校验视频
     * @return 第一个媒体盒为 {@code ftyp} 时返回 true
     */
    private boolean hasRecognizableVideoHeader(MultipartFile file) {
        try {
            byte[] header = file.getInputStream().readNBytes(12);
            if (header.length < 8) {
                return false;
            }
            String signature = new String(header, 4, 4, StandardCharsets.US_ASCII);
            return SystemText.ISO_BASE_MEDIA_SIGNATURE.value().equals(signature);
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * 读取随机存储名对应的文件资源。
     *
     * @param storedName 随机存储名
     * @return 附件元数据和文件资源
     */
    public DownloadableAttachment load(String storedName) {
        AttachmentRow attachment = repository.findAttachmentByStoredName(storedName)
                .orElseThrow(ApiErrorCode.ATTACHMENT_NOT_FOUND::exception);
        Path target = uploadRoot.resolve(attachment.storedName()).normalize();
        ensureInsideUploadRoot(target);
        try {
            Resource resource = new UrlResource(target.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw ApiErrorCode.ATTACHMENT_NOT_FOUND.exception();
            }
            return new DownloadableAttachment(attachment, resource);
        } catch (IOException exception) {
            throw ApiErrorCode.ATTACHMENT_NOT_FOUND.exception();
        }
    }

    /**
     * 返回视频首帧封面；历史视频缺失封面文件时按需补生成。
     *
     * @param storedName 视频随机存储名
     * @return JPEG 封面资源及精确字节数
     */
    public DownloadablePoster loadPoster(String storedName) {
        AttachmentRow attachment = repository.findAttachmentByStoredName(storedName)
                .orElseThrow(ApiErrorCode.ATTACHMENT_NOT_FOUND::exception);
        if (!SocialAttachmentType.VIDEO.name().equals(attachment.attachmentType())) {
            throw ApiErrorCode.ATTACHMENT_NOT_FOUND.exception();
        }
        Path videoTarget = uploadRoot.resolve(attachment.storedName()).normalize();
        Path posterTarget = posterPath(attachment.storedName());
        ensureInsideUploadRoot(videoTarget);
        ensureInsideUploadRoot(posterTarget);
        if (!Files.isReadable(posterTarget)) {
            videoPosterService.createFirstFrame(videoTarget, posterTarget);
        }
        try {
            Resource resource = new UrlResource(posterTarget.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw ApiErrorCode.ATTACHMENT_NOT_FOUND.exception();
            }
            return new DownloadablePoster(resource, Files.size(posterTarget));
        } catch (IOException exception) {
            throw ApiErrorCode.ATTACHMENT_NOT_FOUND.exception();
        }
    }

    /**
     * 构造与视频随机名绑定的封面路径。
     *
     * @param storedName 视频随机存储名
     * @return 位于上传根目录内的 JPEG 路径
     */
    private Path posterPath(String storedName) {
        Path target = uploadRoot.resolve(storedName + SystemText.VIDEO_POSTER_SUFFIX.value()).normalize();
        ensureInsideUploadRoot(target);
        return target;
    }

    /**
     * 上传流程失败时清理视频和封面文件，避免孤立文件占用磁盘。
     *
     * @param target 视频路径
     * @param posterTarget 封面路径
     */
    private void deleteStoredFiles(Path target, Path posterTarget) {
        try {
            Files.deleteIfExists(target);
            Files.deleteIfExists(posterTarget);
        } catch (IOException ignored) {
            // 清理失败不能覆盖原始业务异常。
        }
    }

    /**
     * 防止任何构造路径逃逸出上传根目录。
     *
     * @param target 待访问的规范化路径
     */
    private void ensureInsideUploadRoot(Path target) {
        if (!target.startsWith(uploadRoot)) {
            throw ApiErrorCode.ATTACHMENT_NOT_FOUND.exception();
        }
    }

    /**
     * 去除客户端文件名中的路径和响应头控制字符。
     *
     * @param value 客户端文件名
     * @return 可安全展示的文件名
     */
    private String normalizeOriginalName(String value) {
        if (value == null || value.isBlank()) {
            return SystemText.FILE_NAME_FALLBACK.value();
        }
        String normalized = value.replace('\\', '/');
        int separator = normalized.lastIndexOf('/');
        String baseName = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        return baseName.replace('\r', '_').replace('\n', '_').replace('"', '_');
    }

    /**
     * 将缺失媒体类型收敛为安全的二进制类型。
     *
     * @param value 上传媒体类型
     * @param originalName 已安全化原文件名
     * @return 非空媒体类型
     */
    private String normalizeMimeType(String value, String originalName) {
        if (value != null && !value.isBlank()
                && !SystemText.BINARY_MIME_TYPE.value().equalsIgnoreCase(value.trim())) {
            return value.trim().toLowerCase();
        }
        String inferred = URLConnection.guessContentTypeFromName(originalName);
        return inferred == null ? SystemText.BINARY_MIME_TYPE.value() : inferred.toLowerCase();
    }

    /**
     * 描述可返回给客户端下载的附件。
     *
     * @param metadata 附件元数据
     * @param resource 磁盘资源
     */
    public record DownloadableAttachment(AttachmentRow metadata, Resource resource) {
    }

    /**
     * 描述视频首帧 JPEG 资源。
     *
     * @param resource 封面文件资源
     * @param sizeBytes 精确字节数
     */
    public record DownloadablePoster(Resource resource, long sizeBytes) {
    }
}

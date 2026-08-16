package com.wangzimin.now.domain;

import java.util.Locale;

/**
 * 区分可预览图片、可播放视频和普通文件附件。
 */
public enum SocialAttachmentType {
    IMAGE(SystemText.IMAGE_MIME_PREFIX.value(), true,
            BusinessRule.SOCIAL_UPLOAD_MAX_BYTES, ApiErrorCode.FILE_TOO_LARGE),
    VIDEO(SystemText.VIDEO_MIME_PREFIX.value(), true,
            BusinessRule.SOCIAL_VIDEO_UPLOAD_MAX_BYTES, ApiErrorCode.VIDEO_TOO_LARGE),
    FILE(null, false, BusinessRule.SOCIAL_UPLOAD_MAX_BYTES, ApiErrorCode.FILE_TOO_LARGE);

    private final String mimePrefix;
    private final boolean inlinePreview;
    private final BusinessRule uploadLimit;
    private final ApiErrorCode tooLargeError;

    /**
     * 保存附件识别、响应方式和上传限制。
     *
     * @param mimePrefix 可空的 MIME 前缀
     * @param inlinePreview 是否允许浏览器或 App 内联展示
     * @param uploadLimit 当前类型的最大上传体积
     * @param tooLargeError 超限时的用户错误
     */
    SocialAttachmentType(String mimePrefix, boolean inlinePreview,
            BusinessRule uploadLimit, ApiErrorCode tooLargeError) {
        this.mimePrefix = mimePrefix;
        this.inlinePreview = inlinePreview;
        this.uploadLimit = uploadLimit;
        this.tooLargeError = tooLargeError;
    }

    /**
     * 根据 MIME 类型确定附件类型。
     *
     * @param mimeType 上传文件的媒体类型
     * @return 图片、视频或普通文件
     */
    public static SocialAttachmentType fromMimeType(String mimeType) {
        String normalized = mimeType == null ? SystemText.EMPTY.value()
                : mimeType.toLowerCase(Locale.ROOT);
        for (SocialAttachmentType type : values()) {
            if (type.mimePrefix != null && normalized.startsWith(type.mimePrefix)) {
                return type;
            }
        }
        return FILE;
    }

    /**
     * 返回当前附件类型的最大上传体积。
     *
     * @return 命名后的字节限制
     */
    public BusinessRule uploadLimit() {
        return uploadLimit;
    }

    /**
     * 返回当前附件类型超限时的统一错误。
     *
     * @return 文件或视频体积错误
     */
    public ApiErrorCode tooLargeError() {
        return tooLargeError;
    }

    /**
     * 判断附件是否应以内联方式响应。
     *
     * @return 图片和视频返回真，普通文件返回假
     */
    public boolean supportsInlinePreview() {
        return inlinePreview;
    }
}

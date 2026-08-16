package com.wangzimin.now.domain;

/**
 * 定义聊天消息的可持久化内容类型。
 */
public enum SocialMessageType {
    TEXT,
    EMOJI,
    IMAGE,
    VIDEO,
    FILE,
    SYSTEM;

    /**
     * 判断消息类型是否必须引用附件。
     *
     * @return 图片、视频和文件返回真
     */
    public boolean requiresAttachment() {
        return requiredAttachmentType() != null;
    }

    /**
     * 返回消息必须引用的附件类型。
     *
     * @return 非附件消息返回 null，附件消息返回对应类型
     */
    public SocialAttachmentType requiredAttachmentType() {
        if (this == IMAGE) {
            return SocialAttachmentType.IMAGE;
        }
        if (this == VIDEO) {
            return SocialAttachmentType.VIDEO;
        }
        if (this == FILE) {
            return SocialAttachmentType.FILE;
        }
        return null;
    }

    /**
     * 判断消息类型是否必须携带文本。
     *
     * @return 文本和表情返回真
     */
    public boolean requiresText() {
        return this == TEXT || this == EMOJI || this == SYSTEM;
    }
}

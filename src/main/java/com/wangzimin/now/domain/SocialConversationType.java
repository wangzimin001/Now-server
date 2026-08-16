package com.wangzimin.now.domain;

/**
 * 区分一对一会话和多人群聊。
 */
public enum SocialConversationType {
    DIRECT,
    GROUP;

    /**
     * 返回数据库使用的稳定文本。
     *
     * @return 枚举名称
     */
    public String databaseValue() {
        return name();
    }
}

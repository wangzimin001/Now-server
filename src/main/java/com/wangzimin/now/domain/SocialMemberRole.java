package com.wangzimin.now.domain;

/**
 * 表示群聊成员权限；私聊双方统一使用普通成员角色。
 */
public enum SocialMemberRole {
    OWNER,
    ADMIN,
    MEMBER;

    /**
     * 返回数据库使用的稳定文本。
     *
     * @return 枚举名称
     */
    public String databaseValue() {
        return name();
    }
}

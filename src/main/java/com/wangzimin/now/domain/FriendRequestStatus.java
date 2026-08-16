package com.wangzimin.now.domain;

/**
 * 表示一对用户当前的好友申请状态。
 *
 * <p>数据库只保存枚举名称，服务层通过状态机限制重复申请、接受、拒绝和取消操作。</p>
 */
public enum FriendRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELED;

    /**
     * 返回数据库使用的稳定文本。
     *
     * @return 枚举名称
     */
    public String databaseValue() {
        return name();
    }
}

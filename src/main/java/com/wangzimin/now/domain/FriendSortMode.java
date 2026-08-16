package com.wangzimin.now.domain;

/**
 * 定义好友列表的可选排序方式。
 */
public enum FriendSortMode {
    NAME,
    RECENT_ACTIVITY;

    /**
     * 将可空请求值收敛为受支持的排序方式。
     *
     * @param value 客户端传入值
     * @return 解析结果，缺省时按名称排序
     */
    public static FriendSortMode from(String value) {
        if (value == null || value.isBlank()) {
            return NAME;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw ApiErrorCode.FRIEND_SORT_INVALID.exception();
        }
    }
}

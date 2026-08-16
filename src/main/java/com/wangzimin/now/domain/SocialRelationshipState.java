package com.wangzimin.now.domain;

/**
 * 描述当前用户与被搜索用户之间的关系状态。
 */
public enum SocialRelationshipState {
    SELF,
    FRIEND,
    OUTGOING_PENDING,
    INCOMING_PENDING,
    NONE
}

package com.wangzimin.now.domain;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * 汇总后端可返回给客户端的安全错误文案。
 *
 * <p>每个枚举项同时保存 HTTP 状态和用户可读消息，业务服务不得直接拼写错误文本。
 * 这样既消除魔法字符串，也保证全局异常处理器返回一致、可翻译的消息。</p>
 */
public enum ApiErrorCode {
    USERNAME_FORMAT(HttpStatus.BAD_REQUEST, "用户名需要为 4-30 位字母、数字或下划线"),
    USERNAME_EXISTS(HttpStatus.CONFLICT, "用户名已存在"),
    PUBLIC_ID_GENERATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "用户 ID 生成失败，请稍后重试"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "刷新令牌已失效"),
    ACCOUNT_UNAVAILABLE(HttpStatus.UNAUTHORIZED, "账号不存在或已停用"),
    WORKOUT_END_BEFORE_START(HttpStatus.BAD_REQUEST, "训练结束时间不能早于开始时间"),
    WORKOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "训练记录不存在"),
    EXERCISE_NOT_FOUND(HttpStatus.NOT_FOUND, "动作不存在"),
    WORKOUT_EXERCISES_MISMATCH(HttpStatus.BAD_REQUEST, "训练动作与原记录不一致"),
    WORKOUT_SET_REQUIRED(HttpStatus.BAD_REQUEST, "至少保留一组训练记录"),
    WEIGHT_STEP_INVALID(HttpStatus.BAD_REQUEST, "重量必须以 0.25 kg 为单位"),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "训练模板不存在"),
    PLAN_REPLACEMENT_CONFLICT(HttpStatus.BAD_REQUEST, "替换动作不能与主动作相同"),
    LATEST_PERFORMANCE_LIMIT(HttpStatus.BAD_REQUEST, "一次查询的动作数量超过上限"),
    TRAINING_MODE_INVALID(HttpStatus.BAD_REQUEST, "训练模式不受支持"),
    TRAINING_CYCLE_FORMAT(HttpStatus.BAD_REQUEST, "训练周期格式不正确"),
    TRAINING_CYCLE_DAYS(HttpStatus.BAD_REQUEST, "训练周期天数不符合规则"),
    TRAINING_CYCLE_TOO_LARGE(HttpStatus.BAD_REQUEST, "训练周期内容过大"),
    SOCIAL_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "没有找到这个用户 ID"),
    FRIEND_SELF_REQUEST(HttpStatus.BAD_REQUEST, "不能添加自己为好友"),
    FRIEND_ALREADY_EXISTS(HttpStatus.CONFLICT, "你们已经是好友"),
    FRIEND_REQUEST_EXISTS(HttpStatus.CONFLICT, "好友申请已经发送，请等待对方处理"),
    FRIEND_REQUEST_INCOMING(HttpStatus.CONFLICT, "对方已经向你发送好友申请，请先处理"),
    FRIEND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "好友申请不存在"),
    FRIEND_REQUEST_NOT_PENDING(HttpStatus.CONFLICT, "这条好友申请已经处理"),
    FRIEND_REQUEST_FORBIDDEN(HttpStatus.FORBIDDEN, "无权处理这条好友申请"),
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "好友关系不存在"),
    FRIEND_SORT_INVALID(HttpStatus.BAD_REQUEST, "好友排序方式不受支持"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "会话不存在"),
    CONVERSATION_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "你不是该会话成员"),
    MESSAGE_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "消息内容不能为空"),
    MESSAGE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "消息类型不受支持"),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "附件不存在"),
    ATTACHMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "不能使用其他用户的附件"),
    ATTACHMENT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "附件类型与消息类型不一致"),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "请选择要发送的文件"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "文件不能超过 20 MB"),
    VIDEO_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "视频不能超过 100 MB"),
    VIDEO_FORMAT_UNSUPPORTED(HttpStatus.BAD_REQUEST, "仅支持 MP4、MOV 或 M4V 视频"),
    VIDEO_PREVIEW_UNAVAILABLE(HttpStatus.BAD_REQUEST, "无法读取视频首帧，请选择常见的 H.264 视频"),
    AVATAR_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "头像图片不能超过 5 MB"),
    AVATAR_IMAGE_REQUIRED(HttpStatus.BAD_REQUEST, "头像必须是图片文件"),
    FILE_STORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "文件保存失败，请稍后重试"),
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "朋友圈内容不存在"),
    POST_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "请输入文字、选择图片或视频，或分享训练"),
    POST_MEDIA_LIMIT(HttpStatus.BAD_REQUEST, "一条朋友圈最多发送 9 张图片"),
    POST_VIDEO_LIMIT(HttpStatus.BAD_REQUEST, "一条朋友圈最多发送 1 个视频"),
    POST_MEDIA_MIXED(HttpStatus.BAD_REQUEST, "朋友圈不能同时发送图片和视频"),
    POST_FORBIDDEN(HttpStatus.FORBIDDEN, "无权操作这条朋友圈"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "评论不存在"),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "只能删除自己的评论"),
    WORKOUT_SHARE_INVALID(HttpStatus.BAD_REQUEST, "只能分享自己已完成的训练"),
    GROUP_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "请输入群聊名称"),
    GROUP_MEMBER_LIMIT(HttpStatus.BAD_REQUEST, "群聊成员数量不符合规则"),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "群聊不存在"),
    GROUP_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "只有群主可以执行该操作"),
    GROUP_OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "群主需要先解散群聊"),
    GROUP_MEMBER_EXISTS(HttpStatus.CONFLICT, "用户已经在群聊中"),
    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "群成员不存在");

    private final HttpStatus status;
    private final String message;

    /**
     * 创建一个可安全返回给客户端的错误定义。
     *
     * @param status HTTP 语义状态
     * @param message 用户可读中文文案
     */
    ApiErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 构造 Spring Web 可直接抛出的业务异常。
     *
     * @return 带统一状态和消息的异常
     */
    public ResponseStatusException exception() {
        return new ResponseStatusException(status, message);
    }

    /** @return HTTP 语义状态 */
    public HttpStatus status() {
        return status;
    }

    /** @return 用户可读消息 */
    public String message() {
        return message;
    }
}

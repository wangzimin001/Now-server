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
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "用户名或密码错误"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "刷新令牌已失效"),
    ACCOUNT_UNAVAILABLE(HttpStatus.UNAUTHORIZED, "账号不存在或已停用"),
    WORKOUT_END_BEFORE_START(HttpStatus.BAD_REQUEST, "训练结束时间不能早于开始时间"),
    WORKOUT_NOT_FOUND(HttpStatus.NOT_FOUND, "训练记录不存在"),
    WORKOUT_EXERCISES_MISMATCH(HttpStatus.BAD_REQUEST, "训练动作与原记录不一致"),
    WORKOUT_SET_REQUIRED(HttpStatus.BAD_REQUEST, "至少保留一组训练记录"),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "训练模板不存在"),
    LATEST_PERFORMANCE_LIMIT(HttpStatus.BAD_REQUEST, "一次查询的动作数量超过上限"),
    TRAINING_MODE_INVALID(HttpStatus.BAD_REQUEST, "训练模式不受支持"),
    TRAINING_CYCLE_FORMAT(HttpStatus.BAD_REQUEST, "训练周期格式不正确"),
    TRAINING_CYCLE_DAYS(HttpStatus.BAD_REQUEST, "训练周期天数不符合规则"),
    TRAINING_CYCLE_TOO_LARGE(HttpStatus.BAD_REQUEST, "训练周期内容过大");

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

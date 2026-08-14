package com.wangzimin.now.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import com.wangzimin.now.domain.SystemText;

/**
 * 将业务层的 HTTP 异常转换为统一 JSON 响应。
 *
 * <p>处理器只暴露已经由业务层确认安全的错误原因，避免客户端退化为裸状态码。
 * 未提供原因时使用统一兜底文案，不泄露堆栈或数据库信息。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 处理业务服务抛出的带状态异常。
     *
     * <p>HTTP 状态保持原始语义，响应正文补充状态码、友好消息和请求路径，
     * 供移动端直接展示或记录。</p>
     *
     * @param exception 业务层抛出的状态异常
     * @param request 当前 HTTP 请求
     * @return 结构统一且不包含内部细节的错误响应
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason();
        if (message == null || message.isBlank()) {
            message = SystemText.REQUEST_FAILED.value();
        }
        return ResponseEntity.status(status)
                .body(new ApiError(status.value(), message, request.getRequestURI()));
    }

    /**
     * 描述客户端可安全读取的错误结构。
     *
     * @param status HTTP 数值状态
     * @param message 用户可读错误信息
     * @param path 发生错误的请求路径
     */
    public record ApiError(int status, String message, String path) {
    }
}

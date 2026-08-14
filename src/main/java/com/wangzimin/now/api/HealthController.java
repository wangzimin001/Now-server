package com.wangzimin.now.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wangzimin.now.domain.ApiPath;
import com.wangzimin.now.domain.SystemText;

/**
 * 提供无需鉴权的轻量健康检查。
 *
 * <p>移动端、部署脚本和本地重启流程使用该接口确认新进程已经可用。
 * 接口不访问用户数据，也不返回数据库或主机敏感信息。</p>
 */
@RestController
@RequestMapping(ApiPath.HEALTH)
public class HealthController {

    /**
     * 返回当前应用进程的健康快照。
     *
     * <p>能够执行本方法即表示 Spring Web 上下文已完成启动；
     * 数据库迁移错误会阻止应用启动，因此不会误报迁移失败的进程为健康。</p>
     *
     * @return 包含稳定状态、服务名和服务器时间的响应
     */
    @GetMapping
    public HealthResponse health() {
        return new HealthResponse(SystemText.HEALTH_UP.value(), SystemText.SERVICE_NAME.value(), Instant.now());
    }

    /**
     * 描述一次健康检查结果。
     *
     * @param status 稳定健康状态
     * @param service 服务标识
     * @param timestamp 服务器生成响应的时间
     */
    public record HealthResponse(String status, String service, Instant timestamp) {
    }
}

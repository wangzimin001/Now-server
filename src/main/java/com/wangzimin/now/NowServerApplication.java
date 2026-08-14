package com.wangzimin.now;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 启动“此刻”后端的 Spring Boot 应用。
 *
 * <p>该类型只负责装配应用上下文，不承载业务逻辑。
 * 组件扫描从当前根包开始，覆盖接口、服务、配置和领域枚举。</p>
 */
@SpringBootApplication
public class NowServerApplication {

    /**
     * 启动内嵌 Web 容器和完整 Spring 应用上下文。
     *
     * <p>数据库连接、鉴权密钥等敏感配置只从进程环境读取，
     * 本入口不会写入或输出任何凭据。</p>
     *
     * @param args JVM 传入的 Spring Boot 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(NowServerApplication.class, args);
    }
}

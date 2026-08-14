package com.wangzimin.now.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import com.wangzimin.now.domain.CorsPolicy;

/**
 * 配置本地 Web 与移动端调试所需的 MVC 行为。
 *
 * <p>当前只开放回环地址的跨域请求，不把生产来源写死在应用代码中。</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 注册受控的本地开发跨域规则。
     *
     * <p>策略全部来自领域枚举，方法本身只负责映射到 Spring MVC。
     * 禁用凭据跨域可以减少本地调试时的会话泄露风险。</p>
     *
     * @param registry Spring 提供的跨域规则注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        CorsPolicy policy = CorsPolicy.LOCAL_DEVELOPMENT;
        registry.addMapping(policy.path())
                .allowedOriginPatterns(policy.origins())
                .allowedMethods(policy.methods())
                .allowedHeaders(policy.headers())
                .allowCredentials(false);
    }
}

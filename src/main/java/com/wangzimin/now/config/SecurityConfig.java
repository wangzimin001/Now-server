package com.wangzimin.now.config;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.wangzimin.now.domain.ApiPath;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.SystemText;

/**
 * 配置无状态 JWT 鉴权、密码哈希和签名密钥。
 *
 * <p>公开路径来自统一 API 路径枚举，其余接口默认要求认证。
 * 正式部署必须从环境提供稳定密钥；本地缺省密钥只用于临时调试。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 构建应用的 HTTP 安全过滤链。
     *
     * <p>服务端不创建会话，关闭基于浏览器会话的 CSRF 防护，
     * 并将 Bearer JWT 交给 OAuth2 资源服务器验证。</p>
     *
     * @param http Spring Security 配置入口
     * @return 已构建的无状态过滤链
     * @throws Exception Spring Security 配置失败时抛出
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPath.AUTH_REGISTER, ApiPath.AUTH_LOGIN, ApiPath.AUTH_REFRESH,
                                ApiPath.AUTH_LOGOUT, ApiPath.HEALTH, ApiPath.EXERCISES,
                                ApiPath.EXERCISE_CATEGORIES, ApiPath.ACTUATOR_HEALTH, ApiPath.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, ApiPath.DASHBOARD, ApiPath.WORKOUT_PLANS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .build();
    }

    /**
     * 提供 BCrypt 密码编码器。
     *
     * <p>工作因子来自业务规则枚举，密码永远只保存不可逆哈希。</p>
     *
     * @return 统一密码编码器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BusinessRule.BCRYPT_STRENGTH.value());
    }

    /**
     * 从环境配置构造 JWT 对称签名密钥。
     *
     * <p>未配置时生成仅供本地进程使用的随机密钥；配置值过短会直接阻止启动，
     * 避免以弱密钥运行。</p>
     *
     * @param configuredSecret 环境或配置文件注入的密钥文本
     * @return 经过最小长度校验的密钥包装
     */
    @Bean
    AuthJwtKey authJwtKey(@Value("${auth.jwt.secret:}") String configuredSecret) {
        byte[] keyBytes;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            keyBytes = new byte[BusinessRule.JWT_SECRET_MIN_BYTES.value()];
            new SecureRandom().nextBytes(keyBytes);
        } else {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < BusinessRule.JWT_SECRET_MIN_BYTES.value()) {
                throw new IllegalStateException(SystemText.JWT_SECRET_TOO_SHORT.value());
            }
        }
        return new AuthJwtKey(new SecretKeySpec(keyBytes, SystemText.SIGNING_KEY_ALGORITHM.value()));
    }

    /**
     * 创建访问令牌编码器。
     *
     * @param key 应用签名密钥
     * @return 使用同一密钥的 Nimbus JWT 编码器
     */
    @Bean
    JwtEncoder jwtEncoder(AuthJwtKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key.secretKey()));
    }

    /**
     * 创建访问令牌解码器。
     *
     * <p>算法固定为 HS256，并与令牌签发端保持一致。</p>
     *
     * @param key 应用签名密钥
     * @return 验证 Bearer JWT 的解码器
     */
    @Bean
    JwtDecoder jwtDecoder(AuthJwtKey key) {
        return NimbusJwtDecoder.withSecretKey(key.secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * 包装应用 JWT 密钥，避免在 Bean 图中传递裸字节数组。
     *
     * @param secretKey JCA 对称密钥
     */
    public record AuthJwtKey(SecretKey secretKey) {
    }
}

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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout", "/api/v1/health", "/api/v1/exercises/**",
                                "/api/v1/exercise-categories", "/actuator/health", "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard", "/api/v1/workout-plans").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    AuthJwtKey authJwtKey(@Value("${auth.jwt.secret:}") String configuredSecret) {
        byte[] keyBytes;
        if (configuredSecret == null || configuredSecret.isBlank()) {
            keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
        } else {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < 32) {
                throw new IllegalStateException("AUTH_JWT_SECRET 至少需要 32 个字节");
            }
        }
        return new AuthJwtKey(new SecretKeySpec(keyBytes, "HmacSHA256"));
    }

    @Bean
    JwtEncoder jwtEncoder(AuthJwtKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key.secretKey()));
    }

    @Bean
    JwtDecoder jwtDecoder(AuthJwtKey key) {
        return NimbusJwtDecoder.withSecretKey(key.secretKey()).macAlgorithm(MacAlgorithm.HS256).build();
    }

    public record AuthJwtKey(SecretKey secretKey) {
    }
}

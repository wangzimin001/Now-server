package com.wangzimin.now.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final long ACCESS_SECONDS = 15 * 60;
    private static final long REFRESH_DAYS = 30;

    private final JdbcClient jdbcClient;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(JdbcClient jdbcClient, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.jdbcClient = jdbcClient;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String displayName = request.displayName().trim();
        if (!username.matches("[a-zA-Z0-9_]{4,30}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名需要为 4-30 位字母、数字或下划线");
        }
        try {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbcClient.sql("""
                            INSERT INTO app_user (username, password_hash, display_name, enabled)
                            VALUES (:username, :passwordHash, :displayName, TRUE)
                            """)
                    .param("username", username)
                    .param("passwordHash", passwordEncoder.encode(request.password()))
                    .param("displayName", displayName)
                    .update(keyHolder, "id");
            Number key = keyHolder.getKey();
            if (key == null) throw new IllegalStateException("创建账号后未返回主键");
            return issueTokens(new User(key.longValue(), username, displayName, ""));
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已存在");
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        User user = jdbcClient.sql("""
                        SELECT id, username, display_name AS displayName, password_hash AS passwordHash
                        FROM app_user
                        WHERE username = :username AND enabled = TRUE
                        """)
                .param("username", username)
                .query((resultSet, rowNumber) -> new User(resultSet.getLong("id"), resultSet.getString("username"),
                        resultSet.getString("displayName"), resultSet.getString("passwordHash")))
                .optional()
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) throw invalidCredentials();
        return issueTokens(user.withoutPassword());
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hash(request.refreshToken());
        RefreshRow refresh = jdbcClient.sql("""
                        SELECT rt.id, rt.user_id AS userId, u.username, u.display_name AS displayName
                        FROM auth_refresh_token rt
                        JOIN app_user u ON u.id = rt.user_id AND u.enabled = TRUE
                        WHERE rt.token_hash = :tokenHash
                          AND rt.revoked_at IS NULL
                          AND rt.expires_at > CURRENT_TIMESTAMP
                        """)
                .param("tokenHash", tokenHash)
                .query(RefreshRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "刷新令牌已失效"));
        jdbcClient.sql("UPDATE auth_refresh_token SET revoked_at = CURRENT_TIMESTAMP, last_used_at = CURRENT_TIMESTAMP WHERE id = :id")
                .param("id", refresh.id()).update();
        return issueTokens(new User(refresh.userId(), refresh.username(), refresh.displayName(), ""));
    }

    public void logout(RefreshRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) return;
        jdbcClient.sql("UPDATE auth_refresh_token SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = :tokenHash AND revoked_at IS NULL")
                .param("tokenHash", hash(request.refreshToken())).update();
    }

    public UserProfile profile(Long userId) {
        return jdbcClient.sql("SELECT id, username, display_name AS displayName FROM app_user WHERE id = :id AND enabled = TRUE")
                .param("id", userId)
                .query(UserProfile.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "账号不存在或已停用"));
    }

    private AuthResponse issueTokens(User user) {
        Instant issuedAt = Instant.now();
        Instant accessExpiry = issuedAt.plusSeconds(ACCESS_SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("now-server")
                .issuedAt(issuedAt)
                .expiresAt(accessExpiry)
                .subject(String.valueOf(user.id()))
                .claim("username", user.username())
                .claim("displayName", user.displayName())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        String refreshToken = randomToken();
        Instant refreshExpiry = issuedAt.plus(REFRESH_DAYS, ChronoUnit.DAYS);
        jdbcClient.sql("""
                        INSERT INTO auth_refresh_token (user_id, token_hash, expires_at)
                        VALUES (:userId, :tokenHash, :expiresAt)
                        """)
                .param("userId", user.id())
                .param("tokenHash", hash(refreshToken))
                .param("expiresAt", Timestamp.from(refreshExpiry))
                .update();
        return new AuthResponse(accessToken, refreshToken, ACCESS_SECONDS,
                new UserProfile(user.id(), user.username(), user.displayName()));
    }

    private String randomToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("系统缺少 SHA-256", exception);
        }
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    public record RegisterRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 4, max = 30) String username,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 72) String password,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 1, max = 40) String displayName) {
    }

    public record LoginRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String password) {
    }

    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserProfile user) {
    }

    public record UserProfile(Long id, String username, String displayName) {
    }

    private record User(Long id, String username, String displayName, String passwordHash) {
        User withoutPassword() {
            return new User(id, username, displayName, "");
        }
    }

    private record RefreshRow(Long id, Long userId, String username, String displayName) {
    }
}

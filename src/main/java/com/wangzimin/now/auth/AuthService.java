package com.wangzimin.now.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.domain.ValidationRule;
import com.wangzimin.now.repository.AuthRepository;
import com.wangzimin.now.repository.AuthRepository.RefreshTokenRow;
import com.wangzimin.now.repository.AuthRepository.UserRow;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 实现账号注册、凭据校验和令牌生命周期管理。
 *
 * <p>密码使用 BCrypt 单向哈希；访问令牌使用短期 JWT；刷新令牌使用高熵随机值，
 * 数据库只保存其 SHA-256 摘要。刷新操作在事务中执行旧令牌撤销和新令牌签发，
 * 从而阻止同一刷新令牌被重复轮换。</p>
 */
@Service
public class AuthService {

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建鉴权服务。
     *
     * @param authRepository 账号和刷新令牌仓储
     * @param passwordEncoder BCrypt 密码编码器
     * @param jwtEncoder JWT 签发器
     */
    public AuthService(AuthRepository authRepository, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * 注册唯一用户名并立即签发令牌。
     *
     * <p>用户名先规范化并再次执行服务端格式校验，密码在写库前编码。
     * 数据库唯一约束作为并发注册的最终防线。</p>
     *
     * @param request 注册凭据和展示名称
     * @return 新账号的令牌与公开资料
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = normalizeUsername(request.username());
        String displayName = request.displayName().trim();
        if (!username.matches(SystemText.USERNAME_PATTERN.value())) {
            throw ApiErrorCode.USERNAME_FORMAT.exception();
        }
        if (authRepository.usernameExists(username)) {
            throw ApiErrorCode.USERNAME_EXISTS.exception();
        }
        String passwordHash = passwordEncoder.encode(request.password());
        for (int attempt = BusinessRule.ZERO_COUNT.value();
                attempt < BusinessRule.PUBLIC_ID_GENERATION_ATTEMPTS.value(); attempt++) {
            String publicId = randomPublicId();
            try {
                long userId = authRepository.insertUser(publicId, username, passwordHash, displayName);
                return issueTokens(new UserRow(userId, publicId, username, displayName,
                        null, SystemText.EMPTY.value()));
            } catch (DuplicateKeyException exception) {
                if (authRepository.usernameExists(username)) {
                    throw ApiErrorCode.USERNAME_EXISTS.exception();
                }
            }
        }
        throw ApiErrorCode.PUBLIC_ID_GENERATION_FAILED.exception();
    }

    /**
     * 校验用户名和密码并签发新令牌。
     *
     * <p>不存在、停用和密码不匹配使用同一错误，避免攻击者枚举有效账号。</p>
     *
     * @param request 登录凭据
     * @return 新的访问令牌、刷新令牌和用户资料
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String username = normalizeUsername(request.username());
        UserRow user = authRepository.findEnabledUserByUsername(username)
                .orElseThrow(() -> invalidCredentials());
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw invalidCredentials();
        }
        return issueTokens(user.withoutPassword());
    }

    /**
     * 使用有效刷新令牌轮换令牌对。
     *
     * <p>查询只接受未撤销、未过期且所属账号仍启用的令牌。
     * 旧令牌在签发新令牌前标记撤销，整个流程共享事务。</p>
     *
     * @param request 原刷新令牌
     * @return 轮换后的令牌与资料
     */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hash(request.refreshToken());
        RefreshTokenRow refresh = authRepository.findActiveRefreshToken(tokenHash)
                .orElseThrow(ApiErrorCode.REFRESH_TOKEN_INVALID::exception);
        authRepository.revokeRefreshToken(refresh.id());
        return issueTokens(new UserRow(
                refresh.userId(), refresh.publicId(), refresh.username(), refresh.displayName(),
                refresh.avatarUrl(), SystemText.EMPTY.value()));
    }

    /**
     * 幂等撤销一个刷新令牌。
     *
     * <p>空请求或空令牌直接返回；有效值先计算摘要再匹配数据库，
     * 原始令牌不会出现在 SQL、日志或持久化数据中。</p>
     *
     * @param request 可空的刷新请求
     */
    public void logout(RefreshRequest request) {
        if (request == null || request.refreshToken() == null || request.refreshToken().isBlank()) {
            return;
        }
        authRepository.revokeRefreshTokenByHash(hash(request.refreshToken()));
    }

    /**
     * 查询启用账号的公开资料。
     *
     * @param userId JWT 中的用户主键
     * @return 不含密码和令牌的用户资料
     */
    public UserProfile profile(Long userId) {
        return authRepository.findEnabledUserProfile(userId)
                .map(row -> new UserProfile(row.id(), row.publicId(), row.username(),
                        row.displayName(), row.avatarUrl()))
                .orElseThrow(ApiErrorCode.ACCOUNT_UNAVAILABLE::exception);
    }

    /**
     * 为已确认身份的账号签发访问令牌和刷新令牌。
     *
     * <p>访问令牌携带最小必要声明；刷新令牌只把摘要和过期时间写入数据库。
     * 调用方必须位于事务内，保证刷新轮换和注册流程的一致性。</p>
     *
     * @param user 不包含密码哈希的账号快照
     * @return 完整令牌响应
     */
    private AuthResponse issueTokens(UserRow user) {
        Instant issuedAt = Instant.now();
        Instant accessExpiry = issuedAt.plusSeconds(BusinessRule.ACCESS_TOKEN_SECONDS.longValue());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SystemText.JWT_ISSUER.value())
                .issuedAt(issuedAt)
                .expiresAt(accessExpiry)
                .subject(String.valueOf(user.id()))
                .claim(SystemText.JWT_USERNAME_CLAIM.value(), user.username())
                .claim(SystemText.JWT_DISPLAY_NAME_CLAIM.value(), user.displayName())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type(SystemText.JWT_TOKEN_TYPE.value()).build(), claims)).getTokenValue();
        String refreshToken = randomToken();
        Instant refreshExpiry = issuedAt.plus(BusinessRule.REFRESH_TOKEN_DAYS.longValue(), ChronoUnit.DAYS);
        authRepository.insertRefreshToken(user.id(), hash(refreshToken), Timestamp.from(refreshExpiry));
        return new AuthResponse(accessToken, refreshToken, BusinessRule.ACCESS_TOKEN_SECONDS.longValue(),
                new UserProfile(user.id(), user.publicId(), user.username(), user.displayName(), user.avatarUrl()));
    }

    /**
     * 生成 URL 安全的高熵刷新令牌。
     *
     * <p>随机字节长度来自统一业务规则，Base64 编码移除填充便于客户端存储。</p>
     *
     * @return 未经哈希的令牌，仅返回给当前客户端
     */
    private String randomToken() {
        byte[] bytes = new byte[BusinessRule.REFRESH_TOKEN_BYTES.value()];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 规范化用户名以实现大小写无关登录。
     *
     * @param username 原始用户名
     * @return 去除空白并转为小写的值，空输入返回空字符串
     */
    private String normalizeUsername(String username) {
        return username == null ? SystemText.EMPTY.value() : username.trim().toLowerCase();
    }

    /**
     * 计算刷新令牌的 SHA-256 十六进制摘要。
     *
     * <p>摘要用于精确查询和撤销，数据库永远不保存能够直接使用的原始令牌。</p>
     *
     * @param value 待摘要的原始值
     * @return 固定长度小写十六进制摘要
     */
    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance(SystemText.HASH_ALGORITHM.value())
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(BusinessRule.HASH_HEX_CAPACITY.value());
            for (byte item : digest) {
                result.append(String.format(SystemText.HASH_BYTE_FORMAT.value(), item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("系统缺少 " + SystemText.HASH_ALGORITHM.value(), exception);
        }
    }

    /**
     * 构造统一的凭据无效异常。
     *
     * @return 不区分账号存在性的 401 异常
     */
    private ResponseStatusException invalidCredentials() {
        return ApiErrorCode.INVALID_CREDENTIALS.exception();
    }

    /**
     * 描述注册账号所需字段及边界。
     *
     * @param username 唯一登录名
     * @param password 原始密码，仅在请求生命周期内存在
     * @param displayName 用户展示名称
     */
    public record RegisterRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(
                    min = ValidationRule.USERNAME_MIN_LENGTH,
                    max = ValidationRule.USERNAME_MAX_LENGTH) String username,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(
                    min = ValidationRule.PASSWORD_MIN_LENGTH,
                    max = ValidationRule.PASSWORD_MAX_LENGTH) String password,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(
                    min = ValidationRule.DISPLAY_NAME_MIN_LENGTH,
                    max = ValidationRule.DISPLAY_NAME_MAX_LENGTH) String displayName) {
    }

    /**
     * 描述登录请求。
     *
     * @param username 登录名
     * @param password 原始密码
     */
    public record LoginRequest(
            @jakarta.validation.constraints.NotBlank String username,
            @jakarta.validation.constraints.NotBlank String password) {
    }

    /**
     * 描述刷新或退出请求。
     *
     * @param refreshToken 客户端持有的高熵刷新令牌
     */
    public record RefreshRequest(@jakarta.validation.constraints.NotBlank String refreshToken) {
    }

    /**
     * 描述登录、注册或刷新成功后的响应。
     *
     * @param accessToken 短期 JWT
     * @param refreshToken 可轮换刷新令牌
     * @param expiresIn 访问令牌有效秒数
     * @param user 用户公开资料
     */
    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserProfile user) {
    }

    /**
     * 描述允许返回客户端的账号字段。
     *
     * @param id 用户主键
     * @param publicId 对外可搜索的用户 ID
     * @param username 登录名
     * @param displayName 展示名称
     * @param avatarUrl 可空头像地址
     */
    public record UserProfile(Long id, String publicId, String username, String displayName, String avatarUrl) {
    }

    /**
     * 生成便于人工输入且规避易混淆字符的公开用户 ID。
     *
     * <p>公开 ID 使用密码学安全随机源并由数据库唯一约束兜底；
     * 注册流程遇到极低概率碰撞时会在统一次数内自动重试。</p>
     *
     * @return 以 N 开头的固定长度公开 ID
     */
    private String randomPublicId() {
        String alphabet = SystemText.PUBLIC_ID_ALPHABET.value();
        StringBuilder result = new StringBuilder(SystemText.PUBLIC_ID_PREFIX.value());
        for (int index = BusinessRule.ZERO_COUNT.value();
                index < BusinessRule.PUBLIC_ID_RANDOM_LENGTH.value(); index++) {
            result.append(alphabet.charAt(secureRandom.nextInt(alphabet.length())));
        }
        return result.toString();
    }

}

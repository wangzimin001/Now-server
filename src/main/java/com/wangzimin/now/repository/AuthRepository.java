package com.wangzimin.now.repository;

import java.sql.Timestamp;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import com.wangzimin.now.domain.SystemText;

/**
 * 负责账号和刷新令牌的数据访问。
 *
 * <p>该仓储只处理 SQL、参数绑定、主键读取和查询结果映射；
 * 密码校验、令牌生成及异常语义由鉴权服务负责。</p>
 */
@Repository
public class AuthRepository {

    private final JdbcClient jdbcClient;

    /**
     * 创建账号仓储。
     *
     * @param jdbcClient Spring JDBC 数据访问客户端
     */
    public AuthRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 插入一个启用账号。
     *
     * @param username 规范化用户名
     * @param passwordHash BCrypt 密码摘要
     * @param displayName 展示名称
     * @return 数据库生成的账号主键
     */
    public long insertUser(String username, String passwordHash, String displayName) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient.sql("""
                        INSERT INTO app_user (username, password_hash, display_name, enabled)
                        VALUES (:username, :passwordHash, :displayName, TRUE)
                        """)
                .param("username", username)
                .param("passwordHash", passwordHash)
                .param("displayName", displayName)
                .update(keyHolder, "id");
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException(SystemText.ACCOUNT_KEY_MISSING.value());
        }
        return key.longValue();
    }

    /**
     * 按用户名查询启用账号及密码摘要。
     *
     * @param username 规范化用户名
     * @return 可空账号行
     */
    public Optional<UserRow> findEnabledUserByUsername(String username) {
        return jdbcClient.sql("""
                        SELECT id, username, display_name AS displayName, password_hash AS passwordHash
                        FROM app_user
                        WHERE username = :username AND enabled = TRUE
                        """)
                .param("username", username)
                .query(UserRow.class)
                .optional();
    }

    /**
     * 查询可用于轮换的刷新令牌及账号资料。
     *
     * @param tokenHash 刷新令牌 SHA-256 摘要
     * @return 可空刷新令牌行
     */
    public Optional<RefreshTokenRow> findActiveRefreshToken(String tokenHash) {
        return jdbcClient.sql("""
                        SELECT rt.id, rt.user_id AS userId, u.username, u.display_name AS displayName
                        FROM auth_refresh_token rt
                        JOIN app_user u ON u.id = rt.user_id AND u.enabled = TRUE
                        WHERE rt.token_hash = :tokenHash
                          AND rt.revoked_at IS NULL
                          AND rt.expires_at > CURRENT_TIMESTAMP
                        """)
                .param("tokenHash", tokenHash)
                .query(RefreshTokenRow.class)
                .optional();
    }

    /**
     * 按主键撤销已使用的刷新令牌。
     *
     * @param refreshTokenId 刷新令牌记录主键
     */
    public void revokeRefreshToken(long refreshTokenId) {
        jdbcClient.sql("""
                        UPDATE auth_refresh_token
                        SET revoked_at = CURRENT_TIMESTAMP, last_used_at = CURRENT_TIMESTAMP
                        WHERE id = :id
                        """)
                .param("id", refreshTokenId)
                .update();
    }

    /**
     * 按摘要幂等撤销刷新令牌。
     *
     * @param tokenHash 刷新令牌摘要
     */
    public void revokeRefreshTokenByHash(String tokenHash) {
        jdbcClient.sql("""
                        UPDATE auth_refresh_token
                        SET revoked_at = CURRENT_TIMESTAMP
                        WHERE token_hash = :tokenHash AND revoked_at IS NULL
                        """)
                .param("tokenHash", tokenHash)
                .update();
    }

    /**
     * 查询启用账号的公开资料。
     *
     * @param userId 用户主键
     * @return 可空公开资料行
     */
    public Optional<UserProfileRow> findEnabledUserProfile(Long userId) {
        return jdbcClient.sql("""
                        SELECT id, username, display_name AS displayName
                        FROM app_user
                        WHERE id = :id AND enabled = TRUE
                        """)
                .param("id", userId)
                .query(UserProfileRow.class)
                .optional();
    }

    /**
     * 保存刷新令牌摘要及过期时间。
     *
     * @param userId 用户主键
     * @param tokenHash 刷新令牌摘要
     * @param expiresAt 过期时间
     */
    public void insertRefreshToken(Long userId, String tokenHash, Timestamp expiresAt) {
        jdbcClient.sql("""
                        INSERT INTO auth_refresh_token (user_id, token_hash, expires_at)
                        VALUES (:userId, :tokenHash, :expiresAt)
                        """)
                .param("userId", userId)
                .param("tokenHash", tokenHash)
                .param("expiresAt", expiresAt)
                .update();
    }

    /**
     * 鉴权查询使用的账号行。
     *
     * @param id 用户主键
     * @param username 用户名
     * @param displayName 展示名称
     * @param passwordHash 密码摘要
     */
    public record UserRow(Long id, String username, String displayName, String passwordHash) {
        /**
         * 返回不携带密码摘要的安全副本。
         *
         * @return 安全账号行
         */
        public UserRow withoutPassword() {
            return new UserRow(id, username, displayName, SystemText.EMPTY.value());
        }
    }

    /**
     * 刷新令牌轮换查询行。
     *
     * @param id 刷新令牌主键
     * @param userId 用户主键
     * @param username 用户名
     * @param displayName 展示名称
     */
    public record RefreshTokenRow(Long id, Long userId, String username, String displayName) {
    }

    /**
     * 公开账号资料查询行。
     *
     * @param id 用户主键
     * @param username 用户名
     * @param displayName 展示名称
     */
    public record UserProfileRow(Long id, String username, String displayName) {
    }
}

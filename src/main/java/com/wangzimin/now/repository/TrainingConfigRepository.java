package com.wangzimin.now.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.QueryLockMode;
import com.wangzimin.now.domain.SystemText;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 负责用户训练配置的查询、加锁和持久化。
 *
 * <p>JSON 解析属于数据库结果映射的一部分，因此在仓储边界完成；
 * 周期结构校验和版本冲突决策仍由业务服务负责。</p>
 */
@Repository
public class TrainingConfigRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    /**
     * 创建训练配置仓储。
     *
     * @param jdbcClient Spring JDBC 数据访问客户端
     * @param objectMapper 数据库 JSON 映射器
     */
    public TrainingConfigRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 按账号读取配置，并按需附加悲观写锁。
     *
     * @param userId 用户主键
     * @param lockMode 查询锁模式
     * @return 可空配置行
     */
    public Optional<TrainingConfigRow> findByUserId(Long userId, QueryLockMode lockMode) {
        return jdbcClient.sql("""
                        SELECT training_mode, cycle_plan, client_updated_at, updated_at, revision
                        FROM user_training_config
                        WHERE user_id = :userId
                        """ + lockMode.sqlSuffix())
                .param("userId", userId)
                .query(this::mapRow)
                .optional();
    }

    /**
     * 插入账号首次训练配置。
     *
     * @param userId 用户主键
     * @param trainingMode 训练模式外部值
     * @param cyclePlan 周期 JSON 文本
     * @param clientUpdatedAt 客户端修改时间
     */
    public void insert(Long userId, String trainingMode, String cyclePlan, Instant clientUpdatedAt) {
        jdbcClient.sql("""
                        INSERT INTO user_training_config
                            (user_id, training_mode, cycle_plan, client_updated_at, revision)
                        VALUES (:userId, :trainingMode, CAST(:cyclePlan AS JSON), :clientUpdatedAt, :initialRevision)
                        """)
                .param("userId", userId)
                .param("trainingMode", trainingMode)
                .param("cyclePlan", cyclePlan)
                .param("clientUpdatedAt", Timestamp.from(clientUpdatedAt))
                .param("initialRevision", BusinessRule.INITIAL_REVISION.value())
                .update();
    }

    /**
     * 更新已有账号训练配置并递增修订号。
     *
     * @param userId 用户主键
     * @param trainingMode 训练模式外部值
     * @param cyclePlan 周期 JSON 文本
     * @param clientUpdatedAt 客户端修改时间
     */
    public void update(Long userId, String trainingMode, String cyclePlan, Instant clientUpdatedAt) {
        jdbcClient.sql("""
                        UPDATE user_training_config
                        SET training_mode = :trainingMode,
                            cycle_plan = CAST(:cyclePlan AS JSON),
                            client_updated_at = :clientUpdatedAt,
                            revision = revision + :revisionIncrement
                        WHERE user_id = :userId
                        """)
                .param("trainingMode", trainingMode)
                .param("cyclePlan", cyclePlan)
                .param("clientUpdatedAt", Timestamp.from(clientUpdatedAt))
                .param("userId", userId)
                .param("revisionIncrement", BusinessRule.INITIAL_REVISION.value())
                .update();
    }

    /**
     * 将 JDBC 结果行转换为训练配置行。
     *
     * @param resultSet 当前结果集
     * @param rowNumber Spring JDBC 行号
     * @return 完整配置行
     * @throws SQLException 字段读取或 JSON 解析失败时抛出
     */
    private TrainingConfigRow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            String rawPlan = resultSet.getString("cycle_plan");
            JsonNode cyclePlan = rawPlan == null ? null : objectMapper.readTree(rawPlan);
            return new TrainingConfigRow(
                    resultSet.getString("training_mode"),
                    cyclePlan,
                    resultSet.getTimestamp("client_updated_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant(),
                    resultSet.getLong("revision"));
        } catch (JacksonException exception) {
            throw new SQLException(SystemText.TRAINING_CONFIG_JSON_CORRUPTED.value(), exception);
        }
    }

    /**
     * 数据库中的训练配置投影。
     *
     * @param trainingMode 训练模式
     * @param cyclePlan 周期 JSON
     * @param clientUpdatedAt 客户端修改时间
     * @param serverUpdatedAt 服务端修改时间
     * @param revision 修订号
     */
    public record TrainingConfigRow(
            String trainingMode,
            JsonNode cyclePlan,
            Instant clientUpdatedAt,
            Instant serverUpdatedAt,
            long revision) {
    }
}

package com.wangzimin.now.training;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.QueryLockMode;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.domain.TrainingMode;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 保存并同步每个账号的训练模式和周期计划。
 *
 * <p>服务通过用户主键实现严格数据隔离，并使用客户端修改时间避免离线旧版本覆盖新版本。
 * 周期计划以 JSON 保存，但在写库前验证结构、天数和字节上限。
 * 更新路径使用悲观锁保证并发检查与修订号递增的一致性。</p>
 */
@Service
public class TrainingConfigService {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    /**
     * 创建训练配置服务。
     *
     * @param jdbcClient 执行配置 SQL 的客户端
     * @param objectMapper 解析和序列化周期 JSON 的映射器
     */
    public TrainingConfigService(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询一个账号的当前训练配置。
     *
     * <p>首次使用尚无数据库记录时返回明确的自由训练空配置。</p>
     *
     * @param userId 当前账号主键
     * @return 已保存配置或空配置
     */
    public TrainingConfigResponse get(Long userId) {
        return find(userId, QueryLockMode.NONE).orElseGet(TrainingConfigResponse::empty);
    }

    /**
     * 保存账号训练配置并处理离线版本冲突。
     *
     * <p>方法先锁定现有行；若客户端时间更旧，返回数据库最新版本且 applied 为 false。
     * 新增和更新都在同一事务中完成，并由数据库维护服务端更新时间。</p>
     *
     * @param userId 当前账号主键
     * @param request 训练模式、周期和客户端修改时间
     * @return 数据库中的最终配置
     */
    @Transactional
    public TrainingConfigResponse save(Long userId, TrainingConfigRequest request) {
        validateCyclePlan(request.cyclePlan());
        TrainingMode trainingMode = TrainingMode.fromExternalValue(request.trainingMode());
        if (trainingMode == null) {
            throw ApiErrorCode.TRAINING_MODE_INVALID.exception();
        }
        var existing = find(userId, QueryLockMode.FOR_UPDATE);
        if (existing.isPresent() && request.clientUpdatedAt().isBefore(existing.get().clientUpdatedAt())) {
            return existing.get().withApplied(false);
        }

        String cyclePlan = writeCyclePlan(request.cyclePlan());
        if (existing.isEmpty()) {
            jdbcClient.sql("""
                            INSERT INTO user_training_config
                                (user_id, training_mode, cycle_plan, client_updated_at, revision)
                            VALUES (:userId, :trainingMode, CAST(:cyclePlan AS JSON), :clientUpdatedAt, :initialRevision)
                            """)
                    .param("userId", userId)
                    .param("trainingMode", trainingMode.externalValue())
                    .param("cyclePlan", cyclePlan)
                    .param("clientUpdatedAt", Timestamp.from(request.clientUpdatedAt()))
                    .param("initialRevision", BusinessRule.INITIAL_REVISION.value())
                    .update();
        } else {
            jdbcClient.sql("""
                            UPDATE user_training_config
                            SET training_mode = :trainingMode,
                                cycle_plan = CAST(:cyclePlan AS JSON),
                                client_updated_at = :clientUpdatedAt,
                                revision = revision + :revisionIncrement
                            WHERE user_id = :userId
                            """)
                    .param("trainingMode", trainingMode.externalValue())
                    .param("cyclePlan", cyclePlan)
                    .param("clientUpdatedAt", Timestamp.from(request.clientUpdatedAt()))
                    .param("userId", userId)
                    .param("revisionIncrement", BusinessRule.INITIAL_REVISION.value())
                    .update();
        }
        return find(userId, QueryLockMode.NONE)
                .orElseThrow(() -> new IllegalStateException(SystemText.TRAINING_CONFIG_UNREADABLE.value()));
    }

    /**
     * 按账号查找训练配置，并选择是否附加写锁。
     *
     * <p>锁片段来自受控枚举，不接收任何外部 SQL 文本。</p>
     *
     * @param userId 当前账号主键
     * @param lockMode 无锁读取或 FOR UPDATE
     * @return 可空配置响应
     */
    private java.util.Optional<TrainingConfigResponse> find(Long userId, QueryLockMode lockMode) {
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
     * 将 JDBC 结果行映射为训练配置响应。
     *
     * <p>数据库 JSON 在此边界解析；损坏数据转换为 SQLException，交由数据访问层处理。</p>
     *
     * @param resultSet 当前查询结果
     * @param rowNumber Spring JDBC 行号
     * @return 完整训练配置响应
     * @throws SQLException 字段读取或 JSON 解析失败时抛出
     */
    private TrainingConfigResponse mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        try {
            String rawPlan = resultSet.getString("cycle_plan");
            JsonNode cyclePlan = rawPlan == null ? null : objectMapper.readTree(rawPlan);
            return new TrainingConfigResponse(
                    resultSet.getString("training_mode"),
                    cyclePlan,
                    resultSet.getTimestamp("client_updated_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant(),
                    resultSet.getLong("revision"),
                    true);
        } catch (JacksonException exception) {
            throw new SQLException(SystemText.TRAINING_CONFIG_JSON_CORRUPTED.value(), exception);
        }
    }

    /**
     * 验证可空训练周期 JSON 的结构和体积。
     *
     * <p>有效周期必须是对象，包含规则范围内的非空 days 数组，并低于最大字节数。</p>
     *
     * @param cyclePlan 客户端提交的周期 JSON
     */
    private void validateCyclePlan(JsonNode cyclePlan) {
        if (cyclePlan == null || cyclePlan.isNull()) return;
        if (!cyclePlan.isObject()) throw ApiErrorCode.TRAINING_CYCLE_FORMAT.exception();
        JsonNode days = cyclePlan.path("days");
        if (!days.isArray() || days.size() < BusinessRule.TRAINING_CYCLE_MIN_DAYS.value()
                || days.size() > BusinessRule.TRAINING_CYCLE_MAX_DAYS.value()) {
            throw ApiErrorCode.TRAINING_CYCLE_DAYS.exception();
        }
        if (writeCyclePlan(cyclePlan).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > BusinessRule.TRAINING_PLAN_MAX_BYTES.value()) {
            throw ApiErrorCode.TRAINING_CYCLE_TOO_LARGE.exception();
        }
    }

    /**
     * 将周期 JSON 序列化为数据库可接受的文本。
     *
     * <p>空周期显式写为 JSON null；序列化失败统一转换为友好请求错误。</p>
     *
     * @param cyclePlan 可空周期 JSON
     * @return 合法 JSON 文本
     */
    private String writeCyclePlan(JsonNode cyclePlan) {
        try {
            return cyclePlan == null || cyclePlan.isNull()
                    ? SystemText.EMPTY_JSON.value()
                    : objectMapper.writeValueAsString(cyclePlan);
        } catch (JacksonException exception) {
            throw ApiErrorCode.TRAINING_CYCLE_FORMAT.exception();
        }
    }

    /**
     * 描述客户端提交的训练配置。
     *
     * @param trainingMode 训练模式外部值
     * @param cyclePlan 可空周期计划
     * @param clientUpdatedAt 客户端最后修改时间
     */
    public record TrainingConfigRequest(
            @NotBlank String trainingMode,
            JsonNode cyclePlan,
            @NotNull Instant clientUpdatedAt) {
    }

    /**
     * 描述服务端保存的训练配置及同步结果。
     *
     * @param trainingMode 训练模式外部值
     * @param cyclePlan 可空周期计划
     * @param clientUpdatedAt 客户端修改时间
     * @param serverUpdatedAt 服务端更新时间
     * @param revision 递增修订号
     * @param applied 本次提交是否真正应用
     */
    public record TrainingConfigResponse(
            String trainingMode,
            JsonNode cyclePlan,
            Instant clientUpdatedAt,
            Instant serverUpdatedAt,
            long revision,
            boolean applied) {

        /**
         * 创建首次使用时的自由训练空配置。
         *
         * @return 未持久化的初始响应
         */
        static TrainingConfigResponse empty() {
            return new TrainingConfigResponse(
                    TrainingMode.FREE.externalValue(), null, null, null,
                    BusinessRule.EMPTY_REVISION.longValue(), true);
        }

        /**
         * 复制当前配置并替换本次应用标记。
         *
         * @param value 新应用标记
         * @return 保留所有版本数据的响应副本
         */
        TrainingConfigResponse withApplied(boolean value) {
            return new TrainingConfigResponse(trainingMode, cyclePlan, clientUpdatedAt, serverUpdatedAt, revision, value);
        }
    }
}

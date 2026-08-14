package com.wangzimin.now.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wangzimin.now.repository.WorkoutRepository;
import com.wangzimin.now.repository.WorkoutRepository.ExercisePerformanceSummary;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutCompletionRequest;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutCompletionResponse;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutExerciseRequest;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutHistoryUpdateRequest;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutPlanRequest;

/**
 * 编排训练模板、训练完成和历史纠错写入用例。
 *
 * <p>事务边界位于服务层；SQL、主键读取和数据库结果映射全部委托给训练仓储。
 * 服务公开的方法对应控制器用例，避免 HTTP 层直接依赖 JDBC。</p>
 */
@Service
public class WorkoutService {

    private final WorkoutRepository repository;

    /**
     * 创建训练写服务。
     *
     * @param repository 训练持久化仓储
     */
    public WorkoutService(WorkoutRepository repository) {
        this.repository = repository;
    }

    /**
     * 创建无用户归属模板。
     *
     * @param request 模板请求
     * @return 模板主键
     */
    @Transactional
    public Long createPlan(WorkoutPlanRequest request) {
        return repository.createPlan(request);
    }

    /**
     * 为用户创建训练模板。
     *
     * @param userId 用户主键
     * @param request 模板请求
     * @return 模板主键
     */
    @Transactional
    public Long createPlan(Long userId, WorkoutPlanRequest request) {
        return repository.createPlan(userId, request);
    }

    /**
     * 更新无用户归属模板。
     *
     * @param planId 模板主键
     * @param request 模板请求
     */
    @Transactional
    public void updatePlan(Long planId, WorkoutPlanRequest request) {
        repository.updatePlan(planId, request);
    }

    /**
     * 更新用户拥有的训练模板。
     *
     * @param userId 用户主键
     * @param planId 模板主键
     * @param request 模板请求
     */
    @Transactional
    public void updatePlan(Long userId, Long planId, WorkoutPlanRequest request) {
        repository.updatePlan(userId, planId, request);
    }

    /**
     * 删除无用户归属模板。
     *
     * @param planId 模板主键
     */
    @Transactional
    public void deletePlan(Long planId) {
        repository.deletePlan(planId);
    }

    /**
     * 删除个人模板或隐藏系统模板。
     *
     * @param userId 用户主键
     * @param planId 模板主键
     */
    @Transactional
    public void deletePlan(Long userId, Long planId) {
        repository.deletePlan(userId, planId);
    }

    /**
     * 保存无用户归属训练。
     *
     * @param request 完整训练请求
     * @return 训练完成结果
     */
    @Transactional
    public WorkoutCompletionResponse completeWorkout(WorkoutCompletionRequest request) {
        return repository.completeWorkout(request);
    }

    /**
     * 保存当前用户训练。
     *
     * @param userId 用户主键
     * @param request 完整训练请求
     * @return 训练完成结果
     */
    @Transactional
    public WorkoutCompletionResponse completeWorkout(Long userId, WorkoutCompletionRequest request) {
        return repository.completeWorkout(userId, request);
    }

    /**
     * 纠正训练历史并重新计算容量。
     *
     * @param userId 用户主键
     * @param sessionId 会话主键
     * @param request 完整纠错请求
     * @return 重算总容量
     */
    @Transactional
    public BigDecimal updateWorkoutHistory(
            Long userId, Long sessionId, WorkoutHistoryUpdateRequest request) {
        return repository.updateWorkoutHistory(userId, sessionId, request);
    }

    /**
     * 软删除当前用户训练历史。
     *
     * @param userId 用户主键
     * @param sessionId 会话主键
     */
    @Transactional
    public void deleteWorkoutHistory(Long userId, Long sessionId) {
        repository.deleteWorkoutHistory(userId, sessionId);
    }

    /**
     * 计算动作相对历史基线的表现，供领域测试和完成训练用例复用。
     *
     * @param userId 用户主键
     * @param exercises 当前训练动作
     * @return 动作表现摘要
     */
    List<ExercisePerformanceSummary> evaluateExercisePerformance(
            Long userId, List<WorkoutExerciseRequest> exercises) {
        return repository.evaluateExercisePerformance(userId, exercises);
    }
}

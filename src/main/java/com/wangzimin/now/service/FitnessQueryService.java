package com.wangzimin.now.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;

import com.wangzimin.now.repository.FitnessQueryRepository;
import com.wangzimin.now.repository.FitnessQueryRepository.DashboardResponse;
import com.wangzimin.now.repository.FitnessQueryRepository.ExerciseCategory;
import com.wangzimin.now.repository.FitnessQueryRepository.ExercisePageResponse;
import com.wangzimin.now.repository.FitnessQueryRepository.LatestExercisePerformance;
import com.wangzimin.now.repository.FitnessQueryRepository.ExerciseProgressResponse;
import com.wangzimin.now.repository.FitnessQueryRepository.WorkoutHistoryDetail;
import com.wangzimin.now.repository.FitnessQueryRepository.WorkoutHistoryItem;
import com.wangzimin.now.repository.FitnessQueryRepository.WorkoutPlanResponse;

/**
 * 编排健身看板、动作库、训练模板和历史记录的只读用例。
 *
 * <p>服务层不接触 JDBC 或 SQL，只表达应用用例边界；
 * 查询、参数绑定和数据库投影映射全部由查询仓储完成。</p>
 */
@Service
public class FitnessQueryService {

    private final FitnessQueryRepository repository;

    /**
     * 创建健身查询服务。
     *
     * @param repository 健身只读查询仓储
     */
    public FitnessQueryService(FitnessQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * 查询匿名访问看板。
     *
     * @return 公共看板
     */
    public DashboardResponse dashboard() {
        return repository.dashboard();
    }

    /**
     * 查询当前用户看板。
     *
     * @param userId 可空用户主键
     * @return 用户可见看板
     */
    public DashboardResponse dashboard(Long userId) {
        return repository.dashboard(userId);
    }

    /**
     * 查询匿名可见训练模板。
     *
     * @return 公共模板列表
     */
    public List<WorkoutPlanResponse> workoutPlans() {
        return repository.workoutPlans();
    }

    /**
     * 查询当前用户可见训练模板。
     *
     * @param userId 可空用户主键
     * @return 过滤隐藏关系后的模板列表
     */
    public List<WorkoutPlanResponse> workoutPlans(Long userId) {
        return repository.workoutPlans(userId);
    }

    /**
     * 查询匿名可见模板详情。
     *
     * @param planId 模板主键
     * @return 模板详情
     */
    public WorkoutPlanResponse workoutPlan(Long planId) {
        return repository.workoutPlan(planId);
    }

    /**
     * 查询当前用户可见模板详情。
     *
     * @param planId 模板主键
     * @param userId 可空用户主键
     * @return 模板详情
     */
    public WorkoutPlanResponse workoutPlan(Long planId, Long userId) {
        return repository.workoutPlan(planId, userId);
    }

    /**
     * 按通用分类条件分页查询动作。
     *
     * @param category 一级分类编码
     * @param subcategory 二级分类编码
     * @param keyword 搜索词
     * @param page 页码
     * @param limit 每页数量
     * @return 动作分页
     */
    public ExercisePageResponse exercises(
            String category, String subcategory, String keyword, Integer page, Integer limit) {
        return repository.exercises(category, subcategory, keyword, page, limit);
    }

    /**
     * 查询数据库驱动的动作分类树。
     *
     * @return 一级分类及二级分类
     */
    public List<ExerciseCategory> exerciseCategories() {
        return repository.exerciseCategories();
    }

    /**
     * 查询匿名历史记录。
     *
     * @return 匿名历史列表
     */
    public List<WorkoutHistoryItem> history() {
        return repository.history();
    }

    /**
     * 查询当前用户历史记录。
     *
     * @param userId 可空用户主键
     * @return 用户历史列表
     */
    public List<WorkoutHistoryItem> history(Long userId) {
        return repository.history(userId);
    }

    /**
     * 查询匿名训练历史详情。
     *
     * @param sessionId 会话主键
     * @return 历史详情
     */
    public WorkoutHistoryDetail historyDetail(Long sessionId) {
        return repository.historyDetail(sessionId);
    }

    /**
     * 查询当前用户训练历史详情。
     *
     * @param sessionId 会话主键
     * @param userId 可空用户主键
     * @return 历史详情
     */
    public WorkoutHistoryDetail historyDetail(Long sessionId, Long userId) {
        return repository.historyDetail(sessionId, userId);
    }

    /**
     * 批量查询动作最近两次完成表现。
     *
     * @param userId 当前用户主键
     * @param exerciseIds 动作主键集合
     * @return 包含连续两次证据的最近表现列表
     */
    public List<LatestExercisePerformance> latestExercisePerformances(
            Long userId, Collection<Long> exerciseIds) {
        return repository.latestExercisePerformances(userId, exerciseIds);
    }

    /**
     * 查询一个动作在当前账号下的完整成长记录。
     *
     * @param userId 当前用户主键
     * @param exerciseId 动作库主键
     * @return 动作元数据、完成训练和组记录
     */
    public ExerciseProgressResponse exerciseProgress(Long userId, Long exerciseId) {
        return repository.exerciseProgress(userId, exerciseId);
    }
}

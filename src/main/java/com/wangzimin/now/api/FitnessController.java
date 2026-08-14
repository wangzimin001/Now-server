package com.wangzimin.now.api;

import java.util.List;

import com.wangzimin.now.service.FitnessQueryService;
import com.wangzimin.now.repository.FitnessQueryRepository.DashboardResponse;
import com.wangzimin.now.repository.FitnessQueryRepository.ExerciseCategory;
import com.wangzimin.now.repository.FitnessQueryRepository.ExercisePageResponse;
import com.wangzimin.now.repository.FitnessQueryRepository.WorkoutHistoryItem;
import com.wangzimin.now.repository.FitnessQueryRepository.WorkoutHistoryDetail;
import com.wangzimin.now.repository.FitnessQueryRepository.LatestExercisePerformance;
import com.wangzimin.now.repository.FitnessQueryRepository.WorkoutPlanResponse;
import com.wangzimin.now.service.WorkoutService;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutCompletionRequest;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutCompletionResponse;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutHistoryUpdateRequest;
import com.wangzimin.now.repository.WorkoutRepository.WorkoutPlanRequest;
import com.wangzimin.now.domain.ValidationRule;
import com.wangzimin.now.domain.ApiPath;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 汇总训练看板、动作库、训练模板和训练历史 HTTP 接口。
 *
 * <p>控制器只负责请求映射、鉴权主体提取和服务编排，不直接执行 SQL。
 * 所有用户数据入口都从已验证 JWT 提取用户主键，禁止客户端指定数据归属。</p>
 */
@RestController
@RequestMapping(ApiPath.API_ROOT)
public class FitnessController {

    private final FitnessQueryService fitnessQueryService;
    private final WorkoutService workoutService;

    /**
     * 创建健身聚合控制器。
     *
     * @param fitnessQueryService 只读查询服务
     * @param workoutService 训练写入服务
     */
    public FitnessController(FitnessQueryService fitnessQueryService, WorkoutService workoutService) {
        this.fitnessQueryService = fitnessQueryService;
        this.workoutService = workoutService;
    }

    /**
     * 查询当前用户可见的训练看板。
     *
     * <p>匿名访问保留公共模板数据；登录后会加入用户自己的模板和历史摘要。</p>
     *
     * @param jwt 可空的已验证 JWT
     * @return 今日计划、指标、周摘要和最近训练
     */
    @GetMapping(ApiPath.DASHBOARD_SEGMENT)
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        return fitnessQueryService.dashboard(userId(jwt));
    }

    /**
     * 查询当前用户可见的系统模板和个人模板。
     *
     * @param jwt 可空的已验证 JWT
     * @return 过滤隐藏模板后的完整列表
     */
    @GetMapping(ApiPath.WORKOUT_PLANS_SEGMENT)
    public List<WorkoutPlanResponse> workoutPlans(@AuthenticationPrincipal Jwt jwt) {
        return fitnessQueryService.workoutPlans(userId(jwt));
    }

    /**
     * 为当前登录账号创建个人训练模板。
     *
     * <p>服务层完成事务写入后重新通过查询服务读取响应，确保返回结构与列表接口一致。</p>
     *
     * @param jwt 已验证 JWT
     * @param request 模板名称、时长和动作列表
     * @return 已持久化的模板
     */
    @PostMapping(ApiPath.WORKOUT_PLANS_SEGMENT)
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutPlanResponse createWorkoutPlan(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutPlanRequest request) {
        Long userId = userId(jwt);
        Long planId = workoutService.createPlan(userId, request);
        return fitnessQueryService.workoutPlan(planId, userId);
    }

    /**
     * 更新当前账号拥有的个人训练模板。
     *
     * @param jwt 已验证 JWT
     * @param planId 待更新模板主键
     * @param request 新模板内容
     * @return 更新后的模板
     */
    @PutMapping(ApiPath.WORKOUT_PLAN_SEGMENT)
    public WorkoutPlanResponse updateWorkoutPlan(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long planId,
            @Valid @RequestBody WorkoutPlanRequest request) {
        Long userId = userId(jwt);
        workoutService.updatePlan(userId, planId, request);
        return fitnessQueryService.workoutPlan(planId, userId);
    }

    /**
     * 删除个人模板或为当前用户隐藏系统模板。
     *
     * <p>具体策略由写服务根据模板归属决定，控制器保持统一无正文响应。</p>
     *
     * @param jwt 已验证 JWT
     * @param planId 目标模板主键
     */
    @DeleteMapping(ApiPath.WORKOUT_PLAN_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkoutPlan(@AuthenticationPrincipal Jwt jwt, @PathVariable Long planId) {
        workoutService.deletePlan(userId(jwt), planId);
    }

    /**
     * 分页查询数据库驱动的标准动作库。
     *
     * <p>二级分类使用单一 subcategory 编码，不再暴露按身体部位写死的请求参数。
     * 服务层会验证二级分类与动作一级分类的关联。</p>
     *
     * @param category 可空一级分类编码
     * @param subcategory 可空二级分类编码
     * @param keyword 可空搜索关键词
     * @param page 从一开始的页码
     * @param limit 每页数量
     * @return 动作分页和动态二级分类
     */
    @GetMapping(ApiPath.EXERCISES_SEGMENT)
    public ExercisePageResponse exercises(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = ValidationRule.EXERCISE_DEFAULT_PAGE) Integer page,
            @RequestParam(defaultValue = ValidationRule.EXERCISE_DEFAULT_LIMIT) Integer limit) {
        return fitnessQueryService.exercises(category, subcategory, keyword, page, limit);
    }

    /**
     * 查询动作分类树和各一级分类的动作数量。
     *
     * @return 数据库排序的一级分类及其二级分类
     */
    @GetMapping(ApiPath.EXERCISE_CATEGORIES_SEGMENT)
    public List<ExerciseCategory> exerciseCategories() {
        return fitnessQueryService.exerciseCategories();
    }

    /**
     * 查询当前账号最近的已完成训练摘要。
     *
     * @param jwt 已验证 JWT
     * @return 仅属于当前账号的训练历史
     */
    @GetMapping(ApiPath.WORKOUT_HISTORY_SEGMENT)
    public List<WorkoutHistoryItem> history(@AuthenticationPrincipal Jwt jwt) {
        return fitnessQueryService.history(userId(jwt));
    }

    /**
     * 查询一条训练历史的动作和组明细。
     *
     * @param jwt 已验证 JWT
     * @param sessionId 训练会话主键
     * @return 账号隔离后的训练详情
     */
    @GetMapping(ApiPath.WORKOUT_HISTORY_DETAIL_SEGMENT)
    public WorkoutHistoryDetail historyDetail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) {
        return fitnessQueryService.historyDetail(sessionId, userId(jwt));
    }

    /**
     * 纠正一条训练历史中的组数据。
     *
     * <p>写服务在事务中校验动作归属、重建组记录并重算容量，随后读取最新详情。</p>
     *
     * @param jwt 已验证 JWT
     * @param sessionId 训练会话主键
     * @param request 完整动作与组记录
     * @return 更新后的训练详情
     */
    @PutMapping(ApiPath.WORKOUT_HISTORY_DETAIL_SEGMENT)
    public WorkoutHistoryDetail updateHistoryDetail(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long sessionId, @Valid @RequestBody WorkoutHistoryUpdateRequest request) {
        Long userId = userId(jwt);
        workoutService.updateWorkoutHistory(userId, sessionId, request);
        return fitnessQueryService.historyDetail(sessionId, userId);
    }

    /**
     * 将当前账号的一条已完成训练软删除。
     *
     * @param jwt 已验证 JWT
     * @param sessionId 训练会话主键
     */
    @DeleteMapping(ApiPath.WORKOUT_HISTORY_DETAIL_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHistory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long sessionId) {
        workoutService.deleteWorkoutHistory(userId(jwt), sessionId);
    }

    /**
     * 批量查询动作在当前账号中的最近完成表现。
     *
     * @param jwt 已验证 JWT
     * @param exerciseIds 去重前的动作主键集合
     * @return 每个动作最近一次训练的完成组
     */
    @GetMapping(ApiPath.LATEST_PERFORMANCE_SEGMENT)
    public List<LatestExercisePerformance> latestExercisePerformances(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam List<Long> exerciseIds) {
        return fitnessQueryService.latestExercisePerformances(userId(jwt), exerciseIds);
    }

    /**
     * 保存一次完成训练并计算个人纪录和同类训练对比。
     *
     * @param jwt 已验证 JWT
     * @param request 完整训练快照
     * @return 会话主键、容量、纪录和对比结果
     */
    @PostMapping(ApiPath.WORKOUTS_SEGMENT)
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutCompletionResponse completeWorkout(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody WorkoutCompletionRequest request) {
        return workoutService.completeWorkout(userId(jwt), request);
    }

    /**
     * 从可空 JWT 中提取用户主键。
     *
     * <p>公共只读接口允许空主体；需要登录的接口由安全过滤器保证 JWT 非空。</p>
     *
     * @param jwt 可空的已验证令牌
     * @return 用户主键，匿名请求返回空值
     */
    private Long userId(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}

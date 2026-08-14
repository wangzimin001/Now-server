package com.wangzimin.now.training;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import com.wangzimin.now.domain.ApiPath;

/**
 * 提供登录账号的训练周期配置同步接口。
 *
 * <p>用户主键只从 JWT 主体获取，客户端无法读取或覆盖其他账号的配置。
 * 版本冲突与周期结构校验由服务层统一处理。</p>
 */
@RestController
@RequestMapping(ApiPath.TRAINING_CONFIG)
public class TrainingConfigController {

    private final TrainingConfigService service;

    /**
     * 创建训练配置控制器。
     *
     * @param service 负责账号隔离与版本控制的配置服务
     */
    public TrainingConfigController(TrainingConfigService service) {
        this.service = service;
    }

    /**
     * 查询当前账号保存的训练配置。
     *
     * @param jwt 已验证 JWT
     * @return 已保存配置；首次使用时返回空配置
     */
    @GetMapping
    public TrainingConfigService.TrainingConfigResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(Long.valueOf(jwt.getSubject()));
    }

    /**
     * 保存或更新当前账号的训练配置。
     *
     * <p>客户端时间早于服务端记录时不会覆盖较新数据，响应的 applied 字段会明确结果。</p>
     *
     * @param jwt 已验证 JWT
     * @param request 模式、周期 JSON 和客户端修改时间
     * @return 当前数据库中的最终配置
     */
    @PutMapping
    public TrainingConfigService.TrainingConfigResponse save(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TrainingConfigService.TrainingConfigRequest request) {
        return service.save(Long.valueOf(jwt.getSubject()), request);
    }
}

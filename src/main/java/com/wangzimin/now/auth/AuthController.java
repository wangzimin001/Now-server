package com.wangzimin.now.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import com.wangzimin.now.domain.ApiPath;

/**
 * 暴露账号注册、登录和令牌生命周期接口。
 *
 * <p>控制器只处理 HTTP 映射和参数校验，密码哈希、令牌轮换及账号查询全部委托给服务层。
 * 除当前用户接口外，其余入口由安全配置显式允许匿名访问。</p>
 */
@RestController
@RequestMapping(ApiPath.AUTH_ROOT)
public class AuthController {

    private final AuthService authService;
    private final UserProfileService profileService;

    /**
     * 创建鉴权控制器。
     *
     * @param authService 负责账号与令牌业务的服务
     * @param profileService 负责当前账号公开资料更新的服务
     */
    public AuthController(AuthService authService, UserProfileService profileService) {
        this.authService = authService;
        this.profileService = profileService;
    }

    /**
     * 注册新账号并立即签发访问令牌和刷新令牌。
     *
     * <p>请求字段先通过 Bean Validation，再由服务层执行规范化、唯一性检查和密码哈希。</p>
     *
     * @param request 已通过基础字段校验的注册信息
     * @return 新账号的令牌与安全用户资料
     */
    @PostMapping(ApiPath.REGISTER_SEGMENT)
    @ResponseStatus(HttpStatus.CREATED)
    public AuthService.AuthResponse register(@Valid @RequestBody AuthService.RegisterRequest request) {
        return authService.register(request);
    }

    /**
     * 使用用户名和密码登录。
     *
     * <p>用户名不存在和密码错误返回相同文案，避免暴露账号枚举信息。</p>
     *
     * @param request 登录凭据
     * @return 新签发的访问令牌、刷新令牌和用户资料
     */
    @PostMapping(ApiPath.LOGIN_SEGMENT)
    public AuthService.AuthResponse login(@Valid @RequestBody AuthService.LoginRequest request) {
        return authService.login(request);
    }

    /**
     * 轮换有效刷新令牌并签发新的令牌对。
     *
     * <p>服务层在同一事务中撤销旧令牌，防止同一刷新令牌被重复使用。</p>
     *
     * @param request 当前刷新令牌
     * @return 轮换后的令牌与用户资料
     */
    @PostMapping(ApiPath.REFRESH_SEGMENT)
    public AuthService.AuthResponse refresh(@Valid @RequestBody AuthService.RefreshRequest request) {
        return authService.refresh(request);
    }

    /**
     * 撤销调用方提供的刷新令牌。
     *
     * <p>请求体可为空，便于客户端在本地令牌已经丢失时仍完成幂等退出。</p>
     *
     * @param request 可空的刷新令牌请求
     */
    @PostMapping(ApiPath.LOGOUT_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) AuthService.RefreshRequest request) {
        authService.logout(request);
    }

    /**
     * 查询访问令牌所代表的当前账号。
     *
     * @param jwt 由资源服务器验证后的访问令牌
     * @return 当前账号的公开资料
     */
    @GetMapping(ApiPath.CURRENT_USER_SEGMENT)
    public AuthService.UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        return authService.profile(Long.valueOf(jwt.getSubject()));
    }

    /**
     * 上传并替换当前账号头像。
     *
     * @param jwt 由资源服务器验证后的访问令牌
     * @param file 仅允许图片类型的头像文件
     * @return 更新后的公开账号资料
     */
    @PostMapping(ApiPath.AVATAR_SEGMENT)
    public AuthService.UserProfile updateAvatar(@AuthenticationPrincipal Jwt jwt,
            @RequestPart(ApiPath.MULTIPART_FILE_PART) MultipartFile file) {
        return profileService.updateAvatar(Long.valueOf(jwt.getSubject()), file);
    }
}

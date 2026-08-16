package com.wangzimin.now.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.repository.AuthRepository;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.service.SocialFileService;

/**
 * 维护当前账号可由本人更新的公开资料。
 *
 * <p>头像文件先经过统一附件校验和持久化，再由账号仓储更新公开地址。
 * 其他社交查询均联表读取 {@code app_user.avatar_url}，因此修改后会自动反映到
 * 好友、会话、聊天消息和朋友圈。</p>
 */
@Service
public class UserProfileService {

    private final AuthRepository authRepository;
    private final SocialFileService fileService;

    /**
     * 创建用户资料服务。
     *
     * @param authRepository 账号资料仓储
     * @param fileService 社交文件服务
     */
    public UserProfileService(AuthRepository authRepository, SocialFileService fileService) {
        this.authRepository = authRepository;
        this.fileService = fileService;
    }

    /**
     * 保存当前用户头像并返回更新后的完整公开资料。
     *
     * @param userId JWT 中确认的用户主键
     * @param file 图片文件
     * @return 更新后的账号资料
     */
    @Transactional
    public AuthService.UserProfile updateAvatar(Long userId, MultipartFile file) {
        AttachmentRow avatar = fileService.storeAvatar(userId, file);
        authRepository.updateAvatar(userId, avatar.publicUrl());
        return authRepository.findEnabledUserProfile(userId)
                .map(row -> new AuthService.UserProfile(row.id(), row.publicId(), row.username(),
                        row.displayName(), row.avatarUrl()))
                .orElseThrow(ApiErrorCode.ACCOUNT_UNAVAILABLE::exception);
    }
}

package com.wangzimin.now.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wangzimin.now.domain.ApiErrorCode;
import com.wangzimin.now.domain.BusinessRule;
import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.domain.ValidationRule;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.repository.SocialMomentRepository;
import com.wangzimin.now.repository.SocialNotificationRepository;
import com.wangzimin.now.repository.SocialMomentRepository.CommentRow;
import com.wangzimin.now.repository.SocialMomentRepository.InteractionUserRow;
import com.wangzimin.now.repository.SocialMomentRepository.PostAttachmentRow;
import com.wangzimin.now.repository.SocialMomentRepository.PostRow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 管理朋友圈可见性、训练分享、图片或视频、点赞和评论。
 *
 * <p>帖子只向作者本人及好友展示。对于已经可见的帖子，互动预览直接读取所有用户，
 * 从而让查看者看到共同好友之外的点赞和评论；预览最多十条并返回剩余数量。</p>
 */
@Service
public class SocialMomentService {

    private final SocialMomentRepository repository;
    private final SocialConversationRepository attachmentRepository;
    private final SocialNotificationRepository notificationRepository;

    /**
     * 创建朋友圈服务。
     *
     * @param repository 朋友圈仓储
     * @param attachmentRepository 附件仓储
     * @param notificationRepository 朋友圈互动通知仓储
     */
    public SocialMomentService(SocialMomentRepository repository,
            SocialConversationRepository attachmentRepository,
            SocialNotificationRepository notificationRepository) {
        this.repository = repository;
        this.attachmentRepository = attachmentRepository;
        this.notificationRepository = notificationRepository;
    }

    /**
     * 查询当前用户可见的一页朋友圈。
     *
     * @param userId 当前用户主键
     * @param beforeId 分页上界
     * @param requestedLimit 客户端请求数量
     * @return 完整帖子视图
     */
    public List<PostView> feed(Long userId, Long beforeId, Integer requestedLimit) {
        long cursorId = beforeId == null || beforeId <= BusinessRule.ZERO_COUNT.longValue()
                ? BusinessRule.ZERO_COUNT.longValue() : beforeId;
        int limit = normalizeLimit(requestedLimit);
        return repository.listVisiblePosts(userId, cursorId, limit).stream()
                .map(post -> assemblePost(post, userId))
                .toList();
    }

    /**
     * 发布文字、图片或视频和可选训练摘要。
     *
     * @param userId 当前用户主键
     * @param request 发布内容
     * @return 新帖子完整视图
     */
    @Transactional
    public PostView createPost(Long userId, CreatePostRequest request) {
        String content = normalizeOptional(request.content());
        Set<Long> attachmentIds = uniqueAttachmentIds(request.attachmentIds());
        if (attachmentIds.size() > BusinessRule.SOCIAL_POST_MAX_IMAGE_COUNT.value()) {
            throw ApiErrorCode.POST_MEDIA_LIMIT.exception();
        }
        if (content == null && attachmentIds.isEmpty() && request.workoutSessionId() == null) {
            throw ApiErrorCode.POST_CONTENT_REQUIRED.exception();
        }
        if (request.workoutSessionId() != null
                && !repository.isShareableWorkout(userId, request.workoutSessionId())) {
            throw ApiErrorCode.WORKOUT_SHARE_INVALID.exception();
        }
        List<AttachmentRow> attachments = attachmentIds.stream()
                .map(attachmentId -> requireOwnedMedia(userId, attachmentId))
                .toList();
        validatePostMedia(attachments);
        long postId = repository.insertPost(userId, content, request.workoutSessionId());
        for (int index = 0; index < attachments.size(); index++) {
            repository.insertPostAttachment(postId, attachments.get(index).id(), index);
        }
        return assemblePost(repository.findPost(postId)
                .orElseThrow(ApiErrorCode.POST_NOT_FOUND::exception), userId);
    }

    /**
     * 软删除当前用户自己的朋友圈内容。
     *
     * @param userId 当前用户主键
     * @param postId 帖子主键
     */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        if (repository.deletePost(postId, userId) == 0) {
            throw ApiErrorCode.POST_FORBIDDEN.exception();
        }
        notificationRepository.deletePostNotifications(postId);
    }

    /**
     * 为当前用户可见的帖子点赞。
     *
     * @param userId 当前用户主键
     * @param postId 帖子主键
     * @return 更新后的帖子
     */
    @Transactional
    public PostView like(Long userId, Long postId) {
        PostRow post = requireVisiblePost(userId, postId);
        int inserted = repository.addLike(postId, userId);
        if (inserted > BusinessRule.ZERO_COUNT.value() && !userId.equals(post.authorUserId())) {
            notificationRepository.upsertLike(post.authorUserId(), userId, postId);
        }
        return assemblePost(post, userId);
    }

    /**
     * 取消当前用户对可见帖子的点赞。
     *
     * @param userId 当前用户主键
     * @param postId 帖子主键
     * @return 更新后的帖子
     */
    @Transactional
    public PostView unlike(Long userId, Long postId) {
        PostRow post = requireVisiblePost(userId, postId);
        repository.removeLike(postId, userId);
        if (!userId.equals(post.authorUserId())) {
            notificationRepository.deleteLike(post.authorUserId(), userId, postId);
        }
        return assemblePost(post, userId);
    }

    /**
     * 在当前用户可见的帖子下发表评论。
     *
     * @param userId 当前用户主键
     * @param postId 帖子主键
     * @param request 评论正文
     * @return 更新后的帖子
     */
    @Transactional
    public PostView comment(Long userId, Long postId, CreateCommentRequest request) {
        PostRow post = requireVisiblePost(userId, postId);
        long commentId = repository.insertComment(postId, userId, request.content().trim());
        if (!userId.equals(post.authorUserId())) {
            notificationRepository.insertComment(post.authorUserId(), userId, postId, commentId);
        }
        return assemblePost(post, userId);
    }

    /**
     * 删除当前用户自己的评论。
     *
     * @param userId 当前用户主键
     * @param postId 帖子主键
     * @param commentId 评论主键
     */
    @Transactional
    public void deleteComment(Long userId, Long postId, Long commentId) {
        requireVisiblePost(userId, postId);
        CommentRow comment = repository.findComment(commentId)
                .orElseThrow(ApiErrorCode.COMMENT_NOT_FOUND::exception);
        if (!postId.equals(comment.postId())) {
            throw ApiErrorCode.COMMENT_NOT_FOUND.exception();
        }
        if (repository.deleteComment(commentId, userId) == 0) {
            throw ApiErrorCode.COMMENT_FORBIDDEN.exception();
        }
        notificationRepository.deleteComment(commentId);
    }

    /**
     * 要求帖子对当前用户可见。
     *
     * @param userId 当前用户主键
     * @param postId 帖子主键
     * @return 帖子基础行
     */
    private PostRow requireVisiblePost(Long userId, Long postId) {
        if (!repository.canViewPost(userId, postId)) {
            throw ApiErrorCode.POST_NOT_FOUND.exception();
        }
        return repository.findPost(postId).orElseThrow(ApiErrorCode.POST_NOT_FOUND::exception);
    }

    /**
     * 校验朋友圈媒体归当前用户所有且属于图片或视频。
     *
     * @param userId 当前用户主键
     * @param attachmentId 附件主键
     * @return 已确认媒体附件
     */
    private AttachmentRow requireOwnedMedia(Long userId, Long attachmentId) {
        AttachmentRow attachment = attachmentRepository.findAttachment(attachmentId)
                .orElseThrow(ApiErrorCode.ATTACHMENT_NOT_FOUND::exception);
        if (!userId.equals(attachment.ownerUserId())) {
            throw ApiErrorCode.ATTACHMENT_FORBIDDEN.exception();
        }
        SocialAttachmentType type;
        try {
            type = SocialAttachmentType.valueOf(attachment.attachmentType());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw ApiErrorCode.ATTACHMENT_TYPE_MISMATCH.exception();
        }
        if (type == SocialAttachmentType.FILE) {
            throw ApiErrorCode.ATTACHMENT_TYPE_MISMATCH.exception();
        }
        return attachment;
    }

    /**
     * 校验朋友圈媒体数量和图视频互斥规则。
     *
     * <p>一条动态可以包含最多九张图片，或仅包含一个视频。禁止混合发布，
     * 使客户端布局、播放状态和用户选择结果保持明确。</p>
     *
     * @param attachments 已完成归属校验的媒体列表
     */
    private void validatePostMedia(List<AttachmentRow> attachments) {
        long imageCount = attachments.stream()
                .filter(attachment -> SocialAttachmentType.IMAGE.name()
                        .equals(attachment.attachmentType()))
                .count();
        long videoCount = attachments.stream()
                .filter(attachment -> SocialAttachmentType.VIDEO.name()
                        .equals(attachment.attachmentType()))
                .count();
        if (imageCount > BusinessRule.SOCIAL_POST_MAX_IMAGE_COUNT.longValue()) {
            throw ApiErrorCode.POST_MEDIA_LIMIT.exception();
        }
        if (videoCount > BusinessRule.SOCIAL_POST_MAX_VIDEO_COUNT.longValue()) {
            throw ApiErrorCode.POST_VIDEO_LIMIT.exception();
        }
        if (imageCount > BusinessRule.ZERO_COUNT.longValue()
                && videoCount > BusinessRule.ZERO_COUNT.longValue()) {
            throw ApiErrorCode.POST_MEDIA_MIXED.exception();
        }
    }

    /**
     * 组合帖子、媒体和受限互动预览。
     *
     * @param post 帖子基础行
     * @param userId 当前用户主键
     * @return 完整帖子视图
     */
    private PostView assemblePost(PostRow post, Long userId) {
        int previewLimit = BusinessRule.SOCIAL_INTERACTION_PREVIEW_LIMIT.value();
        List<InteractionUserRow> likes = repository.listLikes(post.id(), userId, previewLimit);
        List<CommentRow> comments = repository.listComments(post.id(), userId, previewLimit);
        int likeCount = repository.countLikes(post.id());
        int commentCount = repository.countComments(post.id());
        return new PostView(post, repository.listPostAttachments(post.id()), likes,
                Math.max(likeCount - likes.size(), BusinessRule.ZERO_COUNT.value()),
                repository.hasLiked(post.id(), userId), comments,
                Math.max(commentCount - comments.size(), BusinessRule.ZERO_COUNT.value()),
                userId.equals(post.authorUserId()));
    }

    /**
     * 将分页数量限制在统一范围。
     *
     * @param requestedLimit 客户端请求数量
     * @return 安全分页数量
     */
    private int normalizeLimit(Integer requestedLimit) {
        if (requestedLimit == null || requestedLimit < BusinessRule.COLLECTION_MIN_SIZE.value()) {
            return BusinessRule.SOCIAL_DEFAULT_PAGE_LIMIT.value();
        }
        return Math.min(requestedLimit, BusinessRule.SOCIAL_MAX_PAGE_LIMIT.value());
    }

    /**
     * 去除重复和空附件主键并保持原选择顺序。
     *
     * @param values 客户端附件主键
     * @return 唯一附件集合
     */
    private Set<Long> uniqueAttachmentIds(List<Long> values) {
        if (values == null) {
            return Set.of();
        }
        Set<Long> result = new LinkedHashSet<>();
        values.stream().filter(value -> value != null && value > BusinessRule.ZERO_COUNT.longValue())
                .forEach(result::add);
        return result;
    }

    /**
     * 清理可空正文，空白内容统一保存为 null。
     *
     * @param value 原始正文
     * @return 规范化正文
     */
    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /**
     * 描述发布朋友圈请求。
     *
     * @param content 可空正文
     * @param attachmentIds 最多九张图片或一个视频附件
     * @param workoutSessionId 可空的本人完成训练
     */
    public record CreatePostRequest(
            @Size(max = ValidationRule.SOCIAL_POST_MAX_LENGTH) String content,
            @Size(max = ValidationRule.SOCIAL_POST_MEDIA_MAX_COUNT) List<Long> attachmentIds,
            Long workoutSessionId) {
    }

    /**
     * 描述发表评论请求。
     *
     * @param content 非空评论正文
     */
    public record CreateCommentRequest(
            @NotBlank @Size(max = ValidationRule.SOCIAL_COMMENT_MAX_LENGTH) String content) {
    }

    /**
     * 描述朋友圈完整列表项。
     *
     * @param post 帖子及训练摘要
     * @param attachments 图片或视频列表
     * @param likes 最多十位点赞用户
     * @param hiddenLikeCount 未在预览中展示的点赞人数
     * @param likedByMe 当前用户是否点赞
     * @param comments 最多十条评论
     * @param hiddenCommentCount 未在预览中展示的评论数量
     * @param ownedByMe 是否由当前用户发布
     */
    public record PostView(PostRow post, List<PostAttachmentRow> attachments,
            List<InteractionUserRow> likes, int hiddenLikeCount, boolean likedByMe,
            List<CommentRow> comments, int hiddenCommentCount, boolean ownedByMe) {
    }
}

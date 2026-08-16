package com.wangzimin.now.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.wangzimin.now.domain.ApiPath;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.repository.SocialConversationRepository.ConversationRow;
import com.wangzimin.now.repository.SocialConversationRepository.MessageRow;
import com.wangzimin.now.repository.SocialFriendRepository.FriendRow;
import com.wangzimin.now.repository.SocialFriendRepository.FriendWorkoutRow;
import com.wangzimin.now.service.SocialConversationService;
import com.wangzimin.now.service.SocialConversationService.AddGroupMembersRequest;
import com.wangzimin.now.service.SocialConversationService.ConversationView;
import com.wangzimin.now.service.SocialConversationService.CreateGroupRequest;
import com.wangzimin.now.service.SocialConversationService.GroupView;
import com.wangzimin.now.service.SocialConversationService.MarkReadRequest;
import com.wangzimin.now.service.SocialConversationService.RenameGroupRequest;
import com.wangzimin.now.service.SocialConversationService.SendMessageRequest;
import com.wangzimin.now.service.SocialFileService;
import com.wangzimin.now.service.SocialFriendService;
import com.wangzimin.now.service.SocialFriendService.CreateFriendRequest;
import com.wangzimin.now.service.SocialFriendService.FriendRequestView;
import com.wangzimin.now.service.SocialFriendService.UpdateRemarkRequest;
import com.wangzimin.now.service.SocialFriendService.UserSearchResult;
import com.wangzimin.now.service.SocialMomentService;
import com.wangzimin.now.service.SocialMomentService.CreateCommentRequest;
import com.wangzimin.now.service.SocialMomentService.CreatePostRequest;
import com.wangzimin.now.service.SocialMomentService.PostView;
import com.wangzimin.now.service.SocialNotificationService;
import com.wangzimin.now.service.SocialNotificationService.UnreadSummary;
import com.wangzimin.now.repository.SocialNotificationRepository.NotificationRow;

import jakarta.validation.Valid;

/**
 * 暴露好友、聊天、群聊、附件和朋友圈的认证 API。
 *
 * <p>控制器只完成 HTTP 参数映射、JWT 用户识别和状态码选择；
 * 关系权限、状态机、文件校验及事务均由对应服务处理。</p>
 */
@RestController
@RequestMapping(ApiPath.SOCIAL_ROOT)
public class SocialController {

    private final SocialFriendService friendService;
    private final SocialConversationService conversationService;
    private final SocialMomentService momentService;
    private final SocialNotificationService notificationService;
    private final SocialFileService fileService;

    /**
     * 创建社交 API 控制器。
     *
     * @param friendService 好友服务
     * @param conversationService 会话服务
     * @param momentService 朋友圈服务
     * @param notificationService 社交未读与朋友圈通知服务
     * @param fileService 附件服务
     */
    public SocialController(SocialFriendService friendService,
            SocialConversationService conversationService, SocialMomentService momentService,
            SocialNotificationService notificationService, SocialFileService fileService) {
        this.friendService = friendService;
        this.conversationService = conversationService;
        this.momentService = momentService;
        this.notificationService = notificationService;
        this.fileService = fileService;
    }

    /**
     * 按公开 ID 搜索用户并返回关系状态。
     *
     * @param jwt 当前访问令牌
     * @param publicId 目标公开 ID
     * @return 搜索结果
     */
    @GetMapping(ApiPath.SOCIAL_USERS_SEGMENT)
    public UserSearchResult findUser(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId) {
        return friendService.findUser(userId(jwt), publicId);
    }

    /**
     * 发送好友申请。
     *
     * @param jwt 当前访问令牌
     * @param request 申请内容
     * @return 新申请视图
     */
    @PostMapping(ApiPath.FRIEND_REQUESTS_SEGMENT)
    @ResponseStatus(HttpStatus.CREATED)
    public FriendRequestView sendFriendRequest(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateFriendRequest request) {
        return friendService.sendRequest(userId(jwt), request);
    }

    /**
     * 查询收到和发出的好友申请。
     *
     * @param jwt 当前访问令牌
     * @return 申请列表
     */
    @GetMapping(ApiPath.FRIEND_REQUESTS_SEGMENT)
    public List<FriendRequestView> friendRequests(@AuthenticationPrincipal Jwt jwt) {
        return friendService.listRequests(userId(jwt));
    }

    /**
     * 接受一条好友申请。
     *
     * @param jwt 当前访问令牌
     * @param requestId 申请主键
     * @return 已接受申请
     */
    @PutMapping(ApiPath.FRIEND_REQUEST_ACCEPT_SEGMENT)
    public FriendRequestView acceptFriendRequest(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestId) {
        return friendService.acceptRequest(userId(jwt), requestId);
    }

    /**
     * 拒绝一条好友申请。
     *
     * @param jwt 当前访问令牌
     * @param requestId 申请主键
     */
    @PutMapping(ApiPath.FRIEND_REQUEST_REJECT_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void rejectFriendRequest(@AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId) {
        friendService.rejectRequest(userId(jwt), requestId);
    }

    /**
     * 取消当前用户发出的好友申请。
     *
     * @param jwt 当前访问令牌
     * @param requestId 申请主键
     */
    @DeleteMapping(ApiPath.FRIEND_REQUEST_CANCEL_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelFriendRequest(@AuthenticationPrincipal Jwt jwt, @PathVariable Long requestId) {
        friendService.cancelRequest(userId(jwt), requestId);
    }

    /**
     * 查询按名称或最近训练排序的好友列表。
     *
     * @param jwt 当前访问令牌
     * @param sort 排序值
     * @return 好友列表
     */
    @GetMapping(ApiPath.FRIENDS_SEGMENT)
    public List<FriendRow> friends(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String sort) {
        return friendService.listFriends(userId(jwt), sort);
    }

    /**
     * 更新一个好友备注。
     *
     * @param jwt 当前访问令牌
     * @param publicId 好友公开 ID
     * @param request 新备注
     */
    @PutMapping(ApiPath.FRIEND_REMARK_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateFriendRemark(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId,
            @Valid @RequestBody UpdateRemarkRequest request) {
        friendService.updateRemark(userId(jwt), publicId, request);
    }

    /**
     * 删除双方好友关系。
     *
     * @param jwt 当前访问令牌
     * @param publicId 好友公开 ID
     */
    @DeleteMapping(ApiPath.FRIEND_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFriend(@AuthenticationPrincipal Jwt jwt, @PathVariable String publicId) {
        friendService.deleteFriend(userId(jwt), publicId);
    }

    /**
     * 查询好友近期训练摘要。
     *
     * @param jwt 当前访问令牌
     * @param publicId 好友公开 ID
     * @return 训练摘要
     */
    @GetMapping(ApiPath.FRIEND_WORKOUTS_SEGMENT)
    public List<FriendWorkoutRow> friendWorkouts(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String publicId) {
        return friendService.friendWorkouts(userId(jwt), publicId);
    }

    /**
     * 查询当前用户的消息会话列表。
     *
     * @param jwt 当前访问令牌
     * @return 会话概览
     */
    @GetMapping(ApiPath.CONVERSATIONS_SEGMENT)
    public List<ConversationRow> conversations(@AuthenticationPrincipal Jwt jwt) {
        return conversationService.conversations(userId(jwt));
    }

    /**
     * 获取或创建与好友的私聊。
     *
     * @param jwt 当前访问令牌
     * @param publicId 好友公开 ID
     * @return 私聊资料
     */
    @PostMapping(ApiPath.DIRECT_CONVERSATION_SEGMENT)
    public ConversationView directConversation(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String publicId) {
        return conversationService.directConversation(userId(jwt), publicId);
    }

    /**
     * 查询历史消息或轮询增量消息。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 会话主键
     * @param beforeId 历史分页上界
     * @param afterId 增量轮询下界
     * @param limit 最大数量
     * @return 消息列表
     */
    @GetMapping(ApiPath.CONVERSATION_MESSAGES_SEGMENT)
    public List<MessageRow> messages(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId, @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Long afterId,
            @RequestParam(required = false) Integer limit) {
        return conversationService.messages(userId(jwt), conversationId, beforeId, afterId, limit);
    }

    /**
     * 发送一条聊天消息。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 会话主键
     * @param request 消息请求
     * @return 已保存消息
     */
    @PostMapping(ApiPath.CONVERSATION_MESSAGES_SEGMENT)
    @ResponseStatus(HttpStatus.CREATED)
    public MessageRow sendMessage(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId, @Valid @RequestBody SendMessageRequest request) {
        return conversationService.sendMessage(userId(jwt), conversationId, request);
    }

    /**
     * 更新会话已读位置。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 会话主键
     * @param request 最后已读消息
     */
    @PutMapping(ApiPath.CONVERSATION_READ_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId,
            @Valid @RequestBody MarkReadRequest request) {
        conversationService.markRead(userId(jwt), conversationId, request);
    }

    /**
     * 上传图片、视频或普通文件并返回附件主键。
     *
     * @param jwt 当前访问令牌
     * @param file 上传文件
     * @return 附件元数据
     */
    @PostMapping(ApiPath.ATTACHMENTS_SEGMENT)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentRow upload(@AuthenticationPrincipal Jwt jwt,
            @RequestPart("file") MultipartFile file) {
        return fileService.store(userId(jwt), file);
    }

    /**
     * 创建群聊。
     *
     * @param jwt 当前访问令牌
     * @param request 群聊名称和成员
     * @return 群聊详情
     */
    @PostMapping(ApiPath.GROUPS_SEGMENT)
    @ResponseStatus(HttpStatus.CREATED)
    public GroupView createGroup(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateGroupRequest request) {
        return conversationService.createGroup(userId(jwt), request);
    }

    /**
     * 查询群聊详情。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 群聊主键
     * @return 群聊详情
     */
    @GetMapping(ApiPath.GROUP_SEGMENT)
    public GroupView group(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        return conversationService.group(userId(jwt), conversationId);
    }

    /**
     * 修改群聊名称。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 群聊主键
     * @param request 新群名
     */
    @PutMapping(ApiPath.GROUP_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renameGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId,
            @Valid @RequestBody RenameGroupRequest request) {
        conversationService.renameGroup(userId(jwt), conversationId, request);
    }

    /**
     * 邀请好友加入群聊。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 群聊主键
     * @param request 好友公开 ID
     */
    @PostMapping(ApiPath.GROUP_MEMBERS_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addGroupMembers(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId,
            @Valid @RequestBody AddGroupMembersRequest request) {
        conversationService.addGroupMembers(userId(jwt), conversationId, request);
    }

    /**
     * 将一个普通成员移出群聊。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 群聊主键
     * @param publicId 成员公开 ID
     */
    @DeleteMapping(ApiPath.GROUP_MEMBER_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeGroupMember(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long conversationId, @PathVariable String publicId) {
        conversationService.removeGroupMember(userId(jwt), conversationId, publicId);
    }

    /**
     * 当前用户退出群聊。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 群聊主键
     */
    @PostMapping(ApiPath.GROUP_LEAVE_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leaveGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        conversationService.leaveGroup(userId(jwt), conversationId);
    }

    /**
     * 群主解散群聊。
     *
     * @param jwt 当前访问令牌
     * @param conversationId 群聊主键
     */
    @DeleteMapping(ApiPath.GROUP_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dissolveGroup(@AuthenticationPrincipal Jwt jwt, @PathVariable Long conversationId) {
        conversationService.dissolveGroup(userId(jwt), conversationId);
    }

    /**
     * 查询朋友圈时间线。
     *
     * @param jwt 当前访问令牌
     * @param beforeId 分页上界
     * @param limit 最大数量
     * @return 朋友圈内容
     */
    @GetMapping(ApiPath.MOMENTS_SEGMENT)
    public List<PostView> moments(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(required = false) Integer limit) {
        return momentService.feed(userId(jwt), beforeId, limit);
    }

    /**
     * 发布朋友圈或分享已完成训练。
     *
     * @param jwt 当前访问令牌
     * @param request 发布内容
     * @return 新帖子
     */
    @PostMapping(ApiPath.MOMENTS_SEGMENT)
    @ResponseStatus(HttpStatus.CREATED)
    public PostView createMoment(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreatePostRequest request) {
        return momentService.createPost(userId(jwt), request);
    }

    /**
     * 删除当前用户自己的朋友圈内容。
     *
     * @param jwt 当前访问令牌
     * @param postId 帖子主键
     */
    @DeleteMapping(ApiPath.MOMENT_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMoment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId) {
        momentService.deletePost(userId(jwt), postId);
    }

    /**
     * 点赞一条可见朋友圈。
     *
     * @param jwt 当前访问令牌
     * @param postId 帖子主键
     * @return 更新后帖子
     */
    @PutMapping(ApiPath.MOMENT_LIKE_SEGMENT)
    public PostView likeMoment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId) {
        return momentService.like(userId(jwt), postId);
    }

    /**
     * 取消朋友圈点赞。
     *
     * @param jwt 当前访问令牌
     * @param postId 帖子主键
     * @return 更新后帖子
     */
    @DeleteMapping(ApiPath.MOMENT_LIKE_SEGMENT)
    public PostView unlikeMoment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId) {
        return momentService.unlike(userId(jwt), postId);
    }

    /**
     * 在朋友圈下发表评论。
     *
     * @param jwt 当前访问令牌
     * @param postId 帖子主键
     * @param request 评论正文
     * @return 更新后帖子
     */
    @PostMapping(ApiPath.MOMENT_COMMENTS_SEGMENT)
    public PostView commentMoment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request) {
        return momentService.comment(userId(jwt), postId, request);
    }

    /**
     * 删除当前用户自己的朋友圈评论。
     *
     * @param jwt 当前访问令牌
     * @param postId 帖子主键
     * @param commentId 评论主键
     */
    @DeleteMapping(ApiPath.MOMENT_COMMENT_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMomentComment(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId,
            @PathVariable Long commentId) {
        momentService.deleteComment(userId(jwt), postId, commentId);
    }

    /**
     * 汇总聊天、好友申请和朋友圈互动未读数。
     *
     * @param jwt 当前访问令牌
     * @return 社交菜单徽标数据
     */
    @GetMapping(ApiPath.SOCIAL_UNREAD_SUMMARY_SEGMENT)
    public UnreadSummary unreadSummary(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.unreadSummary(userId(jwt));
    }

    /**
     * 查询当前用户最近的朋友圈点赞与评论消息。
     *
     * @param jwt 当前访问令牌
     * @return 互动通知列表
     */
    @GetMapping(ApiPath.MOMENT_NOTIFICATIONS_SEGMENT)
    public List<NotificationRow> momentNotifications(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.notifications(userId(jwt));
    }

    /**
     * 将全部朋友圈互动通知标记为已读。
     *
     * @param jwt 当前访问令牌
     */
    @PutMapping(ApiPath.MOMENT_NOTIFICATIONS_READ_SEGMENT)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markMomentNotificationsRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markMomentNotificationsRead(userId(jwt));
    }

    /**
     * 从已验证 JWT 提取当前用户主键。
     *
     * @param jwt 访问令牌
     * @return 用户主键
     */
    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

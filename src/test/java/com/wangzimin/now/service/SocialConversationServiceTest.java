package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.wangzimin.now.domain.SocialConversationType;
import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.domain.SocialMemberRole;
import com.wangzimin.now.domain.SocialMessageType;
import com.wangzimin.now.domain.SystemText;
import com.wangzimin.now.repository.SocialConversationRepository;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.repository.SocialConversationRepository.ConversationAccessRow;
import com.wangzimin.now.repository.SocialConversationRepository.ConversationMemberRow;
import com.wangzimin.now.repository.SocialConversationRepository.MessageRow;
import com.wangzimin.now.repository.SocialFriendRepository.SocialUserRow;

class SocialConversationServiceTest {

    @Test
    void directConversationHistoryRemainsReadableButSendingRequiresCurrentFriendship() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        ConversationAccessRow access = new ConversationAccessRow(8L,
                SocialConversationType.DIRECT.name(), null, null,
                SocialMemberRole.MEMBER.name(), 2L);
        when(repository.findAccess(8L, 1L)).thenReturn(Optional.of(access));
        when(friendService.areFriends(1L, 2L)).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.sendMessage(1L, 8L,
                        new SocialConversationService.SendMessageRequest(
                                SocialMessageType.TEXT.name(), "继续训练", null)));

        assertEquals(404, exception.getStatusCode().value());
        verify(repository, never()).insertMessage(8L, 1L, SocialMessageType.TEXT, "继续训练", null);
    }

    /** 验证视频消息必须引用归当前用户所有的视频附件。 */
    @Test
    void videoMessageUsesVideoAttachmentType() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        ConversationAccessRow access = new ConversationAccessRow(8L,
                SocialConversationType.DIRECT.name(), null, null,
                SocialMemberRole.MEMBER.name(), 2L);
        AttachmentRow video = new AttachmentRow(12L, 1L, SocialAttachmentType.VIDEO.name(),
                "training.mp4", "stored-video", "video/mp4", 1024L,
                "/api/v1/social/files/stored-video",
                "/api/v1/social/files/stored-video/poster", LocalDateTime.now());
        MessageRow message = new MessageRow(19L, 8L, 1L, "N000000001", "训练者", null,
                SocialMessageType.VIDEO.name(), null, 12L, "training.mp4", "video/mp4",
                1024L, "/api/v1/social/files/stored-video",
                "/api/v1/social/files/stored-video/poster", LocalDateTime.now());
        when(repository.findAccess(8L, 1L)).thenReturn(Optional.of(access));
        when(friendService.areFriends(1L, 2L)).thenReturn(true);
        when(repository.findAttachment(12L)).thenReturn(Optional.of(video));
        when(repository.insertMessage(8L, 1L, SocialMessageType.VIDEO, null, 12L))
                .thenReturn(19L);
        when(repository.findMessage(19L)).thenReturn(Optional.of(message));

        MessageRow result = service.sendMessage(1L, 8L,
                new SocialConversationService.SendMessageRequest(
                        SocialMessageType.VIDEO.name(), null, 12L));

        assertEquals(SocialMessageType.VIDEO.name(), result.messageType());
        verify(repository).insertMessage(8L, 1L, SocialMessageType.VIDEO, null, 12L);
    }

    /** 验证创建群聊会一次性保存成员、创建事件并返回完整群资料。 */
    @Test
    void createGroupPersistsMembersAndCreationEvent() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        SocialUserRow friend = new SocialUserRow(2L, "N000000002", "friend", "训练搭档", null);
        ConversationAccessRow access = groupAccess(21L, 1L, SocialMemberRole.OWNER, 1L);
        List<ConversationMemberRow> members = List.of(
                groupMember(1L, "N000000001", "群主", SocialMemberRole.OWNER),
                groupMember(2L, "N000000002", "训练搭档", SocialMemberRole.MEMBER));
        when(friendService.requireFriend(1L, "N000000002")).thenReturn(friend);
        when(repository.insertGroupConversation(1L, "周末训练")).thenReturn(21L);
        when(repository.findAccess(21L, 1L)).thenReturn(Optional.of(access));
        when(repository.listMembers(21L)).thenReturn(members);

        SocialConversationService.GroupView result = service.createGroup(1L,
                new SocialConversationService.CreateGroupRequest(
                        " 周末训练 ", List.of("n000000002")));

        assertEquals(21L, result.id());
        assertEquals(2, result.members().size());
        verify(repository).upsertMember(21L, 1L, SocialMemberRole.OWNER);
        verify(repository).upsertMember(21L, 2L, SocialMemberRole.MEMBER);
        verify(repository).insertMessage(21L, 1L, SocialMessageType.SYSTEM,
                SystemText.SYSTEM_GROUP_CREATED.value(), null);
        verify(repository).touchConversation(21L);
    }

    /** 验证群主改名后群消息流会出现新的群名。 */
    @Test
    void renameGroupPublishesSystemEvent() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        when(repository.findAccess(8L, 1L))
                .thenReturn(Optional.of(groupAccess(8L, 1L, SocialMemberRole.OWNER, 1L)));

        service.renameGroup(1L, 8L,
                new SocialConversationService.RenameGroupRequest(" 晨练小组 "));

        verify(repository).renameGroup(8L, "晨练小组");
        verify(repository).insertMessage(8L, 1L, SocialMessageType.SYSTEM,
                SystemText.SYSTEM_GROUP_RENAMED_PREFIX.value() + "晨练小组", null);
        verify(repository).touchConversation(8L);
    }

    /** 验证邀请成员只读取一次成员快照，并在全部校验后写入入群事件。 */
    @Test
    void addGroupMembersValidatesSnapshotBeforeWriting() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        SocialUserRow friend = new SocialUserRow(3L, "N000000003", "new_friend", "新伙伴", null);
        when(repository.findAccess(8L, 1L))
                .thenReturn(Optional.of(groupAccess(8L, 1L, SocialMemberRole.OWNER, 1L)));
        when(repository.listMembers(8L)).thenReturn(List.of(
                groupMember(1L, "N000000001", "群主", SocialMemberRole.OWNER),
                groupMember(2L, "N000000002", "成员", SocialMemberRole.MEMBER)));
        when(friendService.requireFriend(1L, "N000000003")).thenReturn(friend);

        service.addGroupMembers(1L, 8L,
                new SocialConversationService.AddGroupMembersRequest(List.of("n000000003")));

        verify(repository, times(1)).listMembers(8L);
        verify(repository).upsertMember(8L, 3L, SocialMemberRole.MEMBER);
        verify(repository).insertMessage(8L, 1L, SocialMessageType.SYSTEM,
                "新伙伴" + SystemText.SYSTEM_MEMBER_JOINED.value(), null);
    }

    /** 验证已在群内的用户会在任何成员写入前被拒绝。 */
    @Test
    void addGroupMembersRejectsExistingMemberBeforeWriting() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        SocialUserRow existing = new SocialUserRow(2L, "N000000002", "member", "已有成员", null);
        when(repository.findAccess(8L, 1L))
                .thenReturn(Optional.of(groupAccess(8L, 1L, SocialMemberRole.OWNER, 1L)));
        when(repository.listMembers(8L)).thenReturn(List.of(
                groupMember(1L, "N000000001", "群主", SocialMemberRole.OWNER),
                groupMember(2L, "N000000002", "已有成员", SocialMemberRole.MEMBER)));
        when(friendService.requireFriend(1L, "N000000002")).thenReturn(existing);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.addGroupMembers(1L, 8L,
                        new SocialConversationService.AddGroupMembersRequest(
                                List.of("N000000002"))));

        assertEquals(409, exception.getStatusCode().value());
        verify(repository, never()).upsertMember(8L, 2L, SocialMemberRole.MEMBER);
        verify(repository, never()).insertMessage(8L, 1L, SocialMessageType.SYSTEM,
                "已有成员" + SystemText.SYSTEM_MEMBER_JOINED.value(), null);
    }

    /** 验证群主移出普通成员后会保留可见的成员变更事件。 */
    @Test
    void removeGroupMemberPublishesSystemEvent() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        SocialUserRow member = new SocialUserRow(2L, "N000000002", "member", "训练搭档", null);
        when(repository.findAccess(8L, 1L))
                .thenReturn(Optional.of(groupAccess(8L, 1L, SocialMemberRole.OWNER, 1L)));
        when(friendService.requireUser("N000000002")).thenReturn(member);
        when(repository.leaveMember(8L, 2L)).thenReturn(1);

        service.removeGroupMember(1L, 8L, "N000000002");

        verify(repository).leaveMember(8L, 2L);
        verify(repository).insertMessage(8L, 1L, SocialMessageType.SYSTEM,
                "训练搭档" + SystemText.SYSTEM_MEMBER_REMOVED.value(), null);
    }

    /** 验证普通成员可退出群聊，并让其他成员看到退出事件。 */
    @Test
    void memberCanLeaveGroupWithSystemEvent() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        SocialUserRow member = new SocialUserRow(2L, "N000000002", "member", "训练搭档", null);
        when(repository.findAccess(8L, 2L))
                .thenReturn(Optional.of(groupAccess(8L, 1L, SocialMemberRole.MEMBER, 2L)));
        when(repository.listMembers(8L)).thenReturn(List.of(
                groupMember(1L, "N000000001", "群主", SocialMemberRole.OWNER),
                groupMember(2L, "N000000002", "训练搭档", SocialMemberRole.MEMBER)));
        when(friendService.requireUser("N000000002")).thenReturn(member);

        service.leaveGroup(2L, 8L);

        verify(repository).insertMessage(8L, 2L, SocialMessageType.SYSTEM,
                "训练搭档" + SystemText.SYSTEM_MEMBER_LEFT.value(), null);
        verify(repository).leaveMember(8L, 2L);
    }

    /** 验证群主可以解散群聊并关闭所有成员访问。 */
    @Test
    void ownerCanDissolveGroup() {
        SocialConversationRepository repository = mock(SocialConversationRepository.class);
        SocialFriendService friendService = mock(SocialFriendService.class);
        SocialConversationService service = new SocialConversationService(repository, friendService);
        when(repository.findAccess(8L, 1L))
                .thenReturn(Optional.of(groupAccess(8L, 1L, SocialMemberRole.OWNER, 1L)));

        service.dissolveGroup(1L, 8L);

        verify(repository).dissolveGroup(8L);
    }

    /** 构造指定角色可见的群聊访问快照。 */
    private ConversationAccessRow groupAccess(Long conversationId, Long ownerUserId,
            SocialMemberRole role, Long currentUserId) {
        return new ConversationAccessRow(conversationId, SocialConversationType.GROUP.name(),
                "训练群", ownerUserId, role.name(), currentUserId);
    }

    /** 构造群成员列表行，供生命周期测试复用。 */
    private ConversationMemberRow groupMember(Long userId, String publicId, String displayName,
            SocialMemberRole role) {
        return new ConversationMemberRow(userId, publicId, displayName, null,
                role.name(), false, LocalDateTime.now());
    }
}

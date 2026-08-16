package com.wangzimin.now.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.wangzimin.now.domain.SocialAttachmentType;
import com.wangzimin.now.repository.AuthRepository;
import com.wangzimin.now.repository.AuthRepository.UserProfileRow;
import com.wangzimin.now.repository.SocialConversationRepository.AttachmentRow;
import com.wangzimin.now.service.SocialFileService;

class UserProfileServiceTest {

    @Test
    void avatarUpdateReturnsFreshProfileAndPersistsPublicUrl() {
        AuthRepository authRepository = mock(AuthRepository.class);
        SocialFileService fileService = mock(SocialFileService.class);
        UserProfileService service = new UserProfileService(authRepository, fileService);
        MockMultipartFile image = new MockMultipartFile(
                "file", "profile.jpg", "image/jpeg", new byte[] { 4, 5, 6 });
        String avatarUrl = "/api/v1/social/files/avatar-storage-key";
        AttachmentRow attachment = new AttachmentRow(18L, 5L,
                SocialAttachmentType.IMAGE.name(), "profile.jpg", "avatar-storage-key",
                "image/jpeg", 3L, avatarUrl, null, null);
        UserProfileRow row = new UserProfileRow(5L, "N000000005", "tester", "训练者", avatarUrl);
        when(fileService.storeAvatar(5L, image)).thenReturn(attachment);
        when(authRepository.findEnabledUserProfile(5L)).thenReturn(Optional.of(row));

        AuthService.UserProfile result = service.updateAvatar(5L, image);

        verify(authRepository).updateAvatar(5L, avatarUrl);
        assertEquals(avatarUrl, result.avatarUrl());
        assertEquals("N000000005", result.publicId());
    }
}

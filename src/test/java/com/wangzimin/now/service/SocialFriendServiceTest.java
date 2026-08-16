package com.wangzimin.now.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.wangzimin.now.domain.FriendRequestStatus;
import com.wangzimin.now.repository.SocialFriendRepository;
import com.wangzimin.now.repository.SocialFriendRepository.FriendRequestRow;

class SocialFriendServiceTest {

    @Test
    void acceptingRequestCreatesBothFriendshipDirectionsInOneServiceOperation() {
        SocialFriendRepository repository = mock(SocialFriendRepository.class);
        SocialFriendService service = new SocialFriendService(repository);
        FriendRequestRow pending = request(FriendRequestStatus.PENDING);
        FriendRequestRow accepted = request(FriendRequestStatus.ACCEPTED);
        when(repository.findRequestById(10L)).thenReturn(Optional.of(pending), Optional.of(accepted));
        when(repository.updateRequestStatus(10L, FriendRequestStatus.ACCEPTED)).thenReturn(1);

        SocialFriendService.FriendRequestView result = service.acceptRequest(2L, 10L);

        assertEquals(FriendRequestStatus.ACCEPTED.name(), result.status());
        verify(repository).insertFriendship(1L, 2L);
        verify(repository).insertFriendship(2L, 1L);
        verify(repository).updateRequestStatus(10L, FriendRequestStatus.ACCEPTED);
    }

    private FriendRequestRow request(FriendRequestStatus status) {
        LocalDateTime now = LocalDateTime.now();
        return new FriendRequestRow(10L, 1L, "NUSER0001", "发起者", null,
                2L, "NUSER0002", "接收者", null, "一起训练", status.name(), now, now);
    }
}

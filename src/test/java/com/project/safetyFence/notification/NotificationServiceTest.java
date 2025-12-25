package com.project.safetyFence.notification;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.project.safetyFence.link.domain.Link;
import com.project.safetyFence.notification.domain.DeviceToken;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("알림 서비스 테스트")
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User elderUser;
    private User supporterUser;
    private DeviceToken deviceToken;

    @BeforeEach
    void setUp() {
        elderUser = new User("010-1111-1111", "어르신", "password", LocalDate.of(1950, 1, 1), "ELDER123");
        supporterUser = new User("010-2222-2222", "보호자", "password", LocalDate.of(1980, 1, 1), "SUPPORTER123");
        deviceToken = new DeviceToken(supporterUser, "test-fcm-token", "android");
    }

    @Test
    @DisplayName("보호자에게 알림 전송 성공")
    void sendNotificationToSupporters_shouldSendNotification() {
        // given
        Link link = new Link(elderUser, supporterUser.getNumber(), "보호자");
        elderUser.getLinks().add(link);

        when(userRepository.findByNumberWithLinks(elderUser.getNumber())).thenReturn(elderUser);
        when(userRepository.findByNumber(supporterUser.getNumber())).thenReturn(supporterUser);
        when(deviceTokenRepository.findByUser(supporterUser)).thenReturn(List.of(deviceToken));

        try (MockedStatic<FirebaseMessaging> firebaseMessagingMock = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            firebaseMessagingMock.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id-123");

            // when
            notificationService.sendNotificationToSupporters(elderUser, "테스트 제목", "테스트 내용");

            // then
            verify(userRepository).findByNumberWithLinks(elderUser.getNumber());
            verify(userRepository).findByNumber(supporterUser.getNumber());
            verify(deviceTokenRepository).findByUser(supporterUser);
            verify(firebaseMessaging).send(any(Message.class));
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("보호자가 없으면 알림 전송하지 않음")
    void sendNotificationToSupporters_whenNoSupporters_shouldNotSend() {
        // given
        when(userRepository.findByNumberWithLinks(elderUser.getNumber())).thenReturn(elderUser);

        try (MockedStatic<FirebaseMessaging> firebaseMessagingMock = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            firebaseMessagingMock.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            // when
            notificationService.sendNotificationToSupporters(elderUser, "테스트 제목", "테스트 내용");

            // then
            verify(userRepository).findByNumberWithLinks(elderUser.getNumber());
            verify(firebaseMessaging, never()).send(any(Message.class));
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("디바이스 토큰이 없으면 알림 전송하지 않음")
    void sendNotificationToSupporters_whenNoDeviceToken_shouldNotSend() {
        // given
        Link link = new Link(elderUser, supporterUser.getNumber(), "보호자");
        elderUser.getLinks().add(link);

        when(userRepository.findByNumberWithLinks(elderUser.getNumber())).thenReturn(elderUser);
        when(userRepository.findByNumber(supporterUser.getNumber())).thenReturn(supporterUser);
        when(deviceTokenRepository.findByUser(supporterUser)).thenReturn(new ArrayList<>());

        try (MockedStatic<FirebaseMessaging> firebaseMessagingMock = mockStatic(FirebaseMessaging.class)) {
            FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
            firebaseMessagingMock.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            // when
            notificationService.sendNotificationToSupporters(elderUser, "테스트 제목", "테스트 내용");

            // then
            verify(firebaseMessaging, never()).send(any(Message.class));
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("디바이스 토큰 저장 성공")
    void saveOrUpdateToken_shouldSaveToken() {
        // given
        when(userRepository.findByNumber(supporterUser.getNumber())).thenReturn(supporterUser);
        when(deviceTokenRepository.findByToken(anyString())).thenReturn(Optional.empty());
        when(deviceTokenRepository.save(any(DeviceToken.class))).thenReturn(deviceToken);

        // when
        notificationService.saveOrUpdateToken(supporterUser.getNumber(), "new-fcm-token", "android");

        // then
        verify(userRepository).findByNumber(supporterUser.getNumber());
        verify(deviceTokenRepository).findByToken("new-fcm-token");
        verify(deviceTokenRepository).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("존재하는 디바이스 토큰 업데이트")
    void saveOrUpdateToken_whenTokenExists_shouldUpdate() {
        // given
        when(userRepository.findByNumber(supporterUser.getNumber())).thenReturn(supporterUser);
        when(deviceTokenRepository.findByToken("existing-token")).thenReturn(Optional.of(deviceToken));
        when(deviceTokenRepository.save(any(DeviceToken.class))).thenReturn(deviceToken);

        // when
        notificationService.saveOrUpdateToken(supporterUser.getNumber(), "existing-token", "android");

        // then
        verify(userRepository).findByNumber(supporterUser.getNumber());
        verify(deviceTokenRepository).findByToken("existing-token");
        verify(deviceTokenRepository).save(deviceToken);
    }

    @Test
    @DisplayName("사용자가 없으면 토큰 저장 실패")
    void saveOrUpdateToken_whenUserNotFound_shouldThrowException() {
        // given
        when(userRepository.findByNumber(anyString())).thenReturn(null);

        // when & then
        assertThatThrownBy(() -> notificationService.saveOrUpdateToken("invalid-number", "token", "android"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

        verify(userRepository).findByNumber("invalid-number");
        verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
    }

    @Test
    @DisplayName("디바이스 토큰 삭제 성공")
    void deleteToken_shouldDeleteToken() {
        // given
        String tokenToDelete = "token-to-delete";

        // when
        notificationService.deleteToken(tokenToDelete);

        // then
        verify(deviceTokenRepository).deleteByToken(tokenToDelete);
    }
}

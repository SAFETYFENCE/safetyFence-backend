package com.project.safetyFence.notification;

import com.project.safetyFence.notification.dto.DeviceTokenRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("디바이스 토큰 컨트롤러 테스트")
@ExtendWith(MockitoExtension.class)
class DeviceTokenControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DeviceTokenController deviceTokenController;

    @Test
    @DisplayName("디바이스 토큰 등록 성공")
    void registerToken_shouldReturnOk() {
        // given
        DeviceTokenRequestDto request = new DeviceTokenRequestDto("010-1234-5678", "test-fcm-token", "android");
        doNothing().when(notificationService).saveOrUpdateToken(anyString(), anyString(), anyString());

        // when
        ResponseEntity<String> response = deviceTokenController.registerToken(request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("토큰 등록 성공");
        verify(notificationService).saveOrUpdateToken("010-1234-5678", "test-fcm-token", "android");
    }

    @Test
    @DisplayName("디바이스 토큰 등록 실패 - 서비스 예외")
    void registerToken_whenServiceThrowsException_shouldReturnBadRequest() {
        // given
        DeviceTokenRequestDto request = new DeviceTokenRequestDto("invalid-number", "test-token", "android");
        doThrow(new IllegalArgumentException("사용자를 찾을 수 없습니다"))
                .when(notificationService).saveOrUpdateToken(anyString(), anyString(), anyString());

        // when
        ResponseEntity<String> response = deviceTokenController.registerToken(request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("토큰 등록 실패");
        verify(notificationService).saveOrUpdateToken("invalid-number", "test-token", "android");
    }

    @Test
    @DisplayName("디바이스 토큰 삭제 성공")
    void deleteToken_shouldReturnOk() {
        // given
        String token = "token-to-delete";
        doNothing().when(notificationService).deleteToken(token);

        // when
        ResponseEntity<String> response = deviceTokenController.deleteToken(token);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("토큰 삭제 성공");
        verify(notificationService).deleteToken(token);
    }

    @Test
    @DisplayName("디바이스 토큰 삭제 실패 - 서비스 예외")
    void deleteToken_whenServiceThrowsException_shouldReturnBadRequest() {
        // given
        String token = "invalid-token";
        doThrow(new RuntimeException("삭제 실패"))
                .when(notificationService).deleteToken(token);

        // when
        ResponseEntity<String> response = deviceTokenController.deleteToken(token);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).contains("토큰 삭제 실패");
        verify(notificationService).deleteToken(token);
    }
}

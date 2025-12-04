package com.project.safetyFence.notification;

import com.project.safetyFence.notification.dto.DeviceTokenRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/device-token")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final NotificationService notificationService;

    /**
     * 디바이스 토큰 저장 또는 업데이트
     * POST /api/device-token/register
     */
    @PostMapping("/register")
    public ResponseEntity<String> registerToken(@RequestBody DeviceTokenRequestDto request) {
        try {
            notificationService.saveOrUpdateToken(
                    request.getUserNumber(),
                    request.getToken(),
                    request.getDeviceType()
            );

            log.info("✅ 디바이스 토큰 등록 성공: userNumber={}", request.getUserNumber());
            return ResponseEntity.ok("토큰 등록 성공");

        } catch (Exception e) {
            log.error("❌ 디바이스 토큰 등록 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body("토큰 등록 실패: " + e.getMessage());
        }
    }

    /**
     * 디바이스 토큰 삭제 (로그아웃 시)
     * DELETE /api/device-token
     */
    @DeleteMapping
    public ResponseEntity<String> deleteToken(@RequestParam String token) {
        try {
            notificationService.deleteToken(token);

            log.info("✅ 디바이스 토큰 삭제 성공");
            return ResponseEntity.ok("토큰 삭제 성공");

        } catch (Exception e) {
            log.error("❌ 디바이스 토큰 삭제 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body("토큰 삭제 실패: " + e.getMessage());
        }
    }
}
package com.project.safetyFence.notification;

import com.project.safetyFence.notification.dto.EmergencyRequestDto;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
public class EmergencyController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    /**
     * 긴급 알림 전송
     * POST /notification/emergency
     */
    @PostMapping("/emergency")
    public ResponseEntity<String> sendEmergencyAlert(@RequestBody EmergencyRequestDto request) {
        try {
            String userNumber = request.getUserNumber();

            if (userNumber == null || userNumber.trim().isEmpty()) {
                log.warn("⚠️ 긴급 알림 요청: userNumber가 비어있음");
                return ResponseEntity.badRequest().body("사용자 번호가 필요합니다.");
            }

            // 사용자 조회
            User user = userRepository.findByNumber(userNumber);
            if (user == null) {
                log.warn("⚠️ 긴급 알림 요청: 사용자를 찾을 수 없음 - userNumber={}", userNumber);
                return ResponseEntity.badRequest().body("사용자를 찾을 수 없습니다.");
            }

            log.info("🚨 긴급 알림 요청 수신: userNumber={}, userName={}", userNumber, user.getName());

            // 긴급 알림 전송
            notificationService.sendEmergencyAlert(user);

            log.info("✅ 긴급 알림 전송 완료: userNumber={}", userNumber);
            return ResponseEntity.ok("긴급 알림이 전송되었습니다.");

        } catch (Exception e) {
            log.error("❌ 긴급 알림 전송 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("긴급 알림 전송에 실패했습니다.");
        }
    }
}

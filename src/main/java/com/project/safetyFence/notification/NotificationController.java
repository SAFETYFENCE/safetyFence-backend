package com.project.safetyFence.notification;

import com.project.safetyFence.notification.dto.EventNotificationRequestDto;
import com.project.safetyFence.notification.dto.MedicationNotificationRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping("/medication-added")
    public ResponseEntity<String> sendMedicationNotification(
            @RequestBody MedicationNotificationRequestDto request,
            HttpServletRequest httpRequest) {

        String supporterNumber = (String) httpRequest.getAttribute("userNumber");

        try {
            notificationService.sendMedicationAddedNotification(
                supporterNumber,
                request.getTargetUserNumber(),
                request.getMedicationName()
            );

            return ResponseEntity.ok("Notification sent successfully");

        } catch (IllegalArgumentException e) {
            log.error("알림 전송 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/event-added")
    public ResponseEntity<String> sendEventNotification(
            @RequestBody EventNotificationRequestDto request,
            HttpServletRequest httpRequest) {

        String supporterNumber = (String) httpRequest.getAttribute("userNumber");

        try {
            notificationService.sendEventAddedNotification(
                supporterNumber,
                request.getTargetUserNumber(),
                request.getEventTitle(),
                request.getEventDate(),
                request.getEventTime()
            );

            return ResponseEntity.ok("Notification sent successfully");

        } catch (IllegalArgumentException e) {
            log.error("알림 전송 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}

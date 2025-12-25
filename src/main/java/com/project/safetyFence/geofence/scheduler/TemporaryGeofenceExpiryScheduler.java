package com.project.safetyFence.geofence.scheduler;

import com.project.safetyFence.geofence.GeofenceRepository;
import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.log.domain.Log;
import com.project.safetyFence.notification.NotificationService;
import com.project.safetyFence.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 일시 지오펜스가 종료 시각까지 진입되지 않은 경우 자동으로 알림을 생성하고 삭제한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemporaryGeofenceExpiryScheduler {

    private static final int TEMPORARY_GEOFENCE_TYPE = 1;

    private final GeofenceRepository geofenceRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${geofence.temporary.expiration-check-interval:60000}")
    @Transactional
    public void cleanUpExpiredTemporaryGeofences() {
        LocalDateTime now = LocalDateTime.now();
        List<Geofence> expiredGeofences =
                geofenceRepository.findByTypeAndEndTimeBefore(TEMPORARY_GEOFENCE_TYPE, now);

        if (expiredGeofences.isEmpty()) {
            return;
        }

        expiredGeofences.forEach(geofence -> handleExpiration(geofence, now));
    }

    private void handleExpiration(Geofence geofence, LocalDateTime now) {
        User user = geofence.getUser();

        // 1. 만료 로그 저장
        saveExpirationLog(user, geofence, now);

        // 2. 보호자들에게 알림 전송
        sendExpirationNotification(user, geofence);

        // 3. 지오펜스 삭제
        user.removeGeofence(geofence); // orphanRemoval = true

        log.info("일시 지오펜스 만료: userNumber={}, geofenceId={}, name={}",
                user.getNumber(), geofence.getId(), geofence.getName());
    }

    private void saveExpirationLog(User user, Geofence geofence, LocalDateTime now) {
        Log expirationLog = new Log(
                user,
                geofence.getName() + " (미진입)",
                geofence.getAddress(),
                now
        );

        user.addLog(expirationLog);
    }

    /**
     * 일시적 지오펜스 만료 시 보호자들에게 알림 전송
     */
    private void sendExpirationNotification(User user, Geofence geofence) {
        String title = "⏰ " + user.getName() + "님 일정 알림";
        String body = geofence.getName() + "에 시간 내 도착하지 않았습니다.";

        notificationService.sendNotificationToSupporters(user, title, body);
    }
}
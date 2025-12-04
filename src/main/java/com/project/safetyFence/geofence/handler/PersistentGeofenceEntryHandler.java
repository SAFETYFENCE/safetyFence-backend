package com.project.safetyFence.geofence.handler;

import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.notification.NotificationService;
import com.project.safetyFence.user.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 영구형 지오펜스 진입 처리 핸들러
 *
 * 영구형 지오펜스(type=0)에 진입하면 maxSequence를 차감합니다.
 * 진입 로그는 BaseGeofenceEntryHandler에서 자동 저장됩니다.
 */
@Slf4j
@Component
public class PersistentGeofenceEntryHandler extends BaseGeofenceEntryHandler {

    public PersistentGeofenceEntryHandler(NotificationService notificationService) {
        super(notificationService);
    }

    @Override
    protected void handleEntry(User user, Geofence geofence) {
        int currentMaxSequence = geofence.getMaxSequence();
        if (currentMaxSequence > 0) {
            geofence.decreaseMaxSequence();
            log.info("영구 지오펜스 진입: 지오펜스 ID {}의 maxSequence 차감됨. (현재: {})",
                    geofence.getId(), geofence.getMaxSequence());
        } else {
            log.info("영구 지오펜스 진입: 지오펜스 ID {}의 maxSequence가 이미 0입니다.",
                    geofence.getId());
        }
    }

    @Override
    public boolean supports(int type) {
        return type == 0; // 영구형
    }
}

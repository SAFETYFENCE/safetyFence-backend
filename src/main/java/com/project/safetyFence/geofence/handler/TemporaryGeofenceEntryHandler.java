package com.project.safetyFence.geofence.handler;

import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.notification.NotificationService;
import com.project.safetyFence.user.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 일시형 지오펜스 진입 처리 핸들러
 *
 * 일시형 지오펜스(type=1)에 진입하면 해당 지오펜스를 삭제합니다.
 * 진입 로그는 BaseGeofenceEntryHandler에서 자동 저장됩니다.
 */
@Slf4j
@Component
public class TemporaryGeofenceEntryHandler extends BaseGeofenceEntryHandler {

    public TemporaryGeofenceEntryHandler(NotificationService notificationService) {
        super(notificationService);
    }

    @Override
    protected void handleEntry(User user, Geofence geofence) {
        user.removeGeofence(geofence);
        log.info("일시적인 지오펜스 진입: 지오펜스 ID {} 삭제됨.", geofence.getId());
    }

    @Override
    public boolean supports(int type) {
        return type == 1; // 일시형
    }
}

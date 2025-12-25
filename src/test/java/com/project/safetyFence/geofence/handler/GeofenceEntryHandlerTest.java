package com.project.safetyFence.geofence.handler;

import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.log.domain.Log;
import com.project.safetyFence.notification.NotificationService;
import com.project.safetyFence.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@DisplayName("지오펜스 진입 핸들러 테스트")
@ExtendWith(MockitoExtension.class)
class GeofenceEntryHandlerTest {

    @Mock
    private NotificationService notificationService;

    private PersistentGeofenceEntryHandler persistentHandler;
    private TemporaryGeofenceEntryHandler temporaryHandler;

    @BeforeEach
    void setUp() {
        persistentHandler = new PersistentGeofenceEntryHandler(notificationService);
        temporaryHandler = new TemporaryGeofenceEntryHandler(notificationService);
    }

    @Test
    @DisplayName("영구형 지오펜스 진입 시 로그 저장 및 maxSequence 차감")
    void persistentGeofenceEntry_shouldSaveLogAndDecreaseMaxSequence() {
        // given
        User user = new User("010-1234-5678", "홍길동", "password", LocalDate.of(1990, 1, 1), "LINK123");

        Geofence geofence = new Geofence(
            user,
            "집",
            "서울시 강남구 테헤란로",
            BigDecimal.valueOf(37.5665),
            BigDecimal.valueOf(126.9780),
            0,  // 영구형
            999
        );

        user.addGeofence(geofence);

        int initialMaxSequence = geofence.getMaxSequence();
        int initialLogCount = user.getLogs().size();

        // when
        persistentHandler.handle(user, geofence);

        // then
        // 1. 로그가 저장되었는지 확인
        assertThat(user.getLogs()).hasSize(initialLogCount + 1);

        Log savedLog = user.getLogs().get(user.getLogs().size() - 1);
        assertThat(savedLog.getLocation()).isEqualTo("집");
        assertThat(savedLog.getLocationAddress()).isEqualTo("서울시 강남구 테헤란로");
        assertThat(savedLog.getArriveTime()).isNotNull();
        assertThat(savedLog.getUser()).isEqualTo(user);

        // 2. maxSequence가 차감되었는지 확인
        assertThat(geofence.getMaxSequence()).isEqualTo(initialMaxSequence - 1);
    }

    @Test
    @DisplayName("영구형 지오펜스 진입 시 maxSequence가 0이면 차감하지 않음")
    void persistentGeofenceEntry_whenMaxSequenceZero_shouldNotDecrease() {
        // given
        User user = new User("010-1234-5678", "홍길동", "password", LocalDate.of(1990, 1, 1), "LINK123");

        Geofence geofence = new Geofence(
            user,
            "센터",
            "서울시 서초구",
            BigDecimal.valueOf(37.4833),
            BigDecimal.valueOf(127.0322),
            0,  // 영구형
            0   // maxSequence = 0
        );

        user.addGeofence(geofence);

        // when
        persistentHandler.handle(user, geofence);

        // then
        // 1. 로그는 저장됨
        assertThat(user.getLogs()).hasSize(1);

        // 2. maxSequence는 그대로 0
        assertThat(geofence.getMaxSequence()).isEqualTo(0);
    }

    @Test
    @DisplayName("일시형 지오펜스 진입 시 로그 저장 및 지오펜스 삭제")
    void temporaryGeofenceEntry_shouldSaveLogAndRemoveGeofence() {
        // given
        User user = new User("010-1234-5678", "홍길동", "password", LocalDate.of(1990, 1, 1), "LINK123");

        Geofence geofence = new Geofence(
            user,
            "약속장소",
            "서울시 마포구 홍대입구역",
            BigDecimal.valueOf(37.5572),
            BigDecimal.valueOf(126.9239),
            1,  // 일시형
            java.time.LocalDateTime.of(2025, 11, 2, 14, 0),
            java.time.LocalDateTime.of(2025, 11, 2, 18, 0),
            100
        );

        user.addGeofence(geofence);

        int initialGeofenceCount = user.getGeofences().size();
        int initialLogCount = user.getLogs().size();

        // when
        temporaryHandler.handle(user, geofence);

        // then
        // 1. 로그가 저장되었는지 확인
        assertThat(user.getLogs()).hasSize(initialLogCount + 1);

        Log savedLog = user.getLogs().get(user.getLogs().size() - 1);
        assertThat(savedLog.getLocation()).isEqualTo("약속장소");
        assertThat(savedLog.getLocationAddress()).isEqualTo("서울시 마포구 홍대입구역");

        // 2. 지오펜스가 삭제되었는지 확인
        assertThat(user.getGeofences()).hasSize(initialGeofenceCount - 1);
        assertThat(user.getGeofences()).doesNotContain(geofence);
    }

    @Test
    @DisplayName("PersistentGeofenceEntryHandler는 type=0을 지원")
    void persistentHandler_supportsType0() {
        // when & then
        assertThat(persistentHandler.supports(0)).isTrue();
        assertThat(persistentHandler.supports(1)).isFalse();
    }

    @Test
    @DisplayName("TemporaryGeofenceEntryHandler는 type=1을 지원")
    void temporaryHandler_supportsType1() {
        // when & then
        assertThat(temporaryHandler.supports(1)).isTrue();
        assertThat(temporaryHandler.supports(0)).isFalse();
    }

    @Test
    @DisplayName("여러 지오펜스 진입 시 로그가 순차적으로 저장됨")
    void multipleGeofenceEntry_shouldSaveLogsSequentially() {
        // given
        User user = new User("010-1234-5678", "홍길동", "password", LocalDate.of(1990, 1, 1), "LINK123");

        Geofence geofence1 = new Geofence(
            user, "집", "서울시 강남구",
            BigDecimal.valueOf(37.5665), BigDecimal.valueOf(126.9780),
            0, 999
        );

        Geofence geofence2 = new Geofence(
            user, "센터", "서울시 서초구",
            BigDecimal.valueOf(37.4833), BigDecimal.valueOf(127.0322),
            0, 999
        );

        user.addGeofence(geofence1);
        user.addGeofence(geofence2);

        // when
        persistentHandler.handle(user, geofence1);
        persistentHandler.handle(user, geofence2);

        // then
        assertThat(user.getLogs()).hasSize(2);
        assertThat(user.getLogs().get(0).getLocation()).isEqualTo("집");
        assertThat(user.getLogs().get(1).getLocation()).isEqualTo("센터");
    }
}

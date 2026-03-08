package com.project.safetyFence.service;

import com.project.safetyFence.location.domain.UserLocation;
import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.location.strategy.DistanceBasedSaveStrategy;
import com.project.safetyFence.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DistanceBasedSaveStrategyTest {

    private DistanceBasedSaveStrategy strategy;
    private User testUser;

    // 서울 강남역 좌표
    private static final double GANGNAM_LAT = 37.497942;
    private static final double GANGNAM_LNG = 127.027621;

    @BeforeEach
    void setUp() {
        strategy = new DistanceBasedSaveStrategy();
        testUser = new User("01012345678", "테스터", "111", LocalDate.of(1990, 1, 1), "test-link");
    }

    @Test
    @DisplayName("이전 위치가 null이면 무조건 저장")
    void shouldSave_NoPreviousLocation_ReturnsTrue() {
        // given
        LocationUpdateDto current = new LocationUpdateDto("01012345678", GANGNAM_LAT, GANGNAM_LNG, System.currentTimeMillis());

        // when
        boolean result = strategy.shouldSave(null, current);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("100m 이상 이동 시 저장")
    void shouldSave_DistanceOver100m_ReturnsTrue() {
        // given - 강남역
        UserLocation previous = new UserLocation(
                testUser,
                BigDecimal.valueOf(GANGNAM_LAT),
                BigDecimal.valueOf(GANGNAM_LNG)
        );

        // 약 150m 북쪽 (위도 0.00135 ≈ 150m)
        LocationUpdateDto current = new LocationUpdateDto(
                "01012345678",
                GANGNAM_LAT + 0.00135,
                GANGNAM_LNG,
                System.currentTimeMillis()
        );

        // when
        boolean result = strategy.shouldSave(previous, current);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("100m 미만 이동 + 1분 미만 경과 시 저장 안함")
    void shouldSave_DistanceUnder100m_TimeUnder1min_ReturnsFalse() {
        // given - 강남역
        UserLocation previous = new UserLocation(
                testUser,
                BigDecimal.valueOf(GANGNAM_LAT),
                BigDecimal.valueOf(GANGNAM_LNG)
        );

        // 약 50m 이동 (위도 0.00045 ≈ 50m), 30초 후
        long now = System.currentTimeMillis();
        LocationUpdateDto current = new LocationUpdateDto(
                "01012345678",
                GANGNAM_LAT + 0.00045,
                GANGNAM_LNG,
                now + 30_000 // 30초
        );

        // when - previous의 savedTime은 now 근처이므로 timeDiff < 60초
        boolean result = strategy.shouldSave(previous, current);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("100m 미만 이동이지만 1분 이상 경과 시 저장")
    void shouldSave_DistanceUnder100m_TimeOver1min_ReturnsTrue() {
        // given
        UserLocation previous = new UserLocation(
                testUser,
                BigDecimal.valueOf(GANGNAM_LAT),
                BigDecimal.valueOf(GANGNAM_LNG)
        );

        // 약 10m 이동, 2분 후
        long previousTimestamp = previous.getSavedTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        LocationUpdateDto current = new LocationUpdateDto(
                "01012345678",
                GANGNAM_LAT + 0.0001,
                GANGNAM_LNG,
                previousTimestamp + 120_000 // 2분
        );

        // when
        boolean result = strategy.shouldSave(previous, current);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("경계값 - 정확히 100m 이동 시 저장")
    void shouldSave_ExactlyAt100m_ReturnsTrue() {
        // given
        UserLocation previous = new UserLocation(
                testUser,
                BigDecimal.valueOf(GANGNAM_LAT),
                BigDecimal.valueOf(GANGNAM_LNG)
        );

        // 약 100m 이동 (위도 0.0009 ≈ 100m)
        LocationUpdateDto current = new LocationUpdateDto(
                "01012345678",
                GANGNAM_LAT + 0.0009,
                GANGNAM_LNG,
                System.currentTimeMillis()
        );

        // when
        boolean result = strategy.shouldSave(previous, current);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("같은 위치에서 움직이지 않으면 저장 안함 (1분 미만)")
    void shouldSave_SameLocation_ReturnsFalse() {
        // given
        UserLocation previous = new UserLocation(
                testUser,
                BigDecimal.valueOf(GANGNAM_LAT),
                BigDecimal.valueOf(GANGNAM_LNG)
        );

        // 같은 위치, 10초 후
        long previousTimestamp = previous.getSavedTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        LocationUpdateDto current = new LocationUpdateDto(
                "01012345678",
                GANGNAM_LAT,
                GANGNAM_LNG,
                previousTimestamp + 10_000
        );

        // when
        boolean result = strategy.shouldSave(previous, current);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("경도 방향 이동도 정상 감지")
    void shouldSave_LongitudeMovement_ReturnsTrue() {
        // given
        UserLocation previous = new UserLocation(
                testUser,
                BigDecimal.valueOf(GANGNAM_LAT),
                BigDecimal.valueOf(GANGNAM_LNG)
        );

        // 경도 방향 약 200m 이동
        LocationUpdateDto current = new LocationUpdateDto(
                "01012345678",
                GANGNAM_LAT,
                GANGNAM_LNG + 0.0023,
                System.currentTimeMillis()
        );

        // when
        boolean result = strategy.shouldSave(previous, current);

        // then
        assertThat(result).isTrue();
    }
}

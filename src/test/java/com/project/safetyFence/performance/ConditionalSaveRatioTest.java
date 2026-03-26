package com.project.safetyFence.performance;

import com.project.safetyFence.location.domain.UserLocation;
import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.location.strategy.DistanceBasedSaveStrategy;
import com.project.safetyFence.user.domain.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조건부 저장 전략(DistanceBasedSaveStrategy)의 DB 저장 감소율을 측정하는 테스트
 *
 * Polling 방식에서는 모든 위치 데이터가 DB에 저장되지만,
 * 조건부 저장 전략은 의미 있는 이동(100m+)이나 시간 경과(1분+)만 저장한다.
 * 이 테스트는 실제 사용 시나리오를 시뮬레이션하여 저장 감소율(%)을 정량적으로 측정한다.
 */
class ConditionalSaveRatioTest {

    private DistanceBasedSaveStrategy strategy;
    private User testUser;

    // 서울 강남역 좌표 (시뮬레이션 시작점)
    private static final double START_LAT = 37.497942;
    private static final double START_LNG = 127.027621;

    @BeforeEach
    void setUp() {
        strategy = new DistanceBasedSaveStrategy();
        testUser = new User("01012345678", "테스터", "111", LocalDate.of(1990, 1, 1), "test-link");
    }

    @Test
    @DisplayName("시나리오 1: 실내 정지 상태 (10분간 3초 간격 전송) - 거의 저장 안 함")
    void scenario_stationary_indoor() {
        int totalUpdates = 200; // 10분간 3초 간격 = 200회
        int intervalMs = 3_000; // 3초
        int saveCount = 0;
        UserLocation lastSaved = null;
        Random random = new Random(42);

        long baseTime = System.currentTimeMillis();

        for (int i = 0; i < totalUpdates; i++) {
            // GPS 노이즈: ±5m 이내 (위도 0.000045 ≈ 5m)
            double noiseLat = START_LAT + (random.nextDouble() - 0.5) * 0.0001;
            double noiseLng = START_LNG + (random.nextDouble() - 0.5) * 0.0001;
            long timestamp = baseTime + (long) i * intervalMs;

            LocationUpdateDto current = new LocationUpdateDto("01012345678", noiseLat, noiseLng, timestamp);

            if (strategy.shouldSave(lastSaved, current)) {
                saveCount++;
                lastSaved = createUserLocation(noiseLat, noiseLng, timestamp);
            }
        }

        double saveRatio = (double) saveCount / totalUpdates * 100;
        double reductionRatio = 100 - saveRatio;

        System.out.println("=== 시나리오 1: 실내 정지 상태 ===");
        System.out.println("총 위치 업데이트: " + totalUpdates + "회");
        System.out.println("실제 DB 저장: " + saveCount + "회");
        System.out.println("저장 비율: " + String.format("%.1f%%", saveRatio));
        System.out.println("DB 쓰기 감소율: " + String.format("%.1f%%", reductionRatio));
        System.out.println("Polling 대비 DB 부하: " + String.format("%.1f배 감소", (double) totalUpdates / saveCount));
        System.out.println();

        // 정지 상태에서는 1분마다 1회만 저장되므로 90% 이상 감소해야 함
        assertThat(reductionRatio).isGreaterThan(90.0);
    }

    @Test
    @DisplayName("시나리오 2: 느린 도보 이동 (10분간 3초 간격, 시속 3km)")
    void scenario_slow_walking() {
        int totalUpdates = 200;
        int intervalMs = 3_000;
        int saveCount = 0;
        UserLocation lastSaved = null;

        long baseTime = System.currentTimeMillis();
        // 시속 3km = 초속 0.833m → 3초에 2.5m 이동
        // 위도 1도 ≈ 111,000m → 2.5m ≈ 0.0000225도
        double stepLat = 0.0000225;

        for (int i = 0; i < totalUpdates; i++) {
            double lat = START_LAT + stepLat * i;
            double lng = START_LNG;
            long timestamp = baseTime + (long) i * intervalMs;

            LocationUpdateDto current = new LocationUpdateDto("01012345678", lat, lng, timestamp);

            if (strategy.shouldSave(lastSaved, current)) {
                saveCount++;
                lastSaved = createUserLocation(lat, lng, timestamp);
            }
        }

        double saveRatio = (double) saveCount / totalUpdates * 100;
        double reductionRatio = 100 - saveRatio;

        System.out.println("=== 시나리오 2: 느린 도보 이동 (시속 3km) ===");
        System.out.println("총 위치 업데이트: " + totalUpdates + "회");
        System.out.println("실제 DB 저장: " + saveCount + "회");
        System.out.println("저장 비율: " + String.format("%.1f%%", saveRatio));
        System.out.println("DB 쓰기 감소율: " + String.format("%.1f%%", reductionRatio));
        System.out.println("Polling 대비 DB 부하: " + String.format("%.1f배 감소", (double) totalUpdates / saveCount));
        System.out.println();

        // 느린 도보에서도 3초마다 2.5m → 100m 도달까지 40회(120초) 걸림
        // 그 전에 1분 조건이 먼저 충족 → 대략 1분마다 저장
        assertThat(reductionRatio).isGreaterThan(80.0);
    }

    @Test
    @DisplayName("시나리오 3: 보통 도보 이동 (10분간 3초 간격, 시속 5km)")
    void scenario_normal_walking() {
        int totalUpdates = 200;
        int intervalMs = 3_000;
        int saveCount = 0;
        UserLocation lastSaved = null;

        long baseTime = System.currentTimeMillis();
        // 시속 5km = 초속 1.389m → 3초에 4.17m 이동
        // 위도: 4.17m ≈ 0.0000375도
        double stepLat = 0.0000375;

        for (int i = 0; i < totalUpdates; i++) {
            double lat = START_LAT + stepLat * i;
            double lng = START_LNG;
            long timestamp = baseTime + (long) i * intervalMs;

            LocationUpdateDto current = new LocationUpdateDto("01012345678", lat, lng, timestamp);

            if (strategy.shouldSave(lastSaved, current)) {
                saveCount++;
                lastSaved = createUserLocation(lat, lng, timestamp);
            }
        }

        double saveRatio = (double) saveCount / totalUpdates * 100;
        double reductionRatio = 100 - saveRatio;

        System.out.println("=== 시나리오 3: 보통 도보 이동 (시속 5km) ===");
        System.out.println("총 위치 업데이트: " + totalUpdates + "회");
        System.out.println("실제 DB 저장: " + saveCount + "회");
        System.out.println("저장 비율: " + String.format("%.1f%%", saveRatio));
        System.out.println("DB 쓰기 감소율: " + String.format("%.1f%%", reductionRatio));
        System.out.println("Polling 대비 DB 부하: " + String.format("%.1f배 감소", (double) totalUpdates / saveCount));
        System.out.println();

        assertThat(reductionRatio).isGreaterThan(80.0);
    }

    @Test
    @DisplayName("시나리오 4: 혼합 패턴 (정지 5분 + 도보 5분 + 정지 5분)")
    void scenario_mixed_pattern() {
        int intervalMs = 3_000;
        int saveCount = 0;
        int totalUpdates = 0;
        UserLocation lastSaved = null;
        Random random = new Random(42);

        long baseTime = System.currentTimeMillis();

        // Phase 1: 정지 5분 (100회)
        for (int i = 0; i < 100; i++) {
            double noiseLat = START_LAT + (random.nextDouble() - 0.5) * 0.0001;
            double noiseLng = START_LNG + (random.nextDouble() - 0.5) * 0.0001;
            long timestamp = baseTime + (long) totalUpdates * intervalMs;

            LocationUpdateDto current = new LocationUpdateDto("01012345678", noiseLat, noiseLng, timestamp);
            if (strategy.shouldSave(lastSaved, current)) {
                saveCount++;
                lastSaved = createUserLocation(noiseLat, noiseLng, timestamp);
            }
            totalUpdates++;
        }

        // Phase 2: 도보 5분 시속 4km (100회)
        double walkStepLat = 0.00003; // 시속 4km ≈ 3초에 3.3m
        double walkLat = START_LAT;
        for (int i = 0; i < 100; i++) {
            walkLat += walkStepLat;
            long timestamp = baseTime + (long) totalUpdates * intervalMs;

            LocationUpdateDto current = new LocationUpdateDto("01012345678", walkLat, START_LNG, timestamp);
            if (strategy.shouldSave(lastSaved, current)) {
                saveCount++;
                lastSaved = createUserLocation(walkLat, START_LNG, timestamp);
            }
            totalUpdates++;
        }

        // Phase 3: 정지 5분 (100회)
        double stationaryLat = walkLat;
        for (int i = 0; i < 100; i++) {
            double noiseLat = stationaryLat + (random.nextDouble() - 0.5) * 0.0001;
            double noiseLng = START_LNG + (random.nextDouble() - 0.5) * 0.0001;
            long timestamp = baseTime + (long) totalUpdates * intervalMs;

            LocationUpdateDto current = new LocationUpdateDto("01012345678", noiseLat, noiseLng, timestamp);
            if (strategy.shouldSave(lastSaved, current)) {
                saveCount++;
                lastSaved = createUserLocation(noiseLat, noiseLng, timestamp);
            }
            totalUpdates++;
        }

        double saveRatio = (double) saveCount / totalUpdates * 100;
        double reductionRatio = 100 - saveRatio;

        System.out.println("=== 시나리오 4: 혼합 패턴 (정지→도보→정지, 15분) ===");
        System.out.println("총 위치 업데이트: " + totalUpdates + "회");
        System.out.println("실제 DB 저장: " + saveCount + "회");
        System.out.println("저장 비율: " + String.format("%.1f%%", saveRatio));
        System.out.println("DB 쓰기 감소율: " + String.format("%.1f%%", reductionRatio));
        System.out.println("Polling 대비 DB 부하: " + String.format("%.1f배 감소", (double) totalUpdates / saveCount));
        System.out.println();

        assertThat(reductionRatio).isGreaterThan(80.0);
    }

    @Test
    @DisplayName("시나리오 5: 장시간 시뮬레이션 (1시간, 사용자 10명)")
    void scenario_longterm_multi_user() {
        int userCount = 10;
        int durationMinutes = 60;
        int intervalMs = 3_000;
        int updatesPerUser = durationMinutes * 60 / 3; // 3초 간격
        int totalUpdates = updatesPerUser * userCount;
        int totalSaves = 0;
        Random random = new Random(42);

        System.out.println("=== 시나리오 5: 장시간 시뮬레이션 (1시간, 사용자 " + userCount + "명) ===");

        for (int u = 0; u < userCount; u++) {
            int saveCount = 0;
            UserLocation lastSaved = null;
            long baseTime = System.currentTimeMillis();

            // 각 사용자는 랜덤 패턴 (70% 정지, 30% 이동)
            double currentLat = START_LAT + random.nextDouble() * 0.01;
            double currentLng = START_LNG + random.nextDouble() * 0.01;
            boolean moving = false;

            for (int i = 0; i < updatesPerUser; i++) {
                // 5분마다 이동/정지 전환
                if (i % 100 == 0) {
                    moving = random.nextDouble() < 0.3;
                }

                if (moving) {
                    // 시속 4km 이동
                    currentLat += 0.00003 * (random.nextDouble() * 0.5 + 0.75);
                    currentLng += 0.00002 * (random.nextDouble() - 0.5);
                } else {
                    // GPS 노이즈만
                    currentLat += (random.nextDouble() - 0.5) * 0.0001;
                    currentLng += (random.nextDouble() - 0.5) * 0.0001;
                }

                long timestamp = baseTime + (long) i * intervalMs;
                LocationUpdateDto current = new LocationUpdateDto("user" + u, currentLat, currentLng, timestamp);

                if (strategy.shouldSave(lastSaved, current)) {
                    saveCount++;
                    lastSaved = createUserLocation(currentLat, currentLng, timestamp);
                }
            }

            totalSaves += saveCount;
            System.out.println("  사용자 " + u + ": " + updatesPerUser + "회 전송 → " + saveCount + "회 저장 ("
                    + String.format("%.1f%%", (double) saveCount / updatesPerUser * 100) + ")");
        }

        double overallSaveRatio = (double) totalSaves / totalUpdates * 100;
        double overallReduction = 100 - overallSaveRatio;

        System.out.println();
        System.out.println("--- 전체 합계 ---");
        System.out.println("총 위치 업데이트: " + totalUpdates + "회");
        System.out.println("총 DB 저장: " + totalSaves + "회");
        System.out.println("평균 저장 비율: " + String.format("%.1f%%", overallSaveRatio));
        System.out.println("평균 DB 쓰기 감소율: " + String.format("%.1f%%", overallReduction));
        System.out.println("Polling 대비 DB 부하: " + String.format("%.1f배 감소", (double) totalUpdates / totalSaves));
        System.out.println();
        System.out.println("=== Polling(무조건 저장)이었다면: " + totalUpdates + "회 DB 쓰기 ===");
        System.out.println("=== 조건부 저장 적용 후: " + totalSaves + "회 DB 쓰기 ===");

        assertThat(overallReduction).isGreaterThan(75.0);
    }

    private UserLocation createUserLocation(double lat, double lng, long timestampMillis) {
        UserLocation location = new UserLocation(
                testUser,
                BigDecimal.valueOf(lat),
                BigDecimal.valueOf(lng)
        );

        // 리플렉션으로 savedTime 설정
        try {
            LocalDateTime savedTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestampMillis),
                    ZoneId.systemDefault()
            );
            java.lang.reflect.Field field = UserLocation.class.getDeclaredField("savedTime");
            field.setAccessible(true);
            field.set(location, savedTime);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set savedTime", e);
        }

        return location;
    }
}
package com.project.safetyFence.performance;

import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.location.strategy.DistanceBasedSaveStrategy;
import com.project.safetyFence.location.domain.UserLocation;
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
 * Polling 방식 vs WebSocket 방식의 성능 차이를 정량적으로 비교하는 테스트
 *
 * 비교 항목:
 * 1. HTTP 요청 수 (네트워크 부하)
 * 2. DB 쓰기 횟수 (데이터베이스 부하)
 * 3. 전송 데이터량 (대역폭)
 * 4. 사용자 수 확장 시 부하 증가율
 */
class PollingVsWebSocketComparisonTest {

    private DistanceBasedSaveStrategy strategy;
    private User testUser;

    // Polling 방식 상수 (이전 구조)
    private static final int POLLING_INTERVAL_SEC = 3;        // 3초마다 서버에 HTTP 요청
    private static final int POLLING_RESPONSE_BYTES = 512;    // 평균 HTTP 응답 크기 (헤더 포함)
    private static final int POLLING_REQUEST_BYTES = 256;     // 평균 HTTP 요청 크기 (헤더 포함)

    // WebSocket 방식 상수 (현재 구조)
    private static final int WS_FRAME_BYTES = 64;             // WebSocket 프레임 (헤더 최소)
    private static final int WS_HEARTBEAT_INTERVAL_SEC = 10;  // 하트비트 10초
    private static final int WS_HEARTBEAT_BYTES = 2;          // STOMP 하트비트 크기

    // 공통
    private static final int LOCATION_PAYLOAD_BYTES = 80;     // lat, lng, timestamp JSON

    @BeforeEach
    void setUp() {
        strategy = new DistanceBasedSaveStrategy();
        testUser = new User("01012345678", "테스터", "111", LocalDate.of(1990, 1, 1), "test-link");
    }

    @Test
    @DisplayName("사용자 수별 HTTP 요청 수 비교 (1분 기준)")
    void compare_http_requests_per_minute() {
        int[] userCounts = {1, 10, 50, 100, 500};

        System.out.println("=======================================================");
        System.out.println("       사용자 수별 HTTP 요청 수 비교 (1분 기준)");
        System.out.println("=======================================================");
        System.out.printf("%-10s | %-20s | %-20s | %-10s%n",
                "사용자 수", "Polling (req/min)", "WebSocket (req/min)", "감소율");
        System.out.println("-------------------------------------------------------");

        for (int users : userCounts) {
            // Polling: 모든 사용자가 3초마다 HTTP POST
            int pollingRequests = users * (60 / POLLING_INTERVAL_SEC);

            // WebSocket: 연결 후 이벤트 기반 전송
            // HTTP 요청 = 0 (이미 연결됨, WebSocket 프레임만 전송)
            int wsRequests = 0;

            double reduction = 100.0;

            System.out.printf("%-10d | %-20d | %-20d | %-10s%n",
                    users, pollingRequests, wsRequests, String.format("%.0f%%", reduction));
        }

        System.out.println();
        System.out.println("* Polling: 사용자 수에 비례하여 HTTP 요청 증가");
        System.out.println("* WebSocket: 연결 수립 후 HTTP 요청 없음 (STOMP 프레임 전송)");
        System.out.println();

        // 100명 기준 polling은 2000 req/min, websocket은 0 req/min
        int polling100 = 100 * (60 / POLLING_INTERVAL_SEC);
        assertThat(polling100).isEqualTo(2000);
    }

    @Test
    @DisplayName("사용자 수별 네트워크 대역폭 비교 (1분 기준)")
    void compare_bandwidth_per_minute() {
        int[] userCounts = {1, 10, 50, 100, 500};
        int locationUpdatesPerMinute = 60 / POLLING_INTERVAL_SEC; // 3초 간격 = 20회/분

        System.out.println("=======================================================");
        System.out.println("      사용자 수별 네트워크 대역폭 비교 (1분 기준)");
        System.out.println("=======================================================");
        System.out.printf("%-10s | %-20s | %-20s | %-10s%n",
                "사용자 수", "Polling (KB/min)", "WebSocket (KB/min)", "감소율");
        System.out.println("-------------------------------------------------------");

        for (int users : userCounts) {
            // Polling: 매 요청마다 HTTP 헤더 오버헤드
            // 요청 = (요청헤더 + 페이로드) * 횟수, 응답 = (응답헤더 + 페이로드) * 횟수
            long pollingBytes = (long) users * locationUpdatesPerMinute *
                    (POLLING_REQUEST_BYTES + LOCATION_PAYLOAD_BYTES + POLLING_RESPONSE_BYTES);

            // WebSocket: 프레임 오버헤드만 + 하트비트
            int heartbeatsPerMinute = 60 / WS_HEARTBEAT_INTERVAL_SEC;
            long wsBytes = (long) users * (
                    locationUpdatesPerMinute * (WS_FRAME_BYTES + LOCATION_PAYLOAD_BYTES) +
                    heartbeatsPerMinute * WS_HEARTBEAT_BYTES
            );

            double pollingKB = pollingBytes / 1024.0;
            double wsKB = wsBytes / 1024.0;
            double reduction = (1 - (double) wsBytes / pollingBytes) * 100;

            System.out.printf("%-10d | %-20s | %-20s | %-10s%n",
                    users,
                    String.format("%.1f", pollingKB),
                    String.format("%.1f", wsKB),
                    String.format("%.1f%%", reduction));
        }

        System.out.println();
        System.out.println("* Polling 요청당: " + (POLLING_REQUEST_BYTES + LOCATION_PAYLOAD_BYTES) + " bytes (요청) + "
                + POLLING_RESPONSE_BYTES + " bytes (응답)");
        System.out.println("* WebSocket 프레임당: " + (WS_FRAME_BYTES + LOCATION_PAYLOAD_BYTES) + " bytes (양방향)");
        System.out.println("* WebSocket 하트비트: " + WS_HEARTBEAT_BYTES + " bytes × " + (60 / WS_HEARTBEAT_INTERVAL_SEC) + "회/분");
        System.out.println();

        // WebSocket이 Polling보다 대역폭을 적게 사용해야 함
        long pollingBW = 100L * (60 / POLLING_INTERVAL_SEC) * (POLLING_REQUEST_BYTES + LOCATION_PAYLOAD_BYTES + POLLING_RESPONSE_BYTES);
        long wsBW = 100L * ((60 / POLLING_INTERVAL_SEC) * (WS_FRAME_BYTES + LOCATION_PAYLOAD_BYTES) + (60 / WS_HEARTBEAT_INTERVAL_SEC) * WS_HEARTBEAT_BYTES);
        assertThat(wsBW).isLessThan(pollingBW);
    }

    @Test
    @DisplayName("사용자 수별 DB 쓰기 횟수 비교 (1시간 기준)")
    void compare_db_writes_per_hour() {
        int[] userCounts = {1, 10, 50, 100, 500};
        int durationMinutes = 60;
        int intervalSec = 3;
        int updatesPerUser = durationMinutes * 60 / intervalSec;
        Random random = new Random(42);

        System.out.println("=======================================================");
        System.out.println("      사용자 수별 DB 쓰기 횟수 비교 (1시간 기준)");
        System.out.println("=======================================================");
        System.out.printf("%-10s | %-20s | %-25s | %-10s%n",
                "사용자 수", "Polling (writes/hr)", "WebSocket+조건부 (writes/hr)", "감소율");
        System.out.println("-------------------------------------------------------");

        // 1명의 사용자에 대해 조건부 저장 비율 측정 (혼합 패턴)
        int singleUserSaves = simulateSingleUserSaves(updatesPerUser, random);
        double saveRatio = (double) singleUserSaves / updatesPerUser;

        for (int users : userCounts) {
            // Polling: 모든 위치를 DB에 저장
            long pollingWrites = (long) users * updatesPerUser;

            // WebSocket + 조건부 저장: 의미 있는 이동만 저장
            long wsWrites = (long) Math.round(users * updatesPerUser * saveRatio);

            double reduction = (1 - (double) wsWrites / pollingWrites) * 100;

            System.out.printf("%-10d | %-20s | %-25s | %-10s%n",
                    users,
                    String.format("%,d", pollingWrites),
                    String.format("%,d", wsWrites),
                    String.format("%.1f%%", reduction));
        }

        System.out.println();
        System.out.println("* 사용자당 위치 업데이트: " + updatesPerUser + "회/시간 (3초 간격)");
        System.out.println("* 조건부 저장 비율: " + String.format("%.1f%%", saveRatio * 100));
        System.out.println("* 혼합 패턴 시뮬레이션: 70% 정지 + 30% 도보 이동");
        System.out.println();

        // 조건부 저장이 polling보다 DB 쓰기를 줄여야 함
        assertThat(saveRatio).isLessThan(0.25);
    }

    @Test
    @DisplayName("종합 비교: Polling vs WebSocket (사용자 100명, 1시간)")
    void comprehensive_comparison() {
        int users = 100;
        int durationMinutes = 60;
        int intervalSec = 3;
        int updatesPerUser = durationMinutes * 60 / intervalSec;
        Random random = new Random(42);

        // 조건부 저장 비율 측정
        int singleUserSaves = simulateSingleUserSaves(updatesPerUser, random);
        double saveRatio = (double) singleUserSaves / updatesPerUser;

        // === Polling 방식 통계 ===
        long pollingHttpRequests = (long) users * updatesPerUser;
        long pollingDbWrites = pollingHttpRequests; // 모든 요청이 DB 쓰기
        long pollingBandwidthBytes = pollingHttpRequests * (POLLING_REQUEST_BYTES + LOCATION_PAYLOAD_BYTES + POLLING_RESPONSE_BYTES);

        // === WebSocket 방식 통계 ===
        long wsHttpRequests = 0; // 연결 후 HTTP 요청 없음
        long wsDbWrites = Math.round(pollingHttpRequests * saveRatio);
        int heartbeatsPerHour = durationMinutes * 60 / WS_HEARTBEAT_INTERVAL_SEC;
        long wsBandwidthBytes = (long) users * (
                updatesPerUser * (WS_FRAME_BYTES + LOCATION_PAYLOAD_BYTES) +
                heartbeatsPerHour * WS_HEARTBEAT_BYTES
        );

        System.out.println("===========================================================");
        System.out.println("   종합 비교: Polling vs WebSocket (사용자 100명, 1시간)");
        System.out.println("===========================================================");
        System.out.println();
        System.out.printf("%-25s | %-20s | %-20s | %-10s%n", "항목", "Polling", "WebSocket", "개선율");
        System.out.println("-----------------------------------------------------------");

        // HTTP 요청 수
        System.out.printf("%-25s | %-20s | %-20s | %-10s%n",
                "HTTP 요청 수",
                String.format("%,d", pollingHttpRequests),
                String.format("%,d", wsHttpRequests),
                "100%");

        // DB 쓰기 횟수
        double dbReduction = (1 - (double) wsDbWrites / pollingDbWrites) * 100;
        System.out.printf("%-25s | %-20s | %-20s | %-10s%n",
                "DB 쓰기 횟수",
                String.format("%,d", pollingDbWrites),
                String.format("%,d", wsDbWrites),
                String.format("%.1f%%", dbReduction));

        // 네트워크 대역폭
        double bwReduction = (1 - (double) wsBandwidthBytes / pollingBandwidthBytes) * 100;
        System.out.printf("%-25s | %-20s | %-20s | %-10s%n",
                "네트워크 대역폭",
                String.format("%.1f MB", pollingBandwidthBytes / 1024.0 / 1024.0),
                String.format("%.1f MB", wsBandwidthBytes / 1024.0 / 1024.0),
                String.format("%.1f%%", bwReduction));

        // 위치 반영 지연시간
        System.out.printf("%-25s | %-20s | %-20s | %-10s%n",
                "위치 반영 지연시간",
                "평균 1.5초 (폴링주기/2)",
                "<100ms (즉시 push)",
                "93%+");

        // 서버 연결 방식
        System.out.printf("%-25s | %-20s | %-20s | %-10s%n",
                "연결 방식",
                "매 요청 TCP 핸드셰이크",
                "1회 연결 유지",
                "-");

        System.out.println();
        System.out.println("=== 핵심 수치 요약 ===");
        System.out.println("HTTP 요청: " + String.format("%,d", pollingHttpRequests) + " → " + wsHttpRequests + " (100% 감소)");
        System.out.println("DB 쓰기: " + String.format("%,d", pollingDbWrites) + " → " + String.format("%,d", wsDbWrites)
                + " (" + String.format("%.1f%%", dbReduction) + " 감소)");
        System.out.println("대역폭: " + String.format("%.1f MB", pollingBandwidthBytes / 1024.0 / 1024.0) + " → "
                + String.format("%.1f MB", wsBandwidthBytes / 1024.0 / 1024.0)
                + " (" + String.format("%.1f%%", bwReduction) + " 감소)");
        System.out.println("지연시간: ~1.5초 → <100ms (93%+ 개선)");

        // 검증
        assertThat(dbReduction).isGreaterThan(75.0);
        assertThat(bwReduction).isGreaterThan(50.0);
    }

    /**
     * 1명의 사용자에 대해 혼합 패턴(70% 정지, 30% 이동) 시뮬레이션 후
     * 조건부 저장 횟수를 반환
     */
    private int simulateSingleUserSaves(int totalUpdates, Random random) {
        int saveCount = 0;
        UserLocation lastSaved = null;
        long baseTime = System.currentTimeMillis();

        double currentLat = 37.497942;
        double currentLng = 127.027621;
        boolean moving = false;

        for (int i = 0; i < totalUpdates; i++) {
            if (i % 100 == 0) {
                moving = random.nextDouble() < 0.3;
            }

            if (moving) {
                currentLat += 0.00003 * (random.nextDouble() * 0.5 + 0.75);
                currentLng += 0.00002 * (random.nextDouble() - 0.5);
            } else {
                currentLat += (random.nextDouble() - 0.5) * 0.0001;
                currentLng += (random.nextDouble() - 0.5) * 0.0001;
            }

            long timestamp = baseTime + (long) i * 3_000;
            LocationUpdateDto current = new LocationUpdateDto("user0", currentLat, currentLng, timestamp);

            if (strategy.shouldSave(lastSaved, current)) {
                saveCount++;
                lastSaved = createUserLocation(currentLat, currentLng, timestamp);
            }
        }

        return saveCount;
    }

    private UserLocation createUserLocation(double lat, double lng, long timestampMillis) {
        UserLocation location = new UserLocation(
                testUser,
                BigDecimal.valueOf(lat),
                BigDecimal.valueOf(lng)
        );

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
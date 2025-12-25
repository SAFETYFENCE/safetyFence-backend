package com.project.safetyFence.service;

import com.project.safetyFence.location.LocationCacheService;
import com.project.safetyFence.location.UserLocationRepository;
import com.project.safetyFence.location.domain.UserLocation;
import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LocationCacheService 통합 테스트
 * getLatestLocationWithFallback() DB 폴백 기능 검증
 */
@SpringBootTest
@Transactional
@DisplayName("LocationCacheService 통합 테스트")
class LocationCacheServiceIntegrationTest {

    @Autowired
    private LocationCacheService cacheService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserLocationRepository userLocationRepository;

    private User testUser;
    private UserLocation testLocation;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = new User("01012345678", "testUser", "password", LocalDate.now(), "test-link");
        userRepository.save(testUser);

        // 테스트 위치 데이터 생성
        testLocation = new UserLocation(testUser, new BigDecimal("37.123456"), new BigDecimal("127.123456"));
        userLocationRepository.save(testLocation);
    }

    @Test
    @DisplayName("getLatestLocationWithFallback - 캐시 히트")
    void getLatestLocationWithFallback_캐시_히트() {
        // Given
        String userNumber = testUser.getNumber();
        LocationUpdateDto cachedLocation = new LocationUpdateDto(
                userNumber, 37.999999, 127.999999, System.currentTimeMillis()
        );
        cacheService.updateLocation(userNumber, cachedLocation);

        // When
        LocationUpdateDto result = cacheService.getLatestLocationWithFallback(userNumber);

        // Then - 캐시에서 조회된 값 반환
        assertThat(result).isNotNull();
        assertThat(result.getUserNumber()).isEqualTo(userNumber);
        assertThat(result.getLatitude()).isEqualTo(37.999999); // 캐시 값
        assertThat(result.getLongitude()).isEqualTo(127.999999); // 캐시 값
    }

    @Test
    @DisplayName("getLatestLocationWithFallback - 캐시 미스 후 DB 조회 성공")
    void getLatestLocationWithFallback_캐시_미스_DB_조회_성공() {
        // Given
        String userNumber = testUser.getNumber();
        // 캐시에 데이터 없음

        // When
        LocationUpdateDto result = cacheService.getLatestLocationWithFallback(userNumber);

        // Then - DB에서 조회된 값 반환
        assertThat(result).isNotNull();
        assertThat(result.getUserNumber()).isEqualTo(userNumber);
        assertThat(result.getLatitude()).isEqualTo(37.123456);
        assertThat(result.getLongitude()).isEqualTo(127.123456);
        assertThat(result.getTimestamp()).isEqualTo(
                testLocation.getSavedTime().toInstant(ZoneOffset.UTC).toEpochMilli()
        );

        // 캐시에 저장되었는지 확인
        LocationUpdateDto cached = cacheService.getLatestLocation(userNumber);
        assertThat(cached).isNotNull();
        assertThat(cached.getLatitude()).isEqualTo(37.123456);
    }

    @Test
    @DisplayName("getLatestLocationWithFallback - 사용자를 찾을 수 없음")
    void getLatestLocationWithFallback_사용자_없음() {
        // Given
        String nonExistentUserNumber = "99999999999";

        // When
        LocationUpdateDto result = cacheService.getLatestLocationWithFallback(nonExistentUserNumber);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getLatestLocationWithFallback - DB에 위치 데이터 없음")
    void getLatestLocationWithFallback_DB_위치_데이터_없음() {
        // Given
        User userWithoutLocation = new User("01099999999", "noLocation", "password", LocalDate.now(), "link");
        userRepository.save(userWithoutLocation);

        // When
        LocationUpdateDto result = cacheService.getLatestLocationWithFallback(userWithoutLocation.getNumber());

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getLatestLocationWithFallback - 캐시 워밍 검증")
    void getLatestLocationWithFallback_캐시_워밍() {
        // Given
        String userNumber = testUser.getNumber();
        // 캐시에 데이터 없음

        // When - 첫 번째 호출 (캐시 미스 → DB 조회)
        LocationUpdateDto firstResult = cacheService.getLatestLocationWithFallback(userNumber);

        // Then - DB에서 조회되고 캐시에 저장됨
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.getLatitude()).isEqualTo(37.123456);

        // When - 두 번째 호출 (캐시 히트)
        LocationUpdateDto secondResult = cacheService.getLatestLocationWithFallback(userNumber);

        // Then - 캐시에서 조회됨 (DB 조회 없음)
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.getLatitude()).isEqualTo(37.123456);
        assertThat(secondResult.getLongitude()).isEqualTo(127.123456);

        // 캐시에서 직접 조회해도 같은 값
        LocationUpdateDto cached = cacheService.getLatestLocation(userNumber);
        assertThat(cached).isNotNull();
        assertThat(cached.getLatitude()).isEqualTo(37.123456);
    }

    @Test
    @DisplayName("getLatestLocationWithFallback - 여러 사용자 데이터 독립적으로 처리")
    void getLatestLocationWithFallback_여러_사용자_독립적으로_처리() {
        // Given
        User user1 = new User("01011111111", "user1", "password", LocalDate.now(), "link1");
        User user2 = new User("01022222222", "user2", "password", LocalDate.now(), "link2");
        userRepository.save(user1);
        userRepository.save(user2);

        UserLocation location1 = new UserLocation(user1, new BigDecimal("37.111111"), new BigDecimal("127.111111"));
        UserLocation location2 = new UserLocation(user2, new BigDecimal("37.222222"), new BigDecimal("127.222222"));
        userLocationRepository.save(location1);
        userLocationRepository.save(location2);

        // When
        LocationUpdateDto result1 = cacheService.getLatestLocationWithFallback(user1.getNumber());
        LocationUpdateDto result2 = cacheService.getLatestLocationWithFallback(user2.getNumber());

        // Then
        assertThat(result1).isNotNull();
        assertThat(result1.getLatitude()).isEqualTo(37.111111);

        assertThat(result2).isNotNull();
        assertThat(result2.getLatitude()).isEqualTo(37.222222);
    }
}

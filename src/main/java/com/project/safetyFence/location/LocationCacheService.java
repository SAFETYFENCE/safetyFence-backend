package com.project.safetyFence.location;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.project.safetyFence.location.domain.UserLocation;
import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.user.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LocationCacheService {

    private final Cache<String, LocationUpdateDto> locationCache;
    private final UserRepository userRepository;
    private final UserLocationRepository userLocationRepository;

    public LocationCacheService(UserRepository userRepository, UserLocationRepository userLocationRepository) {
        this.userRepository = userRepository;
        this.userLocationRepository = userLocationRepository;
        this.locationCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats()
                .build();

        log.info("LocationCacheService 초기화 완료: maxSize=10000, TTL=5분");
    }


    // 사용자의 최신 위치 업데이트
    public void updateLocation(String userNumber, LocationUpdateDto location) {
        locationCache.put(userNumber, location);
        log.debug("위치 캐시 업데이트: userNumber={}, lat={}, lng={}",
                userNumber, location.getLatitude(), location.getLongitude());
    }


    // 사용자의 최신 위치 조회
    public LocationUpdateDto getLatestLocation(String userNumber) {
        LocationUpdateDto location = locationCache.getIfPresent(userNumber);

        if (location != null) {
            log.debug("위치 캐시 조회 성공: userNumber={}", userNumber);
        } else {
            log.debug("위치 캐시 조회 실패 (캐시 없음): userNumber={}", userNumber);
        }

        return location;
    }

    // 사용자의 위치 캐시 삭제
    // WebSocket 연결 해제 시 호출
    public void removeLocation(String userNumber) {
        locationCache.invalidate(userNumber);
        log.info("위치 캐시 삭제: userNumber={}", userNumber);
    }


    // 캐시 통계 조회 (모니터링용)
    public String getCacheStats() {
        return locationCache.stats().toString();
    }

    // 캐시 크기 조회
    public long getCacheSize() {
        return locationCache.estimatedSize();
    }

    /**
     * 캐시 조회 → 실패 시 DB 폴백 → 캐시 갱신
     * WebSocket 구독 시 최신 위치를 제공하기 위한 통합 메서드
     *
     * @param userNumber 사용자 번호
     * @return 위치 정보 (없으면 null)
     */
    public LocationUpdateDto getLatestLocationWithFallback(String userNumber) {
        // 1. 캐시 먼저 조회
        LocationUpdateDto cached = getLatestLocation(userNumber);
        if (cached != null) {
            log.debug("캐시 히트: userNumber={}", userNumber);
            return cached;
        }

        // 2. 캐시 미스 → DB 조회
        log.debug("캐시 미스, DB 조회 시도: userNumber={}", userNumber);

        try {
            User user = userRepository.findByNumber(userNumber);
            if (user == null) {
                log.warn("사용자를 찾을 수 없음: userNumber={}", userNumber);
                return null;
            }

            UserLocation dbLocation = userLocationRepository.findLatestByUser(user).orElse(null);
            if (dbLocation == null) {
                log.warn("위치 데이터 없음 (캐시/DB 모두): userNumber={}", userNumber);
                return null;
            }

            // 3. DB 데이터를 DTO로 변환
            LocationUpdateDto dto = new LocationUpdateDto(
                userNumber,
                dbLocation.getLatitude(),
                dbLocation.getLongitude(),
                dbLocation.getSavedTime().toInstant(ZoneOffset.UTC).toEpochMilli()
            );

            // 4. DB 데이터를 캐시에 저장 (캐시 워밍)
            updateLocation(userNumber, dto);
            log.info("DB 조회 성공 및 캐시 갱신: userNumber={}, lat={}, lng={}",
                userNumber, dto.getLatitude(), dto.getLongitude());

            return dto;

        } catch (Exception e) {
            log.error("DB 조회 중 오류 발생: userNumber={}, error={}", userNumber, e.getMessage(), e);
            return null;
        }
    }
}

package com.project.safetyFence.location;

import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.location.domain.UserLocation;
import com.project.safetyFence.location.dto.DailyDistanceResponseDto;
import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.location.strategy.LocationSaveStrategy;
import com.project.safetyFence.location.UserLocationRepository;
import com.project.safetyFence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final UserRepository userRepository;
    private final UserLocationRepository userLocationRepository;
    private final LocationSaveStrategy locationSaveStrategy; // 전략 패턴 적용

    @Async
    @Transactional
    public void saveLocationIfNeeded(LocationUpdateDto locationDto) {
        try {
            // 사용자 조회
            User user = userRepository.findByNumber(locationDto.getUserNumber());
            if (user == null) {
                log.warn("사용자를 찾을 수 없습니다: userNumber={}", locationDto.getUserNumber());
                return;
            }

            // 이전 위치 조회
            Optional<UserLocation> previousLocationOpt = userLocationRepository.findLatestByUser(user);
            UserLocation previousLocation = previousLocationOpt.orElse(null);

            // 전략 패턴 적용: 저장 여부 판단을 전략 객체에 위임
            if (locationSaveStrategy.shouldSave(previousLocation, locationDto)) {
                saveLocation(user, locationDto);
            }

        } catch (Exception e) {
            log.error("위치 저장 중 오류 발생: userNumber={}", locationDto.getUserNumber(), e);
        }
    }

    private void saveLocation(User user, LocationUpdateDto locationDto) {
        UserLocation userLocation = new UserLocation(
                user,
                BigDecimal.valueOf(locationDto.getLatitude()),
                BigDecimal.valueOf(locationDto.getLongitude())
        );

        user.addUserLocation(userLocation);
    }

    /**
     * DB에서 사용자의 최신 위치 조회
     * 캐시에 위치가 없을 때 fallback으로 사용
     *
     * @param userNumber 사용자 번호
     * @return 최신 위치 DTO (없으면 null)
     */
    @Transactional(readOnly = true)
    public LocationUpdateDto getLatestLocationFromDB(String userNumber) {
        User user = userRepository.findByNumber(userNumber);
        if (user == null) {
            log.debug("DB 위치 조회 실패: 사용자 없음 userNumber={}", userNumber);
            return null;
        }

        Optional<UserLocation> latestLocation = userLocationRepository.findLatestByUser(user);
        if (latestLocation.isEmpty()) {
            log.debug("DB 위치 조회 실패: 위치 기록 없음 userNumber={}", userNumber);
            return null;
        }

        UserLocation location = latestLocation.get();
        LocationUpdateDto dto = new LocationUpdateDto(
                location.getLatitude(),
                location.getLongitude()
        );
        dto.setUserNumber(userNumber);
        dto.setTimestamp(location.getSavedTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

        log.info("DB에서 마지막 위치 조회 성공: userNumber={}, lat={}, lng={}",
                userNumber, dto.getLatitude(), dto.getLongitude());

        return dto;
    }

    /**
     * 특정 날짜의 일일 이동거리 계산 (도보만)
     * 속도 기반 필터링으로 차량 이동 제외
     *
     * @param userNumber 사용자 번호
     * @param date 조회할 날짜
     * @return 일일 이동거리 DTO
     */
    @Transactional(readOnly = true)
    public DailyDistanceResponseDto calculateDailyDistance(String userNumber, LocalDate date) {
        User user = userRepository.findByNumber(userNumber);
        if (user == null) {
            log.warn("일일 이동거리 조회 실패: 사용자 없음 userNumber={}", userNumber);
            return new DailyDistanceResponseDto(userNumber, date, 0.0, 0);
        }

        // 해당 날짜의 시작과 끝
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // 위치 이력 조회 (시간순 정렬)
        List<UserLocation> locations = userLocationRepository
                .findByUserAndTimeRangeOrderByTimeAsc(user, startOfDay, endOfDay);

        if (locations.isEmpty()) {
            log.debug("일일 이동거리: 위치 기록 없음 userNumber={}, date={}", userNumber, date);
            return new DailyDistanceResponseDto(userNumber, date, 0.0, 0);
        }

        // 연속된 점들 사이 거리 합산 (속도 필터링 적용)
        double totalDistance = 0.0;
        int validSegments = 0;

        for (int i = 1; i < locations.size(); i++) {
            UserLocation prev = locations.get(i - 1);
            UserLocation curr = locations.get(i);

            double distance = calculateDistance(
                    prev.getLatitude(), prev.getLongitude(),
                    curr.getLatitude(), curr.getLongitude()
            );

            // 시간 차이 계산 (초)
            long timeDiffSeconds = Duration.between(prev.getSavedTime(), curr.getSavedTime()).getSeconds();

            if (timeDiffSeconds > 0) {
                double speedMps = distance / timeDiffSeconds;  // m/s
                double speedKmh = speedMps * 3.6;              // km/h

                // 도보 속도 범위만 포함 (최대 10 km/h = 빠른 걸음/조깅)
                if (speedKmh <= 10.0 && speedKmh >= 0.5) {
                    totalDistance += distance;
                    validSegments++;
                } else if (speedKmh > 10.0) {
                    log.trace("차량 이동으로 판단하여 제외: {}km/h, {}m",
                            String.format("%.1f", speedKmh), String.format("%.1f", distance));
                }
            }
        }

        log.info("일일 이동거리 계산 완료: userNumber={}, date={}, distance={}m, segments={}/{}",
                userNumber, date, String.format("%.1f", totalDistance), validSegments, locations.size() - 1);

        return new DailyDistanceResponseDto(userNumber, date, totalDistance, locations.size());
    }

    /**
     * Haversine 공식을 사용한 거리 계산 (미터)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS = 6371000; // 지구 반지름 (미터)

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}

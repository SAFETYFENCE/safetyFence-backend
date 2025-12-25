package com.project.safetyFence.location;

import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.location.domain.UserLocation;
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
import java.time.LocalDateTime;
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
}

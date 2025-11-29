package com.project.safetyFence.geofence;

import com.project.safetyFence.common.exception.CustomException;
import com.project.safetyFence.common.exception.ErrorResult;
import com.project.safetyFence.geofence.domain.Geofence;
import com.project.safetyFence.geofence.handler.GeofenceEntryHandler;
import com.project.safetyFence.geofence.service.InitialGeofenceCreator;
import com.project.safetyFence.user.domain.User;
import com.project.safetyFence.user.domain.UserAddress;
import com.project.safetyFence.geofence.dto.GeofenceRequestDto;
import com.project.safetyFence.geofence.GeofenceRepository;
import com.project.safetyFence.user.UserRepository;
import com.project.safetyFence.common.service.geocoding.GeocodingService;
import com.project.safetyFence.common.service.geocoding.dto.Coordinate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeofenceService implements InitialGeofenceCreator {

    private final GeofenceRepository geofenceRepository;
    private final GeocodingService geocodingService;
    private final UserRepository userRepository;
    private final List<GeofenceEntryHandler> entryHandlers; // 핸들러 리스트 주입

    @Override
    public Geofence createHomeGeofence(UserAddress userAddress) {
        String address = userAddress.getHomeStreetAddress();
        Coordinate coordinate = convertAddressOrThrow(address);

        Geofence geofence = new Geofence(
                userAddress.getUser(),
                "집",
                address,
                coordinate.getLatitude(),
                coordinate.getLongitude(),
                0,
                100
        );
        return geofence;
    }

    @Override
    public Geofence createCenterGeofence(UserAddress userAddress) {
        String address = userAddress.getCenterStreetAddress();
        Coordinate coordinate = convertAddressOrThrow(address);

        Geofence geofence = new Geofence(
                userAddress.getUser(),
                "센터",
                address,
                coordinate.getLatitude(),
                coordinate.getLongitude(),
                0,
                100
        );
        return geofence;
    }

    @Transactional(readOnly = true)
    public List<Geofence> findGeofenceByNumber(String userNumber) {
        User user = userRepository.findByNumber(userNumber);
        List<Geofence> geofences = geofenceRepository.findByUser(user);
        return geofences;
    }

    @Transactional
    public void userFenceIn(String userNumber, Long geofenceId) {
        User user = userRepository.findByNumberWithGeofences(userNumber);

        // 디버깅을 위한 로그
        List<Long> userGeofenceIds = user.getGeofences().stream()
                .map(Geofence::getId)
                .toList();

        Geofence geofence = user.getGeofences().stream()
                .filter(g -> g.getId().equals(geofenceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));

        // 전략 패턴 적용: 타입에 맞는 핸들러 찾아서 실행
        GeofenceEntryHandler handler = entryHandlers.stream()
                .filter(h -> h.supports(geofence.getType()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported geofence type: " + geofence.getType()));

        handler.handle(user, geofence);
    }

    @Transactional
    public void createNewFence(String userNumber, GeofenceRequestDto geofenceRequestDto) {
        User user = userRepository.findByNumber(userNumber);
        String address = geofenceRequestDto.getAddress();
        Coordinate coordinate = convertAddressOrThrow(address);

        Geofence geofence;
        if (geofenceRequestDto.getType() == 0) { // 영구 지오펜스
            geofence = new Geofence(
                    user,
                    geofenceRequestDto.getName(),
                    address,
                    coordinate.getLatitude(),
                    coordinate.getLongitude(),
                    0,
                    999
            );
        } else { // 일시 지오펜스
            // "09:00" 형식의 시간을 오늘 날짜와 결합하여 LocalDateTime으로 변환
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalTime startLocalTime = java.time.LocalTime.parse(geofenceRequestDto.getStartTime());
            java.time.LocalTime endLocalTime = java.time.LocalTime.parse(geofenceRequestDto.getEndTime());

            java.time.LocalDateTime startDateTime = java.time.LocalDateTime.of(today, startLocalTime);
            java.time.LocalDateTime endDateTime = java.time.LocalDateTime.of(today, endLocalTime);

            geofence = new Geofence(
                    user,
                    geofenceRequestDto.getName(),
                    address,
                    coordinate.getLatitude(),
                    coordinate.getLongitude(),
                    1,
                    startDateTime,
                    endDateTime,
                    100
            );
        }

        user.addGeofence(geofence);
        geofenceRepository.save(geofence);
    }

    @Transactional
    public void deleteFence(String userNumber, Long geofenceId) {
        User user = userRepository.findByNumberWithGeofences(userNumber);

        Geofence geofenceToDelete = user.getGeofences().stream()
                .filter(geofence -> geofence.getId().equals(geofenceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Geofence not found"));

        user.removeGeofence(geofenceToDelete);
        // orphanRemoval = true로 트랜잭션 종료 시 자동 삭제
    }

    private Coordinate convertAddressOrThrow(String address) {
        try {
            return geocodingService.convertAddressToCoordinate(address);
        } catch (RuntimeException e) {
            log.warn("지오코딩 실패 - address={}", address, e);
            throw new CustomException(ErrorResult.GEOFENCE_ADDRESS_CONVERSION_FAILED);
        }
    }

}

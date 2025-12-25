package com.project.safetyFence.location;

import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.location.LocationCacheService;
import com.project.safetyFence.location.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final LocationCacheService cacheService;
    private final LocationService locationService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * WebSocket 위치 업데이트
     * 클라이언트 → 서버: /app/location
     * 서버 → 구독자들: /topic/location/{userNumber}
     *
     * @param location 위치 정보 (lat, lng)
     * @param sessionAttributes WebSocket 세션 속성 (userNumber 포함)
     */
    @MessageMapping("/location")
    public void updateLocation(
            LocationUpdateDto location,
            @Header("simpSessionAttributes") Map<String, Object> sessionAttributes) {
        // 세션에서 사용자 번호 추출
        String userNumber = (String) sessionAttributes.get("userNumber");

        if (userNumber == null) {
            log.error("세션에 userNumber가 없습니다.");
            return;
        }

        // 공통 처리 로직 호출
        processLocationUpdate(userNumber, location);
    }

    /**
     * HTTP POST 위치 업데이트 (백그라운드 fallback)
     * 앱이 백그라운드 상태일 때 WebSocket 연결이 끊기면 HTTP POST로 위치 전송
     *
     * @param location 위치 정보 (lat, lng, userNumber)
     * @param userNumber 사용자 번호 (헤더)
     * @return 응답
     */
    @PostMapping("/location")
    public ResponseEntity<Void> updateLocationHttp(
            @RequestBody LocationUpdateDto location,
            @RequestHeader String userNumber) {

        if (userNumber == null || userNumber.isBlank()) {
            log.error("HTTP POST 위치 업데이트 실패: userNumber가 없습니다.");
            return ResponseEntity.badRequest().build();
        }

        log.debug("HTTP POST 위치 업데이트 수신 (백그라운드): userNumber={}, lat={}, lng={}",
                userNumber, location.getLatitude(), location.getLongitude());

        // 공통 처리 로직 호출
        processLocationUpdate(userNumber, location);

        return ResponseEntity.ok().build();
    }

    /**
     * 위치 업데이트 공통 처리 로직
     * WebSocket과 HTTP POST 모두 동일한 로직 사용
     *
     * @param userNumber 사용자 번호
     * @param location 위치 정보
     */
    private void processLocationUpdate(String userNumber, LocationUpdateDto location) {
        // DTO에 사용자 정보 설정
        location.setUserNumber(userNumber);
        location.setTimestamp(System.currentTimeMillis());

        log.debug("위치 업데이트 처리: userNumber={}, lat={}, lng={}",
                userNumber, location.getLatitude(), location.getLongitude());

        // 1. 캐시에 최신 위치 저장
        cacheService.updateLocation(userNumber, location);

        // 2. 해당 사용자를 구독 중인 모든 클라이언트에게 전송
        messagingTemplate.convertAndSend(
                "/topic/location/" + userNumber,
                location
        );

        // 3. 조건부 DB 저장 (비동기)
        locationService.saveLocationIfNeeded(location);
    }


}

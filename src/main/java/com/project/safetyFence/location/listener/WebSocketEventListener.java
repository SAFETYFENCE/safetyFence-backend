package com.project.safetyFence.location.listener;

import com.project.safetyFence.location.dto.LocationUpdateDto;
import com.project.safetyFence.location.LocationCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket 이벤트 리스너
 * 연결/해제 시 로그 기록 및 리소스 정리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final LocationCacheService cacheService;
    private final SimpMessagingTemplate messagingTemplate;

    private static final Pattern LOCATION_TOPIC_PATTERN = Pattern.compile("^/topic/location/([^/]+)$");

    /**
     * WebSocket 연결 이벤트
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = headerAccessor.getSessionId();
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String userNumber = (String) sessionAttributes.get("userNumber");
            log.info("WebSocket 연결됨: sessionId={}, userNumber={}", sessionId, userNumber);
        } else {
            log.info("WebSocket 연결됨: sessionId={}", sessionId);
        }
    }

    /**
     * WebSocket 구독 이벤트
     * 위치 토픽 구독 시 최신 위치를 즉시 전송 (캐시 → DB 폴백)
     */
    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String destination = headerAccessor.getDestination();
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (destination == null || sessionAttributes == null) {
            return;
        }

        // /topic/location/{userNumber} 패턴 매칭
        Matcher matcher = LOCATION_TOPIC_PATTERN.matcher(destination);
        if (matcher.matches()) {
            String targetUserNumber = matcher.group(1);
            String subscriberNumber = (String) sessionAttributes.get("userNumber");

            log.info("위치 토픽 구독 감지: subscriber={}, target={}, destination={}",
                    subscriberNumber, targetUserNumber, destination);

            // 캐시 → DB 폴백으로 최신 위치 조회
            LocationUpdateDto location = cacheService.getLatestLocationWithFallback(targetUserNumber);

            if (location != null) {
                // 토픽 구독자들에게 위치 전송
                messagingTemplate.convertAndSend(destination, location);

                log.info("위치 전송 완료: subscriber={}, target={}, lat={}, lng={}",
                        subscriberNumber, targetUserNumber,
                        location.getLatitude(), location.getLongitude());
            } else {
                log.warn("전송할 위치 데이터 없음: subscriber={}, target={}", subscriberNumber, targetUserNumber);
            }
        }
    }

    /**
     * WebSocket 연결 해제 이벤트
     * 캐시에서 사용자 위치 삭제
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = headerAccessor.getSessionId();
        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        if (sessionAttributes != null) {
            String userNumber = (String) sessionAttributes.get("userNumber");

            if (userNumber != null) {
                // 캐시에서 위치 정보 삭제
                cacheService.removeLocation(userNumber);
                log.info("WebSocket 연결 해제: sessionId={}, userNumber={}, 캐시 삭제 완료",
                        sessionId, userNumber);
            } else {
                log.info("WebSocket 연결 해제: sessionId={}", sessionId);
            }
        } else {
            log.info("WebSocket 연결 해제: sessionId={}", sessionId);
        }
    }
}

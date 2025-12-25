package com.project.safetyFence.location.config;

import com.project.safetyFence.link.LinkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final LinkService linkService;
    private static final Pattern LOCATION_TOPIC_PATTERN = Pattern.compile("^/topic/location/([^/]+)$");

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && accessor.getCommand() != null) {
            log.debug("[WS-AUTH] preSend command={}, destination={}, sessionId={}",
                    accessor.getCommand(),
                    accessor.getDestination(),
                    accessor.getSessionId());
        }

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // CONNECT 프레임 상세 로깅 (디버깅용)
            log.info("🔍 [CONNECT DEBUG] Received CONNECT frame");
            log.info("🔍 [CONNECT DEBUG] Command: {}", accessor.getCommand());
            log.info("🔍 [CONNECT DEBUG] All native headers: {}", accessor.toNativeHeaderMap());
            log.info("🔍 [CONNECT DEBUG] Message payload type: {}", message.getPayload().getClass());

            // WebSocket 연결 시 사용자 번호 추출
            String userNumber = accessor.getFirstNativeHeader("userNumber");

            if (userNumber == null || userNumber.isBlank()) {
                log.warn("WebSocket 연결 시도 실패: userNumber가 없습니다.");
                throw new IllegalArgumentException("userNumber는 필수입니다.");
            }

            // 세션 속성에 사용자 번호 저장
            accessor.getSessionAttributes().put("userNumber", userNumber);

            log.info("✅ WebSocket 연결 성공: userNumber={}, sessionId={}",
                    userNumber, accessor.getSessionId());
        } else if (accessor != null) {
            // CONNECT가 아닌 경우에도 userNumber가 세션에 있는지 확인
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null || sessionAttributes.get("userNumber") == null) {
                log.debug("[WS-AUTH] 세션에 userNumber 없음: command={}, sessionId={}",
                        accessor.getCommand(), accessor.getSessionId());
            }
        }

        // SUBSCRIBE 명령 시 권한 체크
        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();

            if (destination != null) {
                Matcher matcher = LOCATION_TOPIC_PATTERN.matcher(destination);
                if (matcher.matches()) {
                    String targetUserNumber = matcher.group(1);
                    Map<String, Object> sessionAttributes = accessor.getSessionAttributes();

                    if (sessionAttributes == null) {
                        log.error("구독 차단: 세션 속성 없음");
                        return null; // 메시지 차단
                    }

                    String subscriberNumber = (String) sessionAttributes.get("userNumber");
                    if (subscriberNumber == null) {
                        log.error("구독 차단: userNumber 없음");
                        return null; // 메시지 차단
                    }

                    // 권한 검증
                    if (!linkService.hasLink(subscriberNumber, targetUserNumber)) {
                        log.warn("구독 차단: subscriber={}, target={} (권한 없음)",
                                subscriberNumber, targetUserNumber);
                        return null; // 메시지 차단
                    }

                    log.info("구독 승인: subscriber={}, target={}, destination={}",
                            subscriberNumber, targetUserNumber, destination);
                } else {
                    log.debug("[WS-AUTH] SUBSCRIBE but destination not matched: {}", destination);
                }
            } else {
                log.debug("[WS-AUTH] SUBSCRIBE destination null: sessionId={}", accessor.getSessionId());
            }
        } else if (accessor != null && accessor.getCommand() == StompCommand.SEND) {
            log.debug("[WS-AUTH] SEND command received: destination={}, sessionId={}",
                    accessor.getDestination(), accessor.getSessionId());
        }

        return message;
    }
}

package com.project.safetyFence.location.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

/**
 * WebSocket 핸드셰이크 단계에서 헤더 정보를 확인하기 위한 로깅 인터셉터.
 * Sub-Protocol 및 클라이언트 정보를 확인해 STOMP 연결 문제를 디버깅할 때 사용한다.
 */
@Slf4j
@Component
public class WebSocketHandshakeLoggingInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        HttpHeaders headers = request.getHeaders();
        InetSocketAddress remoteAddress = request.getRemoteAddress();

        List<String> protocols = headers.get("Sec-WebSocket-Protocol");
        String upgrade = headers.getFirst("Upgrade");
        String connection = headers.getFirst("Connection");
        String userAgent = headers.getFirst("User-Agent");

        log.info("WebSocket Handshake 수신: uri={}, remote={}, upgrade={}, connection={}, protocols={}, user-agent={}",
                request.getURI(),
                remoteAddress,
                upgrade,
                connection,
                protocols,
                userAgent);

        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        if (exception != null) {
            log.warn("WebSocket Handshake 실패: uri={}, error={}", request.getURI(), exception.getMessage());
        } else {
            log.info("WebSocket Handshake 완료: uri={}", request.getURI());
        }
    }
}

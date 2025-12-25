package com.project.safetyFence.location.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final WebSocketHandshakeLoggingInterceptor handshakeLoggingInterceptor;

    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 클라이언트가 구독할 destination prefix
        // 예: /topic/location/123
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000})  // 10초마다 heartbeat (연결 유지)
                .setTaskScheduler(heartbeatScheduler());  // Heartbeat용 TaskScheduler

        // 클라이언트가 메시지를 보낼 때 사용할 prefix
        // 예: /app/location
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // React Native용 네이티브 WebSocket 엔드포인트
        registry.addEndpoint("/ws")
                .addInterceptors(handshakeLoggingInterceptor)
                .setAllowedOriginPatterns("*");  // 모든 origin 허용 (SockJS 제거)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 인증 인터셉터 등록
        registration.interceptors(webSocketAuthInterceptor);
    }
}

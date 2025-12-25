# React Native + Spring Boot WebSocket 연결에서 만난 두 가지 함정

> React Native와 Spring Boot 간 STOMP WebSocket 연결을 구현하면서 겪은 삽질기입니다. 백엔드 개발자 관점에서 정리했습니다.

---

## 들어가기 전에: WebSocket과 STOMP란?

### WebSocket

HTTP는 클라이언트가 요청하면 서버가 응답하는 **단방향** 통신입니다. 하지만 실시간 위치 공유처럼 서버가 먼저 데이터를 보내야 하는 경우엔 부적합합니다.

**WebSocket**은 한 번 연결하면 클라이언트와 서버가 **양방향으로 자유롭게** 데이터를 주고받을 수 있는 프로토콜입니다.

```
HTTP (단방향):
클라이언트 → 서버: "새 메시지 있어?"
서버 → 클라이언트: "없음"
(반복...)

WebSocket (양방향):
클라이언트 ↔ 서버: 연결 유지
서버 → 클라이언트: "새 메시지 왔어!" (서버가 먼저 보낼 수 있음)
```

### STOMP

WebSocket은 단순히 "데이터를 주고받는 파이프"일 뿐, **메시지 형식이나 라우팅 규칙이 없습니다**.

STOMP(Simple Text Oriented Messaging Protocol)는 WebSocket 위에서 동작하는 메시징 프로토콜로:

- **구독/발행 패턴**: `/topic/location/user123`을 구독하면 해당 메시지만 받음
- **프레임 구조**: CONNECT, SUBSCRIBE, SEND 등 명확한 명령어
- **헤더 지원**: 인증 정보 등 메타데이터를 함께 전송

```
┌─────────────────────────────────────────┐
│           STOMP 프레임 구조               │
├─────────────────────────────────────────┤
│ COMMAND (CONNECT, SEND, SUBSCRIBE 등)   │
│ header1:value1                          │
│ header2:value2                          │
│                                         │
│ body (선택)                              │
│ \0  ← null 문자로 프레임 종료            │
└─────────────────────────────────────────┘
```

---

## 프로젝트 구조

위치 공유 앱을 만들면서 실시간 위치 전송을 위해 WebSocket + STOMP를 선택했습니다.

```
┌─────────────────┐       ┌─────────┐       ┌──────────────┐
│  React Native   │       │  Nginx  │       │ Spring Boot  │
│  @stomp/stompjs │ ←───→ │  Proxy  │ ←───→ │   STOMP/WS   │
└─────────────────┘   WS  └─────────┘   WS  └──────────────┘
```

### 기술 스택

- **Frontend**: React Native + @stomp/stompjs
- **Backend**: Spring Boot 3.5 + Spring WebSocket + SimpleBroker
- **Infra**: Nginx (리버스 프록시) + Docker Compose

---

## 백엔드 아키텍처

### Spring Boot WebSocket 설정

```java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;

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
        // 구독 prefix: /topic/location/{userNumber}
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());

        // 메시지 전송 prefix: /app/location
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // STOMP 레벨 인증 인터셉터
        registration.interceptors(webSocketAuthInterceptor);
    }
}
```

**핵심 포인트:**
- `@EnableWebSocketMessageBroker`: STOMP 메시지 브로커 활성화
- `SimpleBroker`: 인메모리 메시지 브로커 (소규모 서비스용)
- `ChannelInterceptor`: STOMP 프레임 레벨에서 인증/인가 처리

### STOMP 인터셉터로 인증 처리

```java
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final LinkService linkService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message, StompHeaderAccessor.class);

        // 1. CONNECT 시 사용자 인증
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String userNumber = accessor.getFirstNativeHeader("userNumber");

            if (userNumber == null || userNumber.isBlank()) {
                throw new IllegalArgumentException("userNumber는 필수입니다.");
            }

            // 세션에 사용자 정보 저장
            accessor.getSessionAttributes().put("userNumber", userNumber);
            log.info("WebSocket 연결 성공: userNumber={}", userNumber);
        }

        // 2. SUBSCRIBE 시 권한 검증
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            // /topic/location/{targetUserNumber} 패턴 검증
            // 구독자가 대상 사용자의 위치를 볼 권한이 있는지 확인
            if (!hasPermission(accessor, destination)) {
                return null;  // 메시지 차단
            }
        }

        return message;
    }
}
```

**STOMP 레벨 인증의 장점:**
- HTTP 핸드셰이크가 아닌 **STOMP 프레임에서 인증 정보 추출**
- CONNECT 헤더로 사용자 식별 → 세션에 저장
- SUBSCRIBE 시 권한 검증으로 무단 구독 차단

### 위치 메시지 처리 컨트롤러

```java
@Controller
@RequiredArgsConstructor
public class LocationWebSocketController {

    private final LocationCacheService cacheService;
    private final LocationService locationService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 클라이언트 → 서버: /app/location
     * 서버 → 구독자들: /topic/location/{userNumber}
     */
    @MessageMapping("/location")
    public void updateLocation(
            LocationUpdateDto location,
            @Header("simpSessionAttributes") Map<String, Object> sessionAttributes) {

        String userNumber = (String) sessionAttributes.get("userNumber");

        // 1. 캐시에 최신 위치 저장 (빠른 조회용)
        cacheService.updateLocation(userNumber, location);

        // 2. 구독자들에게 브로드캐스트
        messagingTemplate.convertAndSend(
                "/topic/location/" + userNumber,
                location
        );

        // 3. DB에 조건부 저장 (비동기)
        locationService.saveLocationIfNeeded(location);
    }
}
```

**메시지 흐름:**
```
사용자 A (위치 전송)
    │
    │ SEND /app/location {lat, lng}
    ▼
┌─────────────────────────────┐
│ LocationWebSocketController │
│  1. 캐시 저장               │
│  2. /topic/location/A 전송  │
│  3. DB 저장 (비동기)        │
└─────────────────────────────┘
    │
    │ MESSAGE /topic/location/A
    ▼
구독자 B, C (위치 수신)
```

### 이벤트 리스너: 구독 시 초기 데이터 전송

```java
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final LocationCacheService cacheService;
    private final LocationService locationService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 구독 시작 시 최신 위치를 즉시 전송
     */
    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        // /topic/location/{userNumber} 구독 감지
        String targetUserNumber = extractUserNumber(destination);

        // 1. 캐시에서 조회
        LocationUpdateDto location = cacheService.getLatestLocation(targetUserNumber);

        // 2. 캐시에 없으면 DB에서 조회 (fallback)
        if (location == null) {
            location = locationService.getLatestLocationFromDB(targetUserNumber);
        }

        // 3. 즉시 전송
        if (location != null) {
            messagingTemplate.convertAndSend(destination, location);
        }
    }
}
```

**왜 필요한가?**
- 보호자가 구독을 시작하면 **즉시 현재 위치를 받아야 함**
- 새 위치가 올 때까지 기다리면 UX가 나빠짐
- 캐시 → DB 순서로 조회하여 응답 속도 최적화

---

## 문제 1: 82바이트 문제

### 증상

서버 로그에 계속 이 에러가 찍혔습니다:

```
Incomplete STOMP frame content received, bufferSize=82, bufferSizeLimit=65536
```

WebSocket 핸드셰이크는 성공하는데, STOMP CONNECT 프레임 파싱에서 실패하는 상황.

### 서버에서 본 것

Spring의 `StompDecoder`는 STOMP 프레임을 파싱할 때 **null terminator(`\0`)**를 찾습니다. 그런데 82바이트가 도착해도 `\0`이 없으니 "아직 프레임이 안 끝났네" 하고 계속 대기합니다.

STOMP 프레임 구조를 다시 보면:
```
CONNECT
userNumber:01012345678
accept-version:1.2,1.1,1.0
heart-beat:10000,10000

\0   ← 이게 83번째 바이트인데, 82바이트만 도착!
```

**서버 입장에서는 "클라이언트가 프레임을 다 안 보냈네"로 보임.**

### 원인: React Native 브릿지

클라이언트(React Native) 코드를 확인해보니 분명히 `\0`을 추가하고 있었습니다.

사실 이렇게 해결하는게 맞는지는 모르겠습니다. 너무 하드코딩해서 해결한 것 같은데... 

```typescript
data = data + '\0';  
return originalSend(data);
```

문제는 **React Native의 네이티브 브릿지**였습니다.

React Native는 JavaScript와 네이티브 코드(C/Objective-C) 사이에 브릿지가 있습니다. 문자열을 전달할 때 내부적으로 C 언어의 문자열 함수를 사용하는데, C에서 `\0`은 **문자열의 끝**을 의미합니다.

```
JavaScript: "CONNECT\n...헤더...\n\n\0" (83바이트)
     ↓ Native Bridge
C 함수: strlen() → \0을 종료자로 인식!
     ↓
실제 전송: 82바이트 (\0 제외)
```

### 해결: BINARY 프레임

TEXT 프레임(문자열)로 보내면 브릿지가 C 스타일로 처리되지만,

**BINARY 프레임(ArrayBuffer)**으로 보내면 이를 우회할 수 있습니다.

```typescript
// 문자열 → 바이트 배열 + \0 명시적 추가
const encoder = new TextEncoder();
const bytes = encoder.encode(data);
const withNull = new Uint8Array(bytes.length + 1);
withNull.set(bytes);
withNull[bytes.length] = 0;  // 바이트 0 추가

return originalSend(withNull.buffer);  // BINARY 프레임
```

**서버 측 변경 없음**: Spring의 `StompDecoder`는 TEXT, BINARY 프레임 모두 처리 가능합니다.

### 검증

수정 후 서버 로그:
```
✅ WebSocket 연결 성공: userNumber=01089099797
🔍 [CONNECT DEBUG] Received CONNECT frame
🔍 [CONNECT DEBUG] All native headers: {userNumber=[01089099797], ...}
```

---

## 문제 2: Heartbeat 관련

### 증상

CONNECT는 성공했는데 10~20초 후에 연결이 끊어졌습니다.

```
[서버 로그]
Failed to parse BinaryMessage payload=[...pos=2 lim=2 cap=2]
java.lang.IllegalArgumentException: No enum constant StompCommand.
```



`StompDecoder`가 들어온 메시지를 STOMP 프레임으로 파싱하려고 시도했는데, COMMAND를 찾을 수 없었습니다. `No enum constant StompCommand.`는 빈 문자열이나 알 수 없는 명령어가 왔다는 뜻입니다.

그런데 이상한 점:
- 클라이언트는 1바이트(`\n`)를 보냈다고 주장
- 서버는 2바이트 BINARY 메시지를 받았다고 함

### STOMP Heartbeat 이해

STOMP 1.2의 Heartbeat는 **STOMP 프레임이 아닙니다**. 그냥 `\n` 한 글자를 보내는 것입니다.

```
STOMP Heartbeat:
클라이언트 → 서버: \n (1바이트)
서버 → 클라이언트: \n (1바이트)
```

Spring의 `SimpleBroker`는 이 heartbeat를 정상적으로 처리해야 합니다. 그런데 왜 STOMP 프레임으로 파싱을 시도했을까요?

### 원인 

* 위 에러를 해결하면서 heartbeat도 BINARY로 보내도록 했습니다.
* 즉, `\n`을 BINARY 프레임으로 보냈습니다.

```


### 해결: Heartbeat는 TEXT로, STOMP 프레임은 BINARY로

문제 1에서 모든 데이터를 BINARY로 변환했는데, **heartbeat(`\n`)는 TEXT로 보내야 합니다**.

```typescript
const wrapAndSend = (data: any) => {
  if (typeof data === 'string') {
    // Heartbeat는 TEXT로 그대로 전송
    if (data === '\n' || data === '\r\n' || data.length <= 2) {
      console.log('[WS DEBUG] heartbeat LF 전송');
      return originalSend(data);  // TEXT 프레임
    }

    // 일반 STOMP 프레임은 BINARY로 전송 + \0
    const encoder = new TextEncoder();
    const bytes = encoder.encode(data);
    const withNull = new Uint8Array(bytes.length + 1);
    withNull.set(bytes);
    withNull[bytes.length] = 0;
    return originalSend(withNull.buffer);  // BINARY 프레임
  }
  return originalSend(data);
};
```

**핵심: 데이터 타입에 따라 다르게 처리**
- `\n` (heartbeat) → TEXT 프레임으로 전송
- STOMP 프레임 (CONNECT, SEND 등) → BINARY 프레임 + `\0`

**서버 설정 (heartbeat 활성화 유지):**
```java
config.enableSimpleBroker("/topic")
        .setHeartbeatValue(new long[]{10000, 10000})  // 10초 간격
        .setTaskScheduler(heartbeatScheduler());
```

```typescript
// 클라이언트도 heartbeat 활성화
heartbeatIncoming: 10000,
heartbeatOutgoing: 10000,
```

### 결과

Heartbeat를 유지하면서 안정적인 연결:
- 죽은 연결 빠른 감지 (10초 내)
- 백그라운드에서도 연결 유지
- STOMP 프레임 파싱 정상 동작

---

## 전체 동작 흐름

모든 문제를 해결한 후의 동작 흐름입니다:

```
┌────────────────┐         ┌─────────┐         ┌──────────────┐
│ React Native   │         │  Nginx  │         │ Spring Boot  │
└────────┬───────┘         └────┬────┘         └──────┬───────┘
         │                      │                     │
         │  1. WS Handshake     │                     │
         ├─────────────────────►│                     │
         │                      ├────────────────────►│
         │                      │  101 Switching      │
         │◄─────────────────────┤◄────────────────────┤
         │                      │                     │
         │  2. CONNECT (BINARY) │                     │
         │  [67,79,78,...,0]    │                     │
         ├─────────────────────►│────────────────────►│
         │                      │                     │ ✅ 83바이트
         │                      │                     │ \0 포함!
         │                      │                     │
         │                      │  CONNECTED          │
         │◄─────────────────────┤◄────────────────────┤
         │                      │                     │
         │  3. SUBSCRIBE        │                     │
         │  /topic/location/A   │                     │
         ├─────────────────────►│────────────────────►│
         │                      │                     │ 📍 구독 등록
         │                      │                     │
         │  4. 위치 전송 (SEND)   │                     │
         ├─────────────────────►│────────────────────►│
         │                      │                     │ 캐시 저장
         │                      │                     │ DB 저장(비동기)
         │                      │                     │
         │                      │  위치 브로드캐스트      │
         │◄─────────────────────┤◄────────────────────┤
         │                      │                     │
         ▼                      ▼                     ▼
    연결 유지                                      연결 유지
  (10초 Heartbeat로 상태 확인)
```

**핵심 포인트:**
- STOMP 프레임은 **BINARY**로, Heartbeat는 **TEXT**로 전송
- Heartbeat 유지 (10초) → 끊긴 연결 감지
- 구독 시 캐시/DB에서 최신 위치 즉시 전송

---

## Nginx 설정

```nginx
location = /ws {
    proxy_pass http://backend;
    proxy_http_version 1.1;

    # WebSocket 업그레이드 헤더
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;

    # 버퍼링 비활성화 (프레임 분할 방지!)
    proxy_buffering off;
    proxy_request_buffering off;

    # 긴 타임아웃 (24시간)
    proxy_read_timeout 86400s;
    proxy_send_timeout 86400s;
}
```

**`proxy_buffering off`가 중요한 이유:**
- Nginx가 데이터를 버퍼링하면 WebSocket 프레임이 분할될 수 있음
- STOMP 프레임이 쪼개져서 도착하면 파싱 실패

---

## 최종 코드 요약

### 백엔드 (Spring Boot)

```java
// WebSocketConfig.java - 핵심 설정
@Override
public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic")
            .setHeartbeatValue(new long[]{10000, 10000})
            .setTaskScheduler(heartbeatScheduler());
    config.setApplicationDestinationPrefixes("/app");
}

@Override
public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*");
}

@Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(webSocketAuthInterceptor);
}
```

### 프론트엔드 (React Native) - 핵심 변경

```typescript
this.client = new Client({
  webSocketFactory: () => {
    const socket = new WebSocket(wsUrl, ['v12.stomp', 'v11.stomp', 'v10.stomp']);
    (socket as any).binaryType = 'arraybuffer';

    const originalSend = socket.send.bind(socket);

    const wrapAndSend = (data: any) => {
      if (typeof data === 'string') {
        // Heartbeat는 TEXT로 그대로 전송
        if (data === '\n' || data === '\r\n' || data.length <= 2) {
          return originalSend(data);
        }

        // STOMP 프레임은 BINARY + \0
        const encoder = new TextEncoder();
        const bytes = encoder.encode(data);
        const withNull = new Uint8Array(bytes.length + 1);
        withNull.set(bytes);
        withNull[bytes.length] = 0;
        return originalSend(withNull.buffer);
      }
      return originalSend(data);
    };

    (socket as any).send = wrapAndSend;
    return socket;
  },
  heartbeatIncoming: 10000,  // heartbeat 유지
  heartbeatOutgoing: 10000,
});
```

---

## 배운 점

### 1. STOMP 프레임 구조를 이해하자

STOMP 프레임은 반드시 `\0`으로 끝나야 합니다. 서버 로그에서 `Incomplete STOMP frame`이 보이면 **null terminator 누락**을 의심하세요.

### 2. 레이어별 디버깅

문제가 발생하면 각 레이어에서 확인:
```
React Native (JavaScript)
    ↓ 네이티브 브릿지
    ↓
Nginx (리버스 프록시)
    ↓
Spring Boot (StompDecoder)
```

이번 경우 문제는 **네이티브 브릿지**에서 발생했습니다.

### 3. 라이브러리 호환성 확인

@stomp/stompjs는 브라우저용으로 설계되었습니다. React Native에서 사용할 때는 추가 작업이 필요할 수 있습니다.

---

## 마무리

* 사실 해당 내용 말고도 수많은 수정이 있습니다. 
* 그래도 나머지는 바로바로 이해하고 수정한 것에 비해
* 해당 문제는 너무 공부도 안하고 진행해서 쓸데없이 애를 먹었습니다.
* 다음부터는 꼭 기술 사용하기 전에 공부하고 사용하도록 하겠습니다.

# WebSocket 트러블슈팅

React Native + Spring Boot 간 STOMP WebSocket 연결에서 발생한 문제와 해결 과정을 정리합니다.

---

## 시스템 구성

```
┌─────────────────┐       ┌─────────┐       ┌──────────────┐
│  React Native   │       │  Nginx  │       │ Spring Boot  │
│  @stomp/stompjs │ ←───→ │  Proxy  │ ←───→ │   STOMP/WS   │
└─────────────────┘   WS  └─────────┘   WS  └──────────────┘
```

---

## 문제 1: 82바이트 문제 (STOMP null terminator 누락)

### 증상

서버 로그에 반복적으로 에러 발생:

```
Incomplete STOMP frame content received, bufferSize=82, bufferSizeLimit=65536
```

WebSocket 핸드셰이크는 성공하지만 STOMP CONNECT 프레임 파싱에서 실패.

### 원인 분석

STOMP 프레임은 반드시 `\0` (null terminator)으로 끝나야 합니다.

```
CONNECT
userNumber:01012345678
accept-version:1.2,1.1,1.0
heart-beat:10000,10000

\0   ← 83번째 바이트인데, 82바이트만 도착
```

Spring의 `StompDecoder`가 `\0`을 찾지 못해 "프레임이 아직 완성되지 않았다"고 판단.

**근본 원인: React Native 네이티브 브릿지**

React Native는 JavaScript와 네이티브 코드(C/Objective-C) 사이에 브릿지가 있습니다.
문자열을 전달할 때 내부적으로 C 언어의 `strlen()` 함수를 사용하는데, C에서 `\0`은 **문자열 종료자**입니다.

```
JavaScript: "CONNECT\n...헤더...\n\n\0" (83바이트)
     ↓ Native Bridge
C 함수: strlen() → \0을 종료자로 인식
     ↓
실제 전송: 82바이트 (\0이 잘림)
```

### 해결: BINARY 프레임으로 전송

TEXT 프레임(문자열)은 브릿지가 C 스타일로 처리하지만,
BINARY 프레임(ArrayBuffer)은 바이트 배열 그대로 전달됩니다.

```typescript
// 문자열 → 바이트 배열 + \0 명시적 추가
const encoder = new TextEncoder();
const bytes = encoder.encode(data);
const withNull = new Uint8Array(bytes.length + 1);
withNull.set(bytes);
withNull[bytes.length] = 0;  // null terminator

return originalSend(withNull.buffer);  // BINARY 프레임
```

서버 측 변경 없음: Spring의 `StompDecoder`는 TEXT/BINARY 프레임 모두 처리 가능.

### 검증

```
✅ WebSocket 연결 성공: userNumber=01089099797
🔍 [CONNECT DEBUG] Received CONNECT frame
🔍 [CONNECT DEBUG] All native headers: {userNumber=[01089099797], ...}
```

---

## 문제 2: Heartbeat TEXT/BINARY 분기 처리

### 증상

CONNECT 성공 후 10~20초 뒤 연결 끊김:

```
Failed to parse BinaryMessage payload=[...pos=2 lim=2 cap=2]
java.lang.IllegalArgumentException: No enum constant StompCommand.
```

### 원인 분석

STOMP 1.2의 Heartbeat는 STOMP 프레임이 아닌 단순한 `\n` (1바이트)입니다.

```
STOMP Heartbeat:
클라이언트 → 서버: \n (1바이트)
서버 → 클라이언트: \n (1바이트)
```

문제 1을 해결하면서 **모든 데이터를 BINARY로 변환**했는데, heartbeat `\n`까지 BINARY로 전송된 것이 원인.
Spring의 `StompDecoder`가 BINARY로 도착한 heartbeat를 STOMP 프레임으로 파싱하려 시도하여 실패.

### 해결: 데이터 타입에 따라 분기

```typescript
const wrapAndSend = (data: any) => {
  if (typeof data === 'string') {
    // Heartbeat는 TEXT로 그대로 전송
    if (data === '\n' || data === '\r\n' || data.length <= 2) {
      return originalSend(data);  // TEXT 프레임
    }

    // 일반 STOMP 프레임은 BINARY + \0
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

| 데이터 | 전송 방식 | 이유 |
|--------|-----------|------|
| Heartbeat (`\n`) | TEXT 프레임 | StompDecoder가 TEXT heartbeat만 정상 처리 |
| STOMP 프레임 (CONNECT, SEND 등) | BINARY 프레임 + `\0` | 네이티브 브릿지의 null terminator 잘림 방지 |

---

## Nginx WebSocket 프록시 설정

```nginx
location = /ws {
    proxy_pass http://backend;
    proxy_http_version 1.1;

    # WebSocket 업그레이드 헤더
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;

    # 버퍼링 비활성화 (프레임 분할 방지)
    proxy_buffering off;
    proxy_request_buffering off;

    # 긴 타임아웃 (24시간)
    proxy_read_timeout 86400s;
    proxy_send_timeout 86400s;
}
```

`proxy_buffering off`가 중요한 이유:
- Nginx가 데이터를 버퍼링하면 WebSocket 프레임이 분할될 수 있음
- STOMP 프레임이 쪼개져서 도착하면 서버에서 파싱 실패

---

## 전체 동작 흐름

모든 문제를 해결한 후의 정상 동작 흐름:

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
         │                      │                     │ \0 포함
         │                      │                     │
         │                      │  CONNECTED          │
         │◄─────────────────────┤◄────────────────────┤
         │                      │                     │
         │  3. SUBSCRIBE        │                     │
         │  /topic/location/A   │                     │
         ├─────────────────────►│────────────────────►│
         │                      │                     │ 구독 등록
         │                      │                     │
         │  4. SEND (BINARY)    │                     │
         │  위치 데이터           │                     │
         ├─────────────────────►│────────────────────►│
         │                      │                     │ 캐시 저장
         │                      │                     │ DB 저장 (비동기)
         │                      │                     │
         │                      │  MESSAGE            │
         │                      │  위치 브로드캐스트      │
         │◄─────────────────────┤◄────────────────────┤
         │                      │                     │
         │  5. Heartbeat (TEXT) │                     │
         │  \n (1바이트)         │                     │
         │◄────────────────────►│◄───────────────────►│
         │                      │  10초 간격 유지       │
         │                      │                     │
```

---

## 핵심 교훈

1. **STOMP 프레임 구조를 이해하자**: `\0` null terminator 누락 시 `Incomplete STOMP frame` 에러 발생
2. **레이어별 디버깅**: JavaScript → 네이티브 브릿지 → Nginx → Spring Boot, 각 레이어에서 데이터 확인
3. **라이브러리 호환성**: `@stomp/stompjs`는 브라우저용으로 설계됨. React Native에서는 네이티브 브릿지 차이를 고려해야 함

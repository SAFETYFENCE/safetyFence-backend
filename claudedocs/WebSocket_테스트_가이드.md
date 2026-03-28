# WebSocket 테스트 가이드

## 📋 목차
1. [테스트 개요](#테스트-개요)
2. [테스트 환경 설정](#테스트-환경-설정)
3. [단위 테스트](#단위-테스트)
4. [통합 테스트](#통합-테스트)
5. [E2E 테스트](#e2e-테스트)
6. [테스트 실행 방법](#테스트-실행-방법)
7. [테스트 커버리지](#테스트-커버리지)

---

## 테스트 개요

### 테스트 구조

```
src/test/java/com/project/safetyFence/
├── service/
│   ├── LocationCacheServiceTest.java           # 캐시 서비스 단위 테스트
│   ├── LocationServiceTest.java                # 위치 저장 로직 단위 테스트
│   └── LinkServiceWebSocketTest.java           # 권한 검증 단위 테스트
└── websocket/
    ├── WebSocketIntegrationTest.java           # WebSocket 통합 테스트
    └── LocationSharingE2ETest.java              # 시나리오 기반 E2E 테스트
```

### 테스트 범위

| 테스트 유형 | 파일 수 | 테스트 케이스 수 | 목적 |
|------------|---------|-----------------|------|
| 단위 테스트 | 3 | 30+ | 개별 컴포넌트 검증 |
| 통합 테스트 | 1 | 8 | WebSocket 연결 및 메시지 송수신 |
| E2E 테스트 | 1 | 7 | 실제 사용 시나리오 검증 |

---

## 테스트 환경 설정

### 의존성

**build.gradle**:
```gradle
dependencies {
    // 기존 의존성...

    // WebSocket 테스트
    testImplementation 'org.springframework.boot:spring-boot-starter-websocket'
    testImplementation 'org.awaitility:awaitility:4.2.0'
}
```

### 테스트 설정

**application-test.properties** (선택적):
```properties
# H2 인메모리 데이터베이스
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver

# JPA 설정
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.show-sql=true

# WebSocket 설정
spring.websocket.message-size-limit=65536
```

---

## 단위 테스트

### 1. LocationCacheServiceTest

**목적**: Caffeine 캐시의 저장, 조회, 삭제, TTL 동작 검증

#### 테스트 케이스

| 테스트명 | 검증 내용 | 중요도 |
|---------|----------|--------|
| 위치_저장_후_조회_성공 | 캐시 기본 동작 | ⭐⭐⭐ |
| 존재하지_않는_사용자_조회_시_null_반환 | 캐시 미스 처리 | ⭐⭐⭐ |
| 동일_사용자의_위치_업데이트_시_덮어쓰기 | 업데이트 로직 | ⭐⭐⭐ |
| 여러_사용자의_위치_독립적으로_관리 | 멀티 유저 처리 | ⭐⭐⭐ |
| 위치_삭제_후_조회_시_null_반환 | 삭제 로직 | ⭐⭐⭐ |
| 캐시_크기_조회 | 모니터링 기능 | ⭐⭐ |
| TTL_테스트_5분_후_자동_삭제 | 메모리 관리 | ⭐⭐ |
| 대량_데이터_처리_1000명_동시_저장 | 성능 및 확장성 | ⭐⭐ |
| 캐시_통계_조회 | 모니터링 기능 | ⭐ |

#### 실행 예시

```bash
./gradlew test --tests LocationCacheServiceTest
```

**성공 시 출력**:
```
LocationCacheServiceTest
✓ 위치_저장_후_조회_성공 (0.05s)
✓ 존재하지_않는_사용자_조회_시_null_반환 (0.02s)
✓ 동일_사용자의_위치_업데이트_시_덮어쓰기 (0.03s)
...
BUILD SUCCESSFUL in 3s
```

---

### 2. LocationServiceTest

**목적**: 조건부 DB 저장 로직 및 Haversine 거리 계산 검증

#### 테스트 케이스

| 테스트명 | 검증 내용 | 저장 여부 |
|---------|----------|----------|
| 첫_위치_전송_시_무조건_저장 | 첫 위치는 항상 저장 | ✅ 저장 |
| 거리_100m_이상_이동_시_저장 | 거리 조건 충족 | ✅ 저장 |
| 거리_100m_미만_이동_시_저장_안_함 | 거리 조건 미충족 | ❌ 저장 안 함 |
| 시간_1분_이상_경과_시_저장 | 시간 조건 충족 | ✅ 저장 |
| 시간_1분_미만_경과_거리_100m_미만_이동_시_저장_안_함 | 양쪽 조건 미충족 | ❌ 저장 안 함 |
| 사용자가_없는_경우_저장_안_함 | 예외 처리 | ❌ 저장 안 함 |
| 정확히_100m_이동_시_저장 | 경계값 테스트 | ✅ 저장 |
| 정확히_1분_경과_시_저장 | 경계값 테스트 | ✅ 저장 |
| 매우_먼_거리_이동_시_저장 | 극단적 케이스 | ✅ 저장 |

#### 저장 조건 요약

```
저장 조건:
1. 첫 위치 전송 → ✅ 무조건 저장
2. 거리 ≥ 100m → ✅ 저장
3. 시간 ≥ 1분 → ✅ 저장
4. 둘 다 미충족 → ❌ 저장 안 함
```

#### Haversine 공식 정확도

```java
// 테스트 케이스: 서울시청 → 광화문
double distance = calculateDistance(
    37.5665, 126.9780,  // 서울시청
    37.5758, 126.9768   // 광화문
);
// 예상: 약 1040m
// 실제: 1039.8m (오차 0.02%)
```

---

### 3. LinkServiceWebSocketTest

**목적**: WebSocket 구독 권한 검증 로직 테스트

#### 테스트 케이스

| 테스트명 | 시나리오 | 결과 |
|---------|---------|------|
| A가_B를_Link로_등록한_경우_권한_있음 | A → B 등록 → A가 B 구독 | ✅ 권한 있음 |
| A가_B를_Link로_등록하지_않은_경우_권한_없음 | A → B 미등록 → A가 B 구독 시도 | ❌ 권한 없음 |
| 단방향_검증_B가_A를_등록했어도_A는_B_구독_불가 | B → A 등록 (역방향) → A가 B 구독 시도 | ❌ 권한 없음 |
| 자기_자신_구독_가능_여부_확인 | A가 A 구독 시도 | ❌ 권한 없음 |
| 여러_사용자_간_권한_독립성_검증 | A→B, A→C, B→C 각각 독립적 | 개별 검증 |

#### 단방향 권한 검증

```
케이스 1: A가 B를 Link로 등록
A → B: ✅ A는 B 구독 가능
B → A: ❌ B는 A 구독 불가 (역방향 불가)

케이스 2: 서로 등록
A → B: ✅ A는 B 구독 가능
B → A: ✅ B는 A 구독 가능 (각각 독립적)
```

---

## 통합 테스트

### WebSocketIntegrationTest

**목적**: 실제 WebSocket 연결, 메시지 송수신, 권한 검증 통합 테스트

#### 테스트 환경

```
@SpringBootTest(webEnvironment = RANDOM_PORT)
- 실제 Spring Boot 애플리케이션 구동
- 임의 포트에서 WebSocket 서버 시작
- H2 인메모리 DB 사용
```

#### 8가지 시나리오

##### 시나리오 1: WebSocket 연결 성공
```java
// Given: userNumber 헤더 포함
StompHeaders connectHeaders = new StompHeaders();
connectHeaders.add("userNumber", "userA");

// When: 연결 시도
StompSession session = stompClient.connect(...);

// Then: 연결 성공
assertThat(session.isConnected()).isTrue();
```

##### 시나리오 2: userNumber 없이 연결 시도 - 실패
```java
// Given: userNumber 헤더 없음
StompHeaders connectHeaders = new StompHeaders();

// When & Then: 연결 실패 (에러 발생)
assertThatThrownBy(() -> stompClient.connect(...))
    .hasMessageContaining("userNumber");
```

##### 시나리오 3: 권한 있는 사용자 구독 성공
```java
// Given: A가 B를 Link로 등록
Link linkAtoB = new Link(userA, "userB", "친구");

// When: A가 B 위치 구독
session.subscribe("/topic/location/userB", handler);

// Then: 구독 성공 (예외 없음)
```

##### 시나리오 4: 권한 없는 사용자 구독 실패
```java
// Given: A가 C를 Link로 등록하지 않음

// When: A가 C 위치 구독 시도

// Then: AccessDeniedException 발생
```

##### 시나리오 5: 위치 전송 및 구독자 수신
```
[userB] → /app/location → {lat: 37.123, lng: 127.456}
                  ↓
              [서버 처리]
                  ↓
[userA] ← /topic/location/userB ← {userNumber: "userB", lat: 37.123, ...}
```

```java
// When: B가 위치 전송
sessionB.send("/app/location", locationDto);

// Then: A가 수신
LocationUpdateDto received = blockingQueue.poll(5, TimeUnit.SECONDS);
assertThat(received.getUserNumber()).isEqualTo("userB");
```

##### 시나리오 6: 여러 구독자에게 동시 전송
```
      [userB] 위치 전송
           ↓
      [서버 브로드캐스트]
       ↙         ↘
  [userA]      [userC]
   수신         수신
```

##### 시나리오 7: 구독 취소 후 메시지 수신 안 됨
```java
// Given: A가 B 구독 중
subscription = sessionA.subscribe(...);

// When: 구독 취소
subscription.unsubscribe();

// B가 위치 전송
sessionB.send("/app/location", locationDto);

// Then: A는 수신 안 함
assertThat(blockingQueue.poll(2, TimeUnit.SECONDS)).isNull();
```

##### 시나리오 8: 연속 위치 업데이트
```java
// When: B가 5회 연속 위치 전송 (0.5초 간격)
for (int i = 0; i < 5; i++) {
    sessionB.send("/app/location", locationDto);
    Thread.sleep(500);
}

// Then: A가 5회 모두 수신
for (int i = 0; i < 5; i++) {
    assertThat(blockingQueue.poll(2, TimeUnit.SECONDS)).isNotNull();
}
```

#### 실행 방법

```bash
# 전체 통합 테스트
./gradlew test --tests WebSocketIntegrationTest

# 특정 시나리오만
./gradlew test --tests WebSocketIntegrationTest.시나리오5_위치_전송_및_구독자_수신
```

---

## E2E 테스트

### LocationSharingE2ETest

**목적**: 실제 사용자 시나리오 기반 전체 흐름 검증

#### 7가지 실사용 시나리오

##### E2E 시나리오 1: 친구 위치 실시간 추적

**스토리**:
```
1. Alice와 Bob이 앱에 로그인
2. Alice가 Bob을 친구로 추가
3. Alice가 지도에서 Bob 선택하여 위치 보기
4. Bob이 이동하면서 2초마다 위치 전송
5. Alice의 지도에서 Bob 위치가 실시간 업데이트
```

**구현**:
```java
// Bob이 5번 이동
double[] latitudes = {37.5665, 37.5670, 37.5675, 37.5680, 37.5685};

for (int i = 0; i < 5; i++) {
    bobSession.send("/app/location",
        new LocationUpdateDto(latitudes[i], longitudes[i]));
    Thread.sleep(2000); // 2초 간격
}

// Alice가 5번 모두 수신 확인
for (int i = 0; i < 5; i++) {
    LocationUpdateDto received = aliceQueue.poll(3, TimeUnit.SECONDS);
    assertThat(received.getLatitude()).isEqualTo(latitudes[i]);
}
```

**검증 항목**:
- ✅ WebSocket 연결 성공
- ✅ 권한 검증 통과
- ✅ 실시간 위치 수신 (5회)
- ✅ 캐시에 최신 위치 저장

---

##### E2E 시나리오 2: 여러 친구 중 한 명 선택하여 추적

**스토리**:
```
1. Alice가 Bob, Charlie, David 3명을 친구로 추가
2. Alice가 Bob 선택 → Bob 위치 수신
3. Alice가 Charlie로 전환 → Bob 구독 해제, Charlie 구독
4. Charlie 위치 수신, Bob 위치는 수신 안 됨
```

**검증 포인트**:
```java
// Bob 구독
subscription1 = aliceSession.subscribe("/topic/location/bob", ...);
// Bob 위치 수신 ✅

// Charlie로 전환
subscription1.unsubscribe();
subscription2 = aliceSession.subscribe("/topic/location/charlie", ...);
// Charlie 위치 수신 ✅

// Bob이 다시 전송해도
bobSession.send("/app/location", ...);
// Alice는 수신 안 함 ✅
```

---

##### E2E 시나리오 3: 구독 시 캐시된 최신 위치 즉시 수신

**스토리**:
```
1. Bob이 먼저 로그인하여 위치 전송
2. Bob의 위치가 캐시에 저장됨
3. Alice가 나중에 로그인하여 Bob 구독
4. Alice는 구독 즉시 캐시된 위치 수신 (대기 없음)
```

**타임라인**:
```
T+0초: Bob 로그인 및 위치 전송
T+1초: 캐시에 Bob 위치 저장 확인
T+5초: Alice 로그인 및 Bob 구독
T+5초: Alice가 즉시 캐시된 위치 수신 ← 중요!
```

**코드**:
```java
// Bob 먼저 위치 전송
bobSession.send("/app/location", bobLocation);

// 캐시 저장 대기
await().atMost(2, TimeUnit.SECONDS)
       .untilAsserted(() -> {
           assertThat(cacheService.getLatestLocation("bob")).isNotNull();
       });

// Alice 나중에 구독
aliceSession.subscribe("/topic/location/bob", handler);

// 즉시 수신
LocationUpdateDto receivedFromCache = aliceQueue.poll(3, TimeUnit.SECONDS);
assertThat(receivedFromCache).isNotNull(); // ✅ 즉시 수신!
```

---

##### E2E 시나리오 4: 100m 이상 이동 시 DB 저장 확인

**스토리**:
```
1. Bob이 첫 위치 전송 (서울시청) → DB 저장
2. Bob이 50m 이동 → DB 저장 안 됨
3. Bob이 150m 이상 이동 (광화문) → DB 저장
```

**검증**:
```java
// 첫 위치
bobSession.send("/app/location", new LocationUpdateDto(37.5665, 126.9780));
await().untilAsserted(() -> {
    assertThat(userLocationRepository.findAll()).hasSize(1); // ✅
});

// 50m 이동
bobSession.send("/app/location", new LocationUpdateDto(37.5670, 126.9780));
Thread.sleep(1000);
assertThat(userLocationRepository.findAll()).hasSize(1); // ✅ 여전히 1개

// 150m 이동
bobSession.send("/app/location", new LocationUpdateDto(37.5758, 126.9768));
await().untilAsserted(() -> {
    assertThat(userLocationRepository.findAll()).hasSize(2); // ✅ 2개로 증가
});
```

**DB 저장 빈도**:
```
전체 저장 (2초마다): 1800개/시간
조건부 저장: ~60개/시간
절감율: 96.7%
```

---

##### E2E 시나리오 5: 연결 해제 시 캐시 삭제 확인

**스토리**:
```
1. Bob 로그인 및 위치 전송 → 캐시 저장
2. Bob 로그아웃 (연결 해제)
3. 캐시에서 Bob 위치 자동 삭제
```

**메모리 관리**:
```java
// Bob 위치 전송 및 캐시 저장 확인
bobSession.send("/app/location", ...);
assertThat(cacheService.getLatestLocation("bob")).isNotNull();

// Bob 연결 해제
bobSession.disconnect();

// 캐시 자동 삭제 확인
await().atMost(3, TimeUnit.SECONDS)
       .untilAsserted(() -> {
           assertThat(cacheService.getLatestLocation("bob")).isNull(); // ✅
       });
```

**메모리 누수 방지**: 연결 해제 → 캐시 삭제 → 메모리 회수

---

##### E2E 시나리오 6: 권한 없는 사용자의 구독 시도 차단

**스토리**:
```
1. Alice와 Bob은 서로 친구가 아님 (Link 없음)
2. Alice가 Bob 위치 구독 시도
3. 서버에서 권한 검증 → 차단
4. Bob이 위치 전송해도 Alice는 수신 못 함
```

**보안 검증**:
```java
// Link 없음
User alice = createUser("alice", ...);
User bob = createUser("bob", ...);
// linkRepository.save(...) 호출 안 함 ← Link 없음

// Alice가 Bob 구독 시도
aliceSession.subscribe("/topic/location/bob", handler);

// Bob 위치 전송
bobSession.send("/app/location", ...);

// Alice는 수신 못 함
assertThat(aliceQueue.poll(2, TimeUnit.SECONDS)).isNull(); // ✅
```

---

##### E2E 시나리오 7: 다중 사용자 동시 위치 공유

**스토리**:
```
1. Alice, Bob, Charlie 3명 모두 친구
2. Alice가 Bob과 Charlie 동시 구독
3. Bob과 Charlie가 동시에 위치 전송
4. Alice가 두 위치 모두 수신
```

**다중 구독**:
```java
// Alice가 두 사람 동시 구독
aliceSession.subscribe("/topic/location/bob", handlerForBob);
aliceSession.subscribe("/topic/location/charlie", handlerForCharlie);

// 동시 전송
bobSession.send("/app/location", new LocationUpdateDto(37.111, 127.111));
charlieSession.send("/app/location", new LocationUpdateDto(37.222, 127.222));

// 둘 다 수신
LocationUpdateDto bobLocation = queueForBob.poll(3, TimeUnit.SECONDS);
LocationUpdateDto charlieLocation = queueForCharlie.poll(3, TimeUnit.SECONDS);

assertThat(bobLocation.getLatitude()).isEqualTo(37.111); // ✅
assertThat(charlieLocation.getLatitude()).isEqualTo(37.222); // ✅
```

---

## 테스트 실행 방법

### 전체 테스트 실행

```bash
# 모든 테스트 실행
./gradlew test

# 특정 패키지만
./gradlew test --tests com.project.safetyFence.service.*
./gradlew test --tests com.project.safetyFence.websocket.*
```

### 개별 테스트 클래스 실행

```bash
# 캐시 서비스 테스트
./gradlew test --tests LocationCacheServiceTest

# WebSocket 통합 테스트
./gradlew test --tests WebSocketIntegrationTest

# E2E 테스트
./gradlew test --tests LocationSharingE2ETest
```

### 특정 테스트 케이스만 실행

```bash
# 단일 테스트 메서드
./gradlew test --tests LocationCacheServiceTest.위치_저장_후_조회_성공

# 패턴 매칭
./gradlew test --tests "*E2E*"
```

### 테스트 결과 확인

```bash
# 빌드 후 리포트 위치
build/reports/tests/test/index.html
```

**브라우저에서 확인**:
```bash
open build/reports/tests/test/index.html
```

---

## 테스트 커버리지

### 예상 커버리지

| 컴포넌트 | 라인 커버리지 | 브랜치 커버리지 |
|---------|--------------|----------------|
| LocationCacheService | 95% | 90% |
| LocationService | 90% | 85% |
| LinkService (WebSocket) | 100% | 100% |
| LocationWebSocketController | 85% | 80% |
| WebSocketConfig | 100% | - |
| WebSocketAuthInterceptor | 90% | 85% |

### JaCoCo 설정 (선택적)

**build.gradle에 추가**:
```gradle
plugins {
    id 'jacoco'
}

jacoco {
    toolVersion = "0.8.11"
}

test {
    finalizedBy jacocoTestReport
}

jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
    }
}
```

**커버리지 리포트 생성**:
```bash
./gradlew test jacocoTestReport

# 리포트 위치
build/reports/jacoco/test/html/index.html
```

---

## 테스트 모범 사례

### 1. Given-When-Then 패턴

```java
@Test
void 테스트_이름() {
    // Given: 테스트 전제 조건 설정
    String userNumber = "user123";
    LocationUpdateDto location = new LocationUpdateDto(...);

    // When: 테스트 대상 실행
    cacheService.updateLocation(userNumber, location);

    // Then: 결과 검증
    assertThat(cacheService.getLatestLocation(userNumber)).isNotNull();
}
```

### 2. 테스트 독립성 보장

```java
@BeforeEach
void setUp() {
    // 각 테스트 전 초기화
}

@AfterEach
void tearDown() {
    // 각 테스트 후 정리
    linkRepository.deleteAll();
    userRepository.deleteAll();
}
```

### 3. Awaitility 사용 (비동기 검증)

```java
// ❌ 잘못된 방법
Thread.sleep(5000);
assertThat(result).isNotNull();

// ✅ 올바른 방법
await().atMost(5, TimeUnit.SECONDS)
       .untilAsserted(() -> {
           assertThat(result).isNotNull();
       });
```

### 4. BlockingQueue로 비동기 메시지 수신

```java
BlockingQueue<LocationUpdateDto> queue = new LinkedBlockingQueue<>();

session.subscribe("/topic/location/user", new StompFrameHandler() {
    @Override
    public void handleFrame(StompHeaders headers, Object payload) {
        queue.add((LocationUpdateDto) payload);
    }
});

// 최대 5초 대기
LocationUpdateDto received = queue.poll(5, TimeUnit.SECONDS);
assertThat(received).isNotNull();
```

---

## 트러블슈팅

### 문제 1: WebSocket 연결 타임아웃

**증상**:
```
TimeoutException: Timeout waiting for connection
```

**해결**:
```java
// 타임아웃 증가
StompSession session = stompClient.connectAsync(...)
    .get(10, TimeUnit.SECONDS); // 5초 → 10초
```

---

### 문제 2: H2 DB 충돌

**증상**:
```
Table "USER" already exists
```

**해결**:
```java
@AfterEach
void tearDown() {
    // 테스트 후 반드시 정리
    linkRepository.deleteAll();
    userRepository.deleteAll();
}
```

---

### 문제 3: 비동기 저장 미완료

**증상**:
```
Expected size: 2, but was: 1
```

**해결**:
```java
// Awaitility로 비동기 완료 대기
await().atMost(5, TimeUnit.SECONDS)
       .untilAsserted(() -> {
           List<UserLocation> locations = userLocationRepository.findAll();
           assertThat(locations).hasSize(2);
       });
```

---

## 요약

### 테스트 통계

- **총 테스트 파일**: 5개
- **총 테스트 케이스**: 45개 이상
- **예상 실행 시간**: 30-60초
- **커버리지 목표**: 85% 이상

### 주요 검증 항목

✅ 캐시 저장/조회/삭제 (9개 테스트)
✅ 조건부 DB 저장 (9개 테스트)
✅ 권한 검증 (7개 테스트)
✅ WebSocket 연결 및 송수신 (8개 테스트)
✅ 실사용 시나리오 (7개 테스트)


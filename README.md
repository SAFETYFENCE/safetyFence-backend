# SafetyFence Backend

교통약자(치매 환자, 어린이 등)의 실시간 위치를 보호자와 공유하고, 지오펜스 기반 안전 알림을 제공하는 백엔드 서비스입니다.

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Framework | Spring Boot 3.5, Java 17 |
| Database | PostgreSQL 15 + PostGIS 3.3 (공간 데이터) |
| 실시간 통신 | WebSocket + STOMP (SimpleBroker) |
| 캐싱 | Caffeine Cache (10k entries, 5min TTL) |
| 푸시 알림 | Firebase Cloud Messaging (FCM) |
| 외부 API | Kakao Maps (Geocoding) |
| 파일 저장소 | AWS S3 + CloudFront |
| 인프라 | Docker Compose, Nginx (리버스 프록시) |
| CI/CD | GitHub Actions + JaCoCo |
| 테스트 | JUnit 5, Awaitility, k6 (부하 테스트) |

---

## 시스템 아키텍처

```
                         ┌─────────────────────┐
                         │   React Native App   │
                         └──────────┬───────────┘
                                    │
                          HTTP / WebSocket (WSS)
                                    │
                         ┌──────────▼───────────┐
                         │    Nginx (Reverse     │
                         │    Proxy + SSL)       │
                         └──────────┬───────────┘
                                    │
                         ┌──────────▼───────────┐
                         │   Spring Boot 3.5    │
                         │                      │
                         │  ┌────────────────┐  │
                         │  │  REST API      │  │
                         │  │  Controllers   │  │
                         │  ├────────────────┤  │
                         │  │  WebSocket     │  │
                         │  │  STOMP Broker  │  │
                         │  ├────────────────┤  │
                         │  │  Caffeine      │  │
                         │  │  Cache         │  │
                         │  └────────────────┘  │
                         └──┬──────┬──────┬─────┘
                            │      │      │
               ┌────────────┘      │      └────────────┐
               │                   │                    │
    ┌──────────▼──────┐  ┌────────▼────────┐  ┌───────▼───────┐
    │  PostgreSQL     │  │  Firebase FCM   │  │  Kakao Maps   │
    │  + PostGIS      │  │  (Push Notify)  │  │  (Geocoding)  │
    └─────────────────┘  └─────────────────┘  └───────────────┘
```

---

## 주요 기능

### 실시간 위치 공유
- WebSocket + STOMP 프로토콜로 양방향 실시간 위치 전송
- STOMP 프레임 레벨 인증 (`CONNECT` 헤더에서 사용자 검증)
- 구독 시 권한 검증 (Link 관계 확인 후 위치 수신 허용)
- 구독 즉시 캐시/DB에서 최신 위치 전송

### 지오펜스 (안전 영역)
- PostGIS `ST_DWithin` 공간 쿼리로 영역 진입/이탈 감지
- 영구 지오펜스 (자주 가는 곳) / 임시 지오펜스 (일회성) 지원
- 전략 패턴(`GeofenceEntryHandler`)으로 유형별 처리 분리
- 진입 시 보호자에게 FCM 푸시 알림

### 투약 관리
- 복약 일정 등록 및 자동 알림 (`MedicationReminderScheduler`)
- 복약 이력 관리 및 체크/언체크

### 긴급 알림
- 긴급 상황 발생 시 연결된 보호자 전원에게 FCM 알림

### 보호자-피보호자 연동
- 링크 코드 기반 양방향 연결
- 대표 보호자 지정 기능
- 관리자 대시보드 (연결 현황 조회)

---

## 프로젝트 구조

```
src/main/java/com/project/safetyFence/
├── user/                  # 회원가입, 로그인, 사용자 관리
│   ├── domain/            # User, UserAddress, DeviceToken 엔티티
│   ├── dto/               # 요청/응답 DTO
│   └── exception/         # 사용자 관련 예외
├── location/              # 실시간 위치 공유
│   ├── config/            # WebSocketConfig, WebSocketAuthInterceptor
│   ├── domain/            # UserLocation 엔티티 (PostGIS Point)
│   ├── dto/               # LocationUpdateDto
│   ├── listener/          # WebSocketEventListener
│   └── strategy/          # DistanceBasedSaveStrategy (조건부 저장)
├── geofence/              # 지오펜스 (안전 영역)
│   ├── handler/           # GeofenceEntryHandler 전략 패턴
│   ├── scheduler/         # 임시 지오펜스 만료 스케줄러
│   └── dto/               # 지오펜스 요청/응답 DTO
├── calendar/              # 투약 일정 관리
│   ├── domain/            # Medication, MedicationLog 엔티티
│   └── dto/               # 투약 관련 DTO
├── notification/          # FCM 푸시 알림
│   └── dto/               # 알림 요청 DTO
├── mypage/                # 마이페이지 (프로필, 설정)
├── admin/                 # 관리자 기능
│   └── dto/               # 관리자 응답 DTO
└── common/                # 공통 모듈
    ├── config/            # FirebaseConfig, AsyncConfig, WebConfig
    ├── interceptor/       # AuthInterceptor (API Key 인증)
    ├── service/geocoding/ # Kakao Geocoding 연동
    ├── exception/         # GlobalExceptionHandler
    └── util/              # UserUtils 등 유틸리티
```

---

## 실행 방법

### 사전 요구사항
- Java 17
- PostgreSQL 15 + PostGIS 확장
- Firebase 서비스 계정 키 (`src/main/resources/firebase-service-account.json`)
- Kakao REST API 키

### 로컬 개발

```bash
# 1. PostgreSQL + PostGIS 설정
createdb safetyfence_test_db
psql safetyfence_test_db -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# 2. 실행
./gradlew bootRun --args='\
  --spring.datasource.url=jdbc:postgresql://localhost:5432/safetyfence_test_db \
  --spring.datasource.username=safetyfence \
  --spring.datasource.password=chung0513'
```

### Docker 배포

```bash
docker-compose up -d
# Nginx (80/443) → Spring Boot (8080) → PostgreSQL (5432)
```

---

## 테스트

```bash
# 전체 테스트 실행 (JaCoCo 리포트 자동 생성)
./gradlew test

# 리포트 확인
open build/reports/jacoco/test/html/index.html
```

- **31개 테스트 파일**, 단위 테스트 + 통합 테스트 + E2E 테스트
- k6 부하 테스트: `k6/polling-load-test.js`, `k6/websocket-load-test.js`
- GitHub Actions CI: push/PR 시 자동 빌드 + 테스트 + JaCoCo 리포트 업로드

---

## 성능 최적화

### 1. Polling → WebSocket 전환

| 지표 | HTTP Polling | WebSocket | 개선율 |
|------|-------------|-----------|--------|
| 네트워크 트래픽 | 매 1-2초 폴링 | 변경 시만 전송 | 90% 감소 |
| 실시간성 (p95) | 3.60ms | 0.03ms | 99% 개선 |
| 서버 부하 | N명 × 1req/s | 연결 유지만 | 대폭 감소 |

### 2. Caffeine Cache
- 최신 위치 1건만 메모리 캐싱 (10,000 entries, 5분 TTL)
- 구독 시작 시 캐시에서 즉시 응답 → DB 조회 최소화

### 3. DistanceBasedSaveStrategy
- **100m 이상 이동** 또는 **1분 이상 경과** 시에만 DB 저장
- `@Async`로 비동기 처리하여 WebSocket 응답 차단 없음
- DB 쓰기 약 95% 감소

### 4. PostGIS 공간 인덱스
- `ST_DWithin`으로 인덱스 기반 공간 검색 (O(N) → O(log N))
- Java Haversine 대비 네이티브 C 함수로 정확도 + 속도 향상

---

## 트러블슈팅

프로젝트 진행 중 겪은 주요 문제와 해결 과정을 별도 문서로 정리했습니다.

| 문서 | 내용 |
|------|------|
| [WebSocket 트러블슈팅](docs/troubleshooting-websocket.md) | React Native 82바이트 문제, Heartbeat TEXT/BINARY 분기, Nginx 설정 |
| [CI/CD 트러블슈팅](docs/troubleshooting-cicd.md) | PostGIS 서비스 컨테이너 누락, Firebase 초기화 실패, 테스트 환경 분리 |

---

## 관련 문서

| 문서 | 설명 |
|------|------|
| [API 명세서](claudedocs/API_명세서.md) | REST API 엔드포인트 상세 |
| [WebSocket 시스템 설계](claudedocs/WebSocket_실시간_위치_공유_시스템.md) | 실시간 위치 공유 아키텍처 |
| [기술 발전 과정](claudedocs/technology_evolution.md) | HTTP → WebSocket → PostGIS 진화 |
| [배포 가이드](claudedocs/DEPLOYMENT_GUIDE.md) | Docker Compose 배포 절차 |
| [WebSocket 테스트 가이드](claudedocs/WebSocket_테스트_가이드.md) | WebSocket 테스트 방법 |

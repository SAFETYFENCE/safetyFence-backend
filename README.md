# SafetyFence Backend

[![CI](https://github.com/SAFETYFENCE/safetyFence-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/SAFETYFENCE/safetyFence-backend/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-green)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%20%2B%20PostGIS-blue)

교통약자(치매 환자, 어린이 등)의 실시간 위치를 보호자와 공유하고, 지오펜스 기반 안전 알림을 제공하는 백엔드 서비스입니다.

## Features

- **실시간 위치 공유** - WebSocket + STOMP 프로토콜, STOMP 프레임 레벨 인증 및 구독 권한 검증
- **지오펜스** - PostGIS 공간 쿼리(`ST_DWithin`)로 안전 영역 진입/이탈 감지, 전략 패턴으로 유형별 처리
- **투약 관리** - 복약 일정 등록, 자동 알림 스케줄러, 복약 이력 추적
- **긴급 알림** - FCM 푸시를 통한 보호자 즉시 알림
- **보호자-피보호자 연동** - 링크 코드 기반 양방향 연결, 대표 보호자 지정

## Tech Stack

| Category | Technology |
|----------|-----------|
| Framework | Spring Boot 3.5, Java 17 |
| Database | PostgreSQL 15 + PostGIS 3.3 |
| Real-time | WebSocket + STOMP (SimpleBroker) |
| Cache | Caffeine (10K entries, 5min TTL) |
| Push | Firebase Cloud Messaging |
| External API | Kakao Maps Geocoding |
| Infra | Docker Compose, Nginx |
| CI | GitHub Actions, JaCoCo |

## Architecture

```
React Native App
        │
   HTTP / WebSocket
        │
   ┌────▼────┐     ┌──────────────┐     ┌──────────────────┐
   │  Nginx  │────►│ Spring Boot  │────►│ PostgreSQL       │
   │  (SSL)  │     │              │     │ + PostGIS        │
   └─────────┘     │  REST API    │     └──────────────────┘
                   │  STOMP WS    │
                   │  Caffeine    │────► Firebase FCM
                   └──────────────┘────► Kakao Maps API
```

## Getting Started

### Prerequisites

- Java 17+
- PostgreSQL 15 with PostGIS extension
- Firebase service account key (`src/main/resources/firebase-service-account.json`)
- Kakao REST API key

### Run Locally

```bash
# Setup database
createdb safetyfence_test_db
psql safetyfence_test_db -c "CREATE EXTENSION IF NOT EXISTS postgis;"

# Start server
./gradlew bootRun --args='\
  --spring.datasource.url=jdbc:postgresql://localhost:5432/safetyfence_test_db \
  --spring.datasource.username=safetyfence \
  --spring.datasource.password=chung0513'
```

### Docker

```bash
docker-compose up -d
```

## Testing

```bash
./gradlew test
```

31개 테스트 파일 (단위 + 통합 + E2E). JaCoCo 커버리지 리포트는 `build/reports/jacoco/test/html/`에 생성됩니다.

GitHub Actions CI가 push/PR마다 자동 빌드 및 테스트를 실행합니다.

## Performance

| 최적화 | 방법 | 효과 |
|--------|------|------|
| Polling → WebSocket | STOMP 양방향 통신 | 네트워크 트래픽 90% 감소 |
| Caffeine Cache | 최신 위치 인메모리 캐싱 | DB 읽기 최소화 |
| DistanceBasedSaveStrategy | 100m 이동 or 1분 경과 시만 DB 저장 | DB 쓰기 95% 감소 |
| PostGIS 공간 인덱스 | `ST_DWithin` + GIST 인덱스 | O(N) → O(log N) |

## Project Structure

```
src/main/java/com/project/safetyFence/
├── user/           # 회원, 인증, 디바이스 토큰
├── location/       # 실시간 위치 (WebSocket, Cache, 저장 전략)
├── geofence/       # 지오펜스 (Handler 전략 패턴, 스케줄러)
├── calendar/       # 투약 관리 (Medication, 알림 스케줄러)
├── notification/   # FCM 푸시 알림
├── mypage/         # 프로필, 설정
├── admin/          # 관리자 대시보드
└── common/         # Config, Interceptor, Exception, Util
```

## Documentation

| Document | Description |
|----------|-------------|
| [API 명세서](claudedocs/API_명세서.md) | REST API endpoints |
| [WebSocket 시스템 설계](claudedocs/WebSocket_실시간_위치_공유_시스템.md) | 실시간 위치 공유 아키텍처 |
| [기술 발전 과정](claudedocs/technology_evolution.md) | HTTP → WebSocket → PostGIS 진화 |
| [배포 가이드](claudedocs/DEPLOYMENT_GUIDE.md) | Docker Compose 배포 |
| [WebSocket 테스트 가이드](claudedocs/WebSocket_테스트_가이드.md) | WebSocket 테스트 방법 |

## Troubleshooting

| Issue | Document |
|-------|----------|
| React Native STOMP 82바이트 문제, Heartbeat 분기 | [WebSocket 트러블슈팅](docs/troubleshooting-websocket.md) |
| CI PostGIS 누락, Firebase 초기화 실패 | [CI/CD 트러블슈팅](docs/troubleshooting-cicd.md) |

## License

This project is proprietary.

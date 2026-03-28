# CI/CD 트러블슈팅

GitHub Actions CI 파이프라인 구축 과정에서 발생한 문제와 해결 과정을 정리합니다.

---

## CI 파이프라인 개요

```yaml
# .github/workflows/ci.yml
trigger: push/PR → main branch
steps: Checkout → JDK 17 → Gradle Build (test + JaCoCo) → Report Upload
```

---

## 문제 1: PostGIS 서비스 컨테이너 누락 (208/263 테스트 실패)

### 증상

GitHub Actions에서 빌드 시 263개 테스트 중 **208개 실패**.

로컬에서는 전체 통과했지만, CI 환경에서 대량 실패 발생.

### 원인 분석

로컬 개발 환경에는 PostgreSQL + PostGIS가 항상 실행 중이지만, GitHub Actions 러너에는 데이터베이스가 없습니다.

테스트 프로필(`application-test.properties`)이 PostgreSQL 연결을 요구:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/safetyfence_test_db
spring.datasource.driver-class-name=org.postgresql.Driver
```

CI 러너에 PostgreSQL이 없으므로 DB 연결이 필요한 모든 테스트가 실패.

### 해결: GitHub Actions 서비스 컨테이너 추가

```yaml
# .github/workflows/ci.yml
jobs:
  build:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgis/postgis:15-3.3   # PostgreSQL + PostGIS 통합 이미지
        env:
          POSTGRES_DB: safetyfence_test_db
          POSTGRES_USER: safetyfence
          POSTGRES_PASSWORD: chung0513
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
```

핵심 포인트:
- `postgis/postgis:15-3.3` 이미지 사용 (일반 `postgres` 이미지에는 PostGIS 확장이 없음)
- `health-cmd pg_isready`로 DB 준비 완료까지 대기
- 로컬 환경과 동일한 DB명/유저/비밀번호 설정

> 커밋: `ade1cb6` - fix: CI에 PostgreSQL + PostGIS 서비스 컨테이너 추가

---

## 문제 2: Firebase 초기화 실패 (Context 로딩 에러)

### 증상

PostGIS 서비스 컨테이너를 추가한 후에도 일부 테스트가 Spring Context 로딩 단계에서 실패:

```
java.lang.RuntimeException: Firebase 초기화 실패
Caused by: java.io.FileNotFoundException:
  class path resource [firebase-service-account.json] cannot be resolved
```

### 원인 분석

`FirebaseConfig` 클래스가 `@PostConstruct`로 앱 시작 시 Firebase Admin SDK를 초기화합니다:

```java
@Configuration
public class FirebaseConfig {
    @PostConstruct
    public void initialize() {
        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(
                new ClassPathResource("firebase-service-account.json")
                    .getInputStream()))
            .build();
        FirebaseApp.initializeApp(options);
    }
}
```

`firebase-service-account.json`은 보안상 `.gitignore`에 등록되어 있어 CI 러너에 존재하지 않음.
따라서 **모든 `@SpringBootTest`가 Context 로딩 실패로 중단**.

### 해결: `@Profile("!test")`로 테스트 환경에서 Firebase 비활성화

```java
@Slf4j
@Configuration
@Profile("!test")   // test 프로필에서는 이 Bean을 로딩하지 않음
public class FirebaseConfig {
    @PostConstruct
    public void initialize() {
        // Firebase 초기화 로직 (기존 코드 그대로)
    }
}
```

CI workflow에서 테스트 프로필 활성화:

```yaml
- name: Build with Gradle
  env:
    SPRING_PROFILES_ACTIVE: test
  run: ./gradlew build
```

| 환경 | 프로필 | FirebaseConfig | 동작 |
|------|--------|---------------|------|
| 로컬 개발 | default/dev | 로딩됨 | Firebase 정상 초기화 |
| CI 테스트 | test | 로딩 안 됨 | Firebase 건너뜀 |
| 프로덕션 | prod | 로딩됨 | Firebase 정상 초기화 |

> 커밋: `79b66ef` - fix: test 프로필에서 Firebase 초기화 비활성화 (CI 빌드 수정)

---

## 최종 CI 파이프라인

```yaml
name: CI

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    services:
      postgres:
        image: postgis/postgis:15-3.3
        env:
          POSTGRES_DB: safetyfence_test_db
          POSTGRES_USER: safetyfence
          POSTGRES_PASSWORD: chung0513
        ports:
          - 5432:5432
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Build with Gradle
        env:
          SPRING_PROFILES_ACTIVE: test
        run: ./gradlew build

      - name: Upload JaCoCo Report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: build/reports/jacoco/test/html/
```

---

## 핵심 교훈

### 로컬 vs CI 환경 차이를 항상 의식할 것

| 항목 | 로컬 개발 | CI (GitHub Actions) |
|------|----------|-------------------|
| PostgreSQL | 항상 실행 중 | 서비스 컨테이너 필요 |
| PostGIS | 확장 설치됨 | `postgis/postgis` 이미지 필요 |
| Firebase 키 | 파일 존재 | `.gitignore`로 부재 |
| 환경 변수 | IDE/shell에서 설정 | `env:` 블록으로 명시 |

### 테스트 환경 분리 전략

1. **외부 서비스 의존성 격리**: `@Profile`로 환경별 Bean 로딩 제어
2. **서비스 컨테이너 활용**: 실제 DB와 동일한 환경으로 테스트 신뢰도 확보
3. **Health Check**: 서비스 준비 완료까지 대기하여 Race Condition 방지

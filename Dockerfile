# Dockerfile: Spring Boot 애플리케이션을 Docker 이미지로 만드는 레시피

# ============================================
# 1. 베이스 이미지 선택
# ============================================
# Eclipse Temurin: OpenJDK의 공식 배포판
# 17-jre-alpine: Java 17 런타임만 포함된 경량 버전 (Alpine Linux 기반)
# JRE만 사용하는 이유: 실행만 하면 되므로 JDK(개발도구) 불필요
FROM eclipse-temurin:17-jre

ARG DEBIAN_FRONTEND=noninteractive
ENV TZ=Asia/Seoul

RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && ln -fs /usr/share/zoneinfo/${TZ} /etc/localtime \
    && dpkg-reconfigure -f noninteractive tzdata \
    && rm -rf /var/lib/apt/lists/*

# ============================================
# 2. 작업 디렉토리 설정
# ============================================
# 컨테이너 내부에서 /app 디렉토리를 작업 공간으로 사용
WORKDIR /app

# ============================================
# 3. JAR 파일 복사
# ============================================
# 로컬의 build/libs/safetyFence-0.0.X-SNAPSHOT.jar 파일을 컨테이너의 /app/app.jar로 복사
# Gradle 빌드 후 생성되는 실행 가능한 JAR 파일을 이미지에 포함
# plain.jar는 의존성이 없으므로 제외
COPY build/libs/safetyFence-0.0.6-SNAPSHOT.jar app.jar

# ============================================
# 4. 포트 노출
# ============================================
# 컨테이너가 8080 포트를 사용한다는 것을 문서화
# (실제 포트 매핑은 docker-compose.yml에서 설정)
EXPOSE 8080

# ============================================
# 5. 애플리케이션 실행 명령어
# ============================================
# 컨테이너가 시작될 때 실행할 명령어
# java -jar app.jar로 Spring Boot 실행
ENTRYPOINT ["java", "-jar", "app.jar"]

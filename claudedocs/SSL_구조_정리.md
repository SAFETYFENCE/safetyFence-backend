# SafetyFence SSL 구조 정리

## 1. SSL이란?

SSL(TLS)은 클라이언트와 서버 사이의 통신을 암호화하는 프로토콜이다.
- HTTP (평문) → HTTPS (암호화)
- 브라우저 주소창에 자물쇠 아이콘이 뜨면 SSL이 적용된 것

---

## 2. Let's Encrypt란?

무료 SSL 인증서 발급 기관이다.
- 인증서 유효기간: **90일** (3개월마다 갱신 필요)
- `certbot`이라는 도구로 발급/갱신을 자동화
- 발급 시 "이 도메인이 정말 네 서버인지" 검증하는 과정이 필요 (80 포트 사용)

---

## 3. 현재 SafetyFence SSL 구조

```
사용자 (앱/브라우저)
    │
    │ HTTPS (443)
    ▼
┌─────────────────────────────┐
│  Docker: safetyfence-nginx  │  ← SSL 종단 (암호화 해제)
│  포트: 80, 443              │
│  인증서: /etc/nginx/ssl/    │
└─────────┬───────────────────┘
          │ HTTP (8080, Docker 내부 네트워크)
          ▼
┌─────────────────────────────┐
│  Docker: safetyfence-app    │  ← Spring Boot
│  포트: 8080                 │
└─────────────────────────────┘
```

- **SSL 종단(Termination)**: Docker Nginx가 HTTPS를 받아서 암호화를 해제하고, 내부에서는 평문 HTTP로 Spring Boot에 전달
- HTTP(80)로 접속하면 HTTPS(443)로 자동 리다이렉트

---

## 4. 인증서 파일 위치

### 호스트 (EC2)
```
/etc/letsencrypt/live/safetyfencecompany.com/
├── fullchain.pem   ← 인증서 (서버 인증서 + 중간 인증서 체인)
├── privkey.pem     ← 개인 키
├── cert.pem        ← 서버 인증서만
└── chain.pem       ← 중간 인증서만
```
- certbot이 발급/갱신하면 여기에 저장됨

### Docker Nginx가 읽는 위치
```
~/safetyfence/nginx/ssl/
├── fullchain.pem   ← 호스트에서 복사해온 것
└── privkey.pem     ← 호스트에서 복사해온 것
```
- docker-compose.yml에서 볼륨 마운트: `./nginx/ssl:/etc/nginx/ssl`
- Docker Nginx는 이 경로에서 인증서를 읽음

### 흐름 요약
```
certbot 갱신 → /etc/letsencrypt/live/.../ (호스트)
                        │
                    (수동 복사 필요)
                        │
                        ▼
              ~/safetyfence/nginx/ssl/ (호스트)
                        │
                    (볼륨 마운트)
                        │
                        ▼
              /etc/nginx/ssl/ (Docker 컨테이너 내부)
```

---

## 5. Nginx SSL 설정 (nginx.conf)

파일 위치: `~/safetyfence/nginx/nginx.conf`

```nginx
# HTTP → HTTPS 리다이렉트
server {
    listen 80;
    server_name safetyfencecompany.com;
    return 301 https://$host$request_uri;   # 모든 HTTP 요청을 HTTPS로 전환
}

# HTTPS 서버
server {
    listen 443 ssl;
    server_name safetyfencecompany.com;

    ssl_certificate /etc/nginx/ssl/fullchain.pem;       # 인증서
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;      # 개인 키
    ssl_protocols TLSv1.2 TLSv1.3;                       # 허용할 TLS 버전
    ssl_ciphers HIGH:!aNULL:!MD5;                        # 암호화 알고리즘

    # 일반 API → Spring Boot로 프록시
    location / {
        proxy_pass http://backend;
        ...
    }

    # WebSocket → Spring Boot로 프록시 (Upgrade 헤더 필요)
    location = /ws {
        proxy_pass http://backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
        ...
    }
}
```

---

## 6. 현재 문제: 인증서 자동 갱신 실패

### 인증서 만료일
- **2026-04-15** (현재 기준 18일 남음)

### 자동 갱신 시도는 되고 있음
- `certbot.timer` (systemd 타이머)가 주기적으로 `certbot renew` 실행

### 하지만 매번 실패
```
Failed to renew: Could not bind TCP port 80 because it is already in use
```
- certbot의 standalone 방식은 80 포트를 직접 바인딩해야 함
- Docker Nginx가 이미 80 포트를 사용 중이라 충돌

### 추가 문제
- certbot이 갱신에 성공하더라도 `/etc/letsencrypt/live/...`에만 저장됨
- `~/safetyfence/nginx/ssl/`로 복사하는 과정이 없음
- Docker Nginx를 reload/restart 하지 않으면 새 인증서를 읽지 않음

---

## 7. 해결 방법

certbot 갱신 시 Docker Nginx를 잠깐 멈추고 → 갱신 → 인증서 복사 → 다시 시작

### 수동 테스트 (dry-run)
```bash
sudo certbot renew --dry-run \
  --pre-hook "docker stop safetyfence-nginx" \
  --post-hook "cp /etc/letsencrypt/live/safetyfencecompany.com/fullchain.pem /home/ubuntu/safetyfence/nginx/ssl/ && cp /etc/letsencrypt/live/safetyfencecompany.com/privkey.pem /home/ubuntu/safetyfence/nginx/ssl/ && docker start safetyfence-nginx"
```

- `--pre-hook`: 갱신 전에 Docker Nginx 중지 (80 포트 해제)
- `--post-hook`: 갱신 후 인증서 복사 + Docker Nginx 재시작
- 전체 소요시간: 약 5~10초

### 자동 갱신 설정 (테스트 성공 후)
```bash
# certbot 갱신 설정 파일 수정
sudo vi /etc/letsencrypt/renewal/safetyfencecompany.com.conf

# 아래 내용 추가
[renewalparams]
pre_hook = docker stop safetyfence-nginx
post_hook = cp /etc/letsencrypt/live/safetyfencecompany.com/fullchain.pem /home/ubuntu/safetyfence/nginx/ssl/ && cp /etc/letsencrypt/live/safetyfencecompany.com/privkey.pem /home/ubuntu/safetyfence/nginx/ssl/ && docker start safetyfence-nginx
```

이렇게 설정하면 `certbot.timer`가 자동 갱신 시도할 때 hook이 함께 실행됨.

---

## 8. 호스트 Nginx 문제

현재 **호스트에도 Nginx가 설치**되어 있다.
- `/etc/nginx/sites-enabled/default` → 80 포트 listen
- 하지만 Docker Nginx가 먼저 80 포트를 점유하고 있어서 실질적으로 동작하지 않음
- 혼란을 줄 수 있으므로 비활성화 권장

```bash
# 호스트 Nginx 중지 및 자동시작 비활성화
sudo systemctl stop nginx
sudo systemctl disable nginx
```

---

## 9. 관련 파일 목록

| 파일/경로 | 위치 | 역할 |
|-----------|------|------|
| `/etc/letsencrypt/live/safetyfencecompany.com/` | EC2 호스트 | certbot이 관리하는 인증서 원본 |
| `~/safetyfence/nginx/ssl/` | EC2 호스트 | Docker Nginx에 마운트되는 인증서 복사본 |
| `~/safetyfence/nginx/nginx.conf` | EC2 호스트 | Nginx SSL 설정 |
| `~/safetyfence/docker-compose.yml` | EC2 호스트 | 볼륨 마운트 정의 (`./nginx/ssl:/etc/nginx/ssl`) |
| `/etc/letsencrypt/renewal/safetyfencecompany.com.conf` | EC2 호스트 | certbot 갱신 설정 (hook 추가할 곳) |
| `/etc/nginx/sites-enabled/default` | EC2 호스트 | 호스트 Nginx (미사용, 비활성화 권장) |

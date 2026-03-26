#!/bin/bash
# Polling vs WebSocket 부하 테스트 비교 실행
# 사용법: bash k6/run-comparison.sh
#
# 사전 조건:
#   1. 서버 실행 중: ./gradlew bootRun (또는 test profile)
#   2. 테스트 사용자 생성 완료: bash k6/setup-test-data.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/.."

# 서버 상태 확인
echo "=== 서버 연결 확인 ==="
if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/user/signIn -X POST -H "Content-Type: application/json" -d '{"number":"test","password":"test"}' > /dev/null 2>&1; then
    echo "ERROR: 서버가 응답하지 않습니다. 8081 포트에서 서버를 먼저 실행하세요."
    exit 1
fi
echo "서버 연결 확인 완료"
echo ""

# API Key 파일 확인
if [ ! -f "k6/api-keys.json" ]; then
    echo "ERROR: k6/api-keys.json 파일이 없습니다. bash k6/setup-test-data.sh 를 먼저 실행하세요."
    exit 1
fi

KEY_COUNT=$(grep -c 'apiKey' k6/api-keys.json)
echo "테스트 사용자: ${KEY_COUNT}명"
echo ""

# === Polling 테스트 ===
echo "╔══════════════════════════════════════════════════╗"
echo "║          1/2: POLLING 부하 테스트 시작             ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

k6 run k6/polling-load-test.js

echo ""
echo "Polling 테스트 완료. 30초 대기 후 WebSocket 테스트 시작..."
sleep 30

# === WebSocket 테스트 ===
echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║        2/2: WEBSOCKET 부하 테스트 시작             ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

k6 run k6/websocket-load-test.js

echo ""

# === 비교 결과 출력 ===
echo "╔══════════════════════════════════════════════════════════════════╗"
echo "║                    최종 비교 결과                                 ║"
echo "╠══════════════════════════════════════════════════════════════════╣"
echo ""

if [ -f "k6/polling-result.json" ] && [ -f "k6/websocket-result.json" ]; then
    python3 - << 'PYTHON_SCRIPT'
import json

with open('k6/polling-result.json') as f:
    polling = json.load(f)
with open('k6/websocket-result.json') as f:
    websocket = json.load(f)

pm = polling['metrics']
wm = websocket['metrics']

print(f"{'항목':<25} | {'Polling':<20} | {'WebSocket':<20} | {'비교':<15}")
print("-" * 85)

# HTTP 요청 vs WebSocket 메시지
p_reqs = pm.get('http_reqs', 0)
w_msgs = wm.get('ws_messages_sent', 0)
print(f"{'총 전송 횟수':<25} | {p_reqs:<20} | {w_msgs:<20} | {'WebSocket 승' if w_msgs >= p_reqs else 'Polling 승'}")

# 응답시간 비교
p_p95 = pm.get('http_req_duration_p95', 0)
w_p95 = wm.get('ws_send_latency_p95', 0)
print(f"{'p95 지연시간':<25} | {p_p95:.2f}ms{'':<13} | {w_p95:.2f}ms{'':<13} | {((1 - w_p95/p_p95) * 100):.0f}% 개선" if p_p95 > 0 else f"{'p95 지연시간':<25} | {p_p95:.2f}ms{'':<13} | {w_p95:.2f}ms{'':<13} | -")

# 평균 응답시간
p_avg = pm.get('http_req_duration_avg', 0)
w_avg = wm.get('ws_send_latency_avg', 0)
print(f"{'평균 지연시간':<25} | {p_avg:.2f}ms{'':<13} | {w_avg:.2f}ms{'':<13} | {((1 - w_avg/p_avg) * 100):.0f}% 개선" if p_avg > 0 else f"{'평균 지연시간':<25} | {p_avg:.2f}ms{'':<13} | {w_avg:.2f}ms{'':<13} | -")

# 에러율
p_err = pm.get('http_req_failed_rate', 0) * 100
w_err = (wm.get('ws_message_errors', 0) / max(w_msgs, 1)) * 100
print(f"{'에러율':<25} | {p_err:.2f}%{'':<14} | {w_err:.2f}%{'':<14} | -")

# 연결 오버헤드
w_conn = wm.get('ws_connect_time_avg', 0)
print(f"{'연결 오버헤드':<25} | {'매 요청 TCP 핸드셰이크':<20} | {w_conn:.2f}ms (1회){'':<6} | WebSocket 승")

print()
print("=== 핵심 결론 ===")
if p_p95 > 0 and w_p95 > 0:
    improvement = ((p_p95 - w_p95) / p_p95) * 100
    print(f"WebSocket p95 지연시간이 Polling 대비 {improvement:.0f}% 빠름")
    print(f"  Polling p95: {p_p95:.2f}ms → WebSocket p95: {w_p95:.2f}ms")
print(f"Polling HTTP 요청: {p_reqs}회 vs WebSocket 프레임: {w_msgs}회 (HTTP 요청 0회)")
PYTHON_SCRIPT
else
    echo "결과 파일이 생성되지 않았습니다."
fi

echo ""
echo "╚══════════════════════════════════════════════════════════════════╝"
echo ""
echo "상세 결과:"
echo "  - Polling:   k6/polling-result.json"
echo "  - WebSocket: k6/websocket-result.json"

import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

/**
 * WebSocket (STOMP) 방식 부하 테스트
 *
 * 시뮬레이션: 각 사용자가 WebSocket 연결 후 3초 간격으로 STOMP SEND 프레임 전송
 * 현재 아키텍처의 실제 동작 방식
 */

// 커스텀 메트릭
const wsMsgSent = new Counter('ws_messages_sent');
const wsMsgErrors = new Counter('ws_message_errors');
const wsConnectTime = new Trend('ws_connect_time', true);
const wsSendLatency = new Trend('ws_send_latency', true);

// 테스트 사용자 API Key 로드
const apiKeys = new SharedArray('apiKeys', function () {
    return JSON.parse(open('./api-keys.json'));
});

// 시나리오: Polling 테스트와 동일한 구조
export const options = {
    scenarios: {
        websocket_rampup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 10 },
                { duration: '1m', target: 10 },
                { duration: '10s', target: 30 },
                { duration: '1m', target: 30 },
                { duration: '10s', target: 50 },
                { duration: '1m', target: 50 },
                { duration: '10s', target: 0 },
            ],
        },
    },
    thresholds: {
        ws_connect_time: ['p(95)<1000'],
        ws_send_latency: ['p(95)<100'],
    },
};

// 강남역 근처 좌표
const BASE_LAT = 37.497942;
const BASE_LNG = 127.027621;

// STOMP 프레임 생성 헬퍼
function stompFrame(command, headers, body) {
    let frame = command + '\n';
    for (const [key, value] of Object.entries(headers)) {
        frame += key + ':' + value + '\n';
    }
    frame += '\n';
    if (body) {
        frame += body;
    }
    frame += '\x00';
    return frame;
}

export default function () {
    const userIndex = __VU % apiKeys.length;
    const user = apiKeys[userIndex];

    const url = 'ws://localhost:8081/ws';
    const connectStart = Date.now();

    const res = ws.connect(url, {}, function (socket) {
        const connectElapsed = Date.now() - connectStart;
        wsConnectTime.add(connectElapsed);

        // STOMP CONNECT 프레임 전송
        const connectFrame = stompFrame('CONNECT', {
            'accept-version': '1.1,1.0',
            'heart-beat': '10000,10000',
            'X-API-Key': user.apiKey,
        });
        socket.send(connectFrame);

        let connected = false;
        let msgCount = 0;
        const MAX_MESSAGES = 20; // VU당 약 60초 (3초 × 20회)

        socket.on('message', function (data) {
            // STOMP CONNECTED 프레임 수신 확인
            if (data.startsWith('CONNECTED') || data.indexOf('CONNECTED') !== -1) {
                connected = true;
            }
        });

        socket.on('error', function (e) {
            wsMsgErrors.add(1);
        });

        // 연결 후 위치 데이터 전송 루프
        socket.setTimeout(function () {
            if (!connected) {
                // CONNECTED 프레임 대기 시간 추가
                socket.setTimeout(function () {
                    sendLocations(socket, user, msgCount, MAX_MESSAGES);
                }, 500);
            } else {
                sendLocations(socket, user, msgCount, MAX_MESSAGES);
            }
        }, 1000); // 1초 대기 후 전송 시작
    });

    check(res, {
        'WebSocket 연결 성공': (r) => r && r.status === 101,
    });
}

function sendLocations(socket, user, msgCount, maxMessages) {
    const interval = setInterval(function () {
        if (msgCount >= maxMessages) {
            clearInterval(interval);

            // STOMP DISCONNECT 프레임 전송
            const disconnectFrame = stompFrame('DISCONNECT', { receipt: 'disconnect-1' });
            socket.send(disconnectFrame);

            socket.setTimeout(function () {
                socket.close();
            }, 500);
            return;
        }

        // GPS 노이즈 포함 위치 생성
        const lat = BASE_LAT + (Math.random() - 0.5) * 0.009;
        const lng = BASE_LNG + (Math.random() - 0.5) * 0.011;
        const payload = JSON.stringify({
            latitude: lat,
            longitude: lng,
            batteryLevel: Math.floor(Math.random() * 100),
        });

        // STOMP SEND 프레임
        const sendFrame = stompFrame('SEND', {
            destination: '/app/location',
            'content-type': 'application/json',
        }, payload);

        const sendStart = Date.now();
        socket.send(sendFrame);
        wsSendLatency.add(Date.now() - sendStart);
        wsMsgSent.add(1);
        msgCount++;
    }, 3000); // 3초 간격
}

export function handleSummary(data) {
    const summary = {
        type: 'websocket',
        timestamp: new Date().toISOString(),
        metrics: {
            ws_sessions: data.metrics.ws_sessions ? data.metrics.ws_sessions.values.count : 0,
            ws_messages_sent: data.metrics.ws_messages_sent ? data.metrics.ws_messages_sent.values.count : 0,
            ws_message_errors: data.metrics.ws_message_errors ? data.metrics.ws_message_errors.values.count : 0,
            ws_connect_time_p95: data.metrics.ws_connect_time ? data.metrics.ws_connect_time.values['p(95)'] : 0,
            ws_connect_time_avg: data.metrics.ws_connect_time ? data.metrics.ws_connect_time.values.avg : 0,
            ws_send_latency_p95: data.metrics.ws_send_latency ? data.metrics.ws_send_latency.values['p(95)'] : 0,
            ws_send_latency_avg: data.metrics.ws_send_latency ? data.metrics.ws_send_latency.values.avg : 0,
            ws_connecting: data.metrics.ws_connecting ? data.metrics.ws_connecting.values.avg : 0,
        },
    };

    return {
        'k6/websocket-result.json': JSON.stringify(summary, null, 2),
        stdout: generateReport(data),
    };
}

function generateReport(data) {
    const m = data.metrics;
    let report = '\n';
    report += '╔══════════════════════════════════════════════════╗\n';
    report += '║       WEBSOCKET 부하 테스트 결과                  ║\n';
    report += '╠══════════════════════════════════════════════════╣\n';
    report += `║ WebSocket 세션 수:    ${pad(m.ws_sessions ? m.ws_sessions.values.count : 0)}║\n`;
    report += `║ 전송한 메시지 수:     ${pad(m.ws_messages_sent ? m.ws_messages_sent.values.count : 0)}║\n`;
    report += `║ 전송 오류:            ${pad(m.ws_message_errors ? m.ws_message_errors.values.count : 0)}║\n`;
    report += '╠══════════════════════════════════════════════════╣\n';
    report += `║ 평균 연결 시간:       ${pad(fmt(m.ws_connect_time ? m.ws_connect_time.values.avg : 0) + 'ms')}║\n`;
    report += `║ p95 연결 시간:        ${pad(fmt(m.ws_connect_time ? m.ws_connect_time.values['p(95)'] : 0) + 'ms')}║\n`;
    report += `║ 평균 전송 지연:       ${pad(fmt(m.ws_send_latency ? m.ws_send_latency.values.avg : 0) + 'ms')}║\n`;
    report += `║ p95 전송 지연:        ${pad(fmt(m.ws_send_latency ? m.ws_send_latency.values['p(95)'] : 0) + 'ms')}║\n`;
    report += '╚══════════════════════════════════════════════════╝\n';
    return report;
}

function pad(val) {
    return String(val).padEnd(28);
}

function fmt(n) {
    return typeof n === 'number' ? n.toFixed(2) : '0.00';
}

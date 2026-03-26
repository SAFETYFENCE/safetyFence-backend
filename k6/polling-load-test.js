import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';
import { Counter, Trend } from 'k6/metrics';

/**
 * Polling 방식 부하 테스트
 *
 * 시뮬레이션: 각 사용자가 3초 간격으로 HTTP POST /location 요청
 * 이전 polling 아키텍처에서는 이 방식으로 위치를 전송했음
 */

// 커스텀 메트릭
const locationUpdates = new Counter('location_updates');
const locationErrors = new Counter('location_errors');
const locationLatency = new Trend('location_latency', true);

// 테스트 사용자 API Key 로드
const apiKeys = new SharedArray('apiKeys', function () {
    return JSON.parse(open('./api-keys.json'));
});

// 시나리오: 10명 → 30명 → 50명 단계별 증가
export const options = {
    scenarios: {
        polling_rampup: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 10 },   // 10초간 10명까지 증가
                { duration: '1m', target: 10 },     // 1분간 10명 유지
                { duration: '10s', target: 30 },    // 10초간 30명까지 증가
                { duration: '1m', target: 30 },     // 1분간 30명 유지
                { duration: '10s', target: 50 },    // 10초간 50명까지 증가
                { duration: '1m', target: 50 },     // 1분간 50명 유지
                { duration: '10s', target: 0 },     // 10초간 0명으로 감소
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],       // p95 응답시간 500ms 이하
        http_req_failed: ['rate<0.05'],          // 에러율 5% 이하
        location_latency: ['p(95)<500'],
    },
};

// 강남역 근처 좌표
const BASE_LAT = 37.497942;
const BASE_LNG = 127.027621;

export default function () {
    // 각 VU에 고유 사용자 할당
    const userIndex = __VU % apiKeys.length;
    const user = apiKeys[userIndex];

    // GPS 노이즈 포함 위치 생성 (±500m 범위)
    const lat = BASE_LAT + (Math.random() - 0.5) * 0.009;
    const lng = BASE_LNG + (Math.random() - 0.5) * 0.011;

    const payload = JSON.stringify({
        latitude: lat,
        longitude: lng,
        batteryLevel: Math.floor(Math.random() * 100),
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'X-API-Key': user.apiKey,
        },
    };

    const startTime = Date.now();
    const res = http.post('http://localhost:8081/location', payload, params);
    const elapsed = Date.now() - startTime;

    locationLatency.add(elapsed);

    const success = check(res, {
        'status is 200': (r) => r.status === 200,
    });

    if (success) {
        locationUpdates.add(1);
    } else {
        locationErrors.add(1);
    }

    // 3초 간격 (Polling 주기)
    sleep(3);
}

export function handleSummary(data) {
    const summary = {
        type: 'polling',
        timestamp: new Date().toISOString(),
        metrics: {
            http_reqs: data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0,
            http_req_duration_p95: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(95)'] : 0,
            http_req_duration_p99: data.metrics.http_req_duration ? data.metrics.http_req_duration.values['p(99)'] : 0,
            http_req_duration_avg: data.metrics.http_req_duration ? data.metrics.http_req_duration.values.avg : 0,
            http_req_failed_rate: data.metrics.http_req_failed ? data.metrics.http_req_failed.values.rate : 0,
            location_updates: data.metrics.location_updates ? data.metrics.location_updates.values.count : 0,
            location_errors: data.metrics.location_errors ? data.metrics.location_errors.values.count : 0,
        },
    };

    return {
        'k6/polling-result.json': JSON.stringify(summary, null, 2),
        stdout: generateReport(data),
    };
}

function generateReport(data) {
    const m = data.metrics;
    let report = '\n';
    report += '╔══════════════════════════════════════════════════╗\n';
    report += '║         POLLING 부하 테스트 결과                  ║\n';
    report += '╠══════════════════════════════════════════════════╣\n';
    report += `║ 총 HTTP 요청 수:     ${pad(m.http_reqs ? m.http_reqs.values.count : 0)}║\n`;
    report += `║ 성공한 위치 업데이트:  ${pad(m.location_updates ? m.location_updates.values.count : 0)}║\n`;
    report += `║ 실패한 요청:          ${pad(m.location_errors ? m.location_errors.values.count : 0)}║\n`;
    report += '╠══════════════════════════════════════════════════╣\n';
    report += `║ 평균 응답시간:        ${pad(fmt(m.http_req_duration ? m.http_req_duration.values.avg : 0) + 'ms')}║\n`;
    report += `║ p95 응답시간:         ${pad(fmt(m.http_req_duration ? m.http_req_duration.values['p(95)'] : 0) + 'ms')}║\n`;
    report += `║ p99 응답시간:         ${pad(fmt(m.http_req_duration ? m.http_req_duration.values['p(99)'] : 0) + 'ms')}║\n`;
    report += `║ 에러율:               ${pad(fmt((m.http_req_failed ? m.http_req_failed.values.rate : 0) * 100) + '%')}║\n`;
    report += '╚══════════════════════════════════════════════════╝\n';
    return report;
}

function pad(val) {
    return String(val).padEnd(28);
}

function fmt(n) {
    return typeof n === 'number' ? n.toFixed(2) : '0.00';
}

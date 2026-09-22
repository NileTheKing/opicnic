// [보관] 동기 채점 경로(/answers·/finalize)는 2026-09-21에 제거됨 — 전환 전 측정 기록용, 현재는 실행 불가
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// S3 — 피크 동시 30 제출 5분 (docs/performance/slo.md 검증 시나리오 S3). load-test.js의 제출 흐름을 그대로 쓰고
// 부하 모양만 다르다: 실패 주입 없이 현실 지연(STT 3s + LLM 4.5s)으로 동시 30 고정.
//
// 실행: 서버를 s1.sh 헤더의 명령(mock + 지연 3000/4500, 실패율 0)으로 기동한 뒤
//      k6 run --summary-export docs/performance/<날짜>/s3-<라벨>.json scripts/s3.js
// 측정 중 컴파일 금지 (DevTools 핫 리스타트 → 지표 리셋)

const answersDuration = new Trend('answers_duration', true);
const startDuration   = new Trend('start_duration',   true);
const errorRate       = new Rate('error_rate');

const binFile = open('test_audio.webm', 'b');

// k6는 http.file() 배열을 multipart로 직렬화 못함 → 수동으로 구성
// k6 goja runtime에 TextEncoder 없음 → charCodeAt 방식으로 대체
function strToBytes(str) {
    const bytes = new Uint8Array(str.length);
    for (let i = 0; i < str.length; i++) bytes[i] = str.charCodeAt(i) & 0xff;
    return bytes;
}

function buildMultipart(textFields, fileEntries) {
    const boundary = 'k6boundary' + Math.random().toString(36).substring(2);
    const parts = [];

    for (const [name, value] of Object.entries(textFields)) {
        parts.push(strToBytes(`--${boundary}\r\nContent-Disposition: form-data; name="${name}"\r\n\r\n${value}\r\n`));
    }
    for (const [name, data, filename] of fileEntries) {
        parts.push(strToBytes(`--${boundary}\r\nContent-Disposition: form-data; name="${name}"; filename="${filename}"\r\nContent-Type: audio/webm\r\n\r\n`));
        parts.push(new Uint8Array(data));
        parts.push(strToBytes('\r\n'));
    }
    parts.push(strToBytes(`--${boundary}--\r\n`));

    const total = parts.reduce((s, p) => s + p.byteLength, 0);
    const buf = new Uint8Array(total);
    let offset = 0;
    for (const p of parts) { buf.set(p, offset); offset += p.byteLength; }
    return { body: buf.buffer, contentType: `multipart/form-data; boundary=${boundary}` };
}

export const options = {
    // S3: 피크 동시 30 제출, 5분 (slo.md). 30은 국내 상위 영어앱 피크 추정 17건의 2배.
    scenarios: {
        peak: { executor: 'constant-vus', vus: 30, duration: __ENV.DURATION || '5m' },
    },
    thresholds: {},   // 판정은 slo.md 표에서 사람이. 여기선 기록만
};

const BASE = 'http://localhost:8080';
const TOPIC = 'MOVIE_WATCHING';
const DIFFICULTY = 'LEVEL_3';

export default function () {
    // 0단계: CSRF 토큰 발급 — k6는 로그인 세션이 없어 CSRF 토큰도 없다. 같은 VU 안에서는
    // 세션 쿠키가 자동으로 유지되므로(k6 기본 cookie jar), 이후 POST에 헤더로 실어 보낸다.
    const csrfRes = http.get(`${BASE}/api/practice-attempts/csrf`);
    const csrfToken = csrfRes.json('token');
    const csrfHeader = csrfRes.json('headerName');

    // 1단계: attempt 생성
    const startRes = http.post(
        `${BASE}/api/practice-attempts/start?topic=${TOPIC}&difficulty=${DIFFICULTY}`,
        null,
        { headers: { [csrfHeader]: csrfToken } },
    );
    startDuration.add(startRes.timings.duration);

    const startOk = check(startRes, {
        'start 200': (r) => r.status === 200,
        'has attemptId': (r) => r.json('attemptId') !== undefined,
    });
    if (!startOk) {
        errorRate.add(1);
        console.log(`[start fail] status=${startRes.status} body=${startRes.body}`);
        return;
    }
    errorRate.add(0);

    const attemptId   = startRes.json('attemptId');
    const qCount      = startRes.json('questionCount');

    // 2단계: 음성 파일 제출 — buildMultipart로 바이너리 multipart 직접 구성
    const fileEntries = [];
    for (let i = 0; i < qCount; i++) {
        fileEntries.push(['files', binFile, `audio_${i}.webm`]);
    }
    const indexes = Array.from({ length: qCount }, (_, i) => i);
    const { body, contentType } = buildMultipart(
        { attemptId, questionIndexes: JSON.stringify(indexes) },
        fileEntries
    );

    const answersRes = http.post(
        `${BASE}/api/practice-attempts/${attemptId}/answers`,
        body,
        { headers: { 'Content-Type': contentType, [csrfHeader]: csrfToken } }
    );
    answersDuration.add(answersRes.timings.duration);

    const answersOk = check(answersRes, {
        'answers 200': (r) => r.status === 200,
        'no failed feedbacks': (r) => {
            try {
                const body = r.json();
                return !body.failedIndexes || body.failedIndexes.length === 0;
            } catch (_) { return false; }
        },
    });
    if (!answersOk) {
        console.log(`[answers fail] status=${answersRes.status} body=${answersRes.body ? answersRes.body.substring(0, 200) : 'null'}`);
    }
    errorRate.add(answersOk ? 0 : 1);

    sleep(1);   // 사용자가 결과를 보고 다음 콤보로 넘어가는 최소 간격
}

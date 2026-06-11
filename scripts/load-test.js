import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// 실행: STT_ENABLED=false LLM_ENABLED=false k6 run scripts/load-test.js
//
// 목적: Mock STT/LLM 모드에서 VirtualThread + StructuredTaskScope 처리량 측정
//       Groq rate limit 우회, readAllBytes() 힙 부하만 격리해서 측정

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
    stages: [
        { duration: '15s', target: 20  },  // 웜업
        { duration: '30s', target: 50  },  // 압박
        { duration: '30s', target: 100 },  // 피크 (macOS 소켓 고갈 방지)
        { duration: '15s', target: 0   },  // 쿨다운
    ],
    thresholds: {
        'answers_duration': ['p(95)<5000'],
        'error_rate':       ['rate<0.05'],
    },
};

const BASE = 'http://localhost:8080';
const TOPIC = 'MOVIE_WATCHING';
const DIFFICULTY = 'LEVEL_3';

export default function () {
    // 1단계: attempt 생성
    const startRes = http.post(
        `${BASE}/api/practice-attempts/start?topic=${TOPIC}&difficulty=${DIFFICULTY}`,
        null,
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
        `${BASE}/api/practice-attempts/answers`,
        body,
        { headers: { 'Content-Type': contentType } }
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

    sleep(1);
}

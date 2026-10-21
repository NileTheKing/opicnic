// 용량·장애 실험 부하 (docs/performance/2026-10-21-capacity-outage/README.md).
// open model: constant-arrival-rate, 완료를 기다리지 않는다(폴링 없음).
// 제출 순서는 무작위가 아니라 고정 반복: C T C C T C M C T C (콤보 6 / 유형 연습 3 / 모의고사 1).
// 전체 흐름: csrf → start(-type/-mock) → upload-urls → R2 PUT(병렬) → POST /api/scoring-jobs
// 반복마다 새 쿠키 jar(새 사용자). 예열(WARM_S) 구간의 숫자는 *_warm으로 따로 센다(버림).
import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';

const AUDIO = open('/data/exp_recording.webm', 'b');
const SIZE = AUDIO.byteLength;
const BASE = __ENV.BASE || 'http://app:8080';
const WARM_MS = Number(__ENV.WARM_S || 60) * 1000;
const TOTAL_S = Number(__ENV.TOTAL_S || 360);
const RATE = Number(__ENV.RATE || 6.7);
const ORDER = 'CTCCTCMCTC';
const TOPICS = (__ENV.TOPICS || 'MOVIE_WATCHING').split(',');
const TYPE_PAIRS = (__ENV.TYPE_PAIRS || 'MOVIE_WATCHING:TYPE_1').split(',').map((s) => s.split(':'));
const JSON_H = { 'Content-Type': 'application/json' };

export const options = {
  batchPerHost: 20,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    load: {
      executor: 'constant-arrival-rate',
      rate: Math.round(RATE * 10),
      timeUnit: '10s',
      duration: `${TOTAL_S}s`,
      preAllocatedVUs: 150,
      maxVUs: 800,
      gracefulStop: '60s',
    },
  },
};

const M = {};
for (const ph of ['warm', 'measure']) {
  M[`submit_ms_${ph}`] = new Trend(`submit_ms_${ph}`, true);
  M[`iter_ms_${ph}`] = new Trend(`iter_ms_${ph}`, true);
  M[`put_ms_${ph}`] = new Trend(`put_ms_${ph}`, true);
  for (const c of ['iter', 'sub202', 'sub_other', 'fail_csrf', 'fail_start', 'fail_urls', 'fail_put', 'questions', 'kind_C', 'kind_T', 'kind_M']) {
    M[`${c}_${ph}`] = new Counter(`${c}_${ph}`);
  }
}

export default function () {
  const idx = exec.scenario.iterationInTest;
  const kind = ORDER[idx % ORDER.length];
  const ph = exec.instance.currentTestRunDuration < WARM_MS ? 'warm' : 'measure';
  const t0 = Date.now();
  M[`iter_${ph}`].add(1);
  M[`kind_${kind}_${ph}`].add(1);
  const jar = new http.CookieJar();
  const base = { jar, timeout: '60s' };

  const csrf = http.get(`${BASE}/api/practice-attempts/csrf`, base);
  if (csrf.status !== 200) { M[`fail_csrf_${ph}`].add(1); return; }
  const { token, headerName } = csrf.json();
  const hdr = { [headerName]: token };

  let startRes;
  if (kind === 'C') {
    const topic = TOPICS[idx % TOPICS.length];
    startRes = http.post(`${BASE}/api/practice-attempts/start?topic=${topic}&difficulty=LEVEL_3`, null, { ...base, headers: hdr });
  } else if (kind === 'T') {
    const [topic, type] = TYPE_PAIRS[idx % TYPE_PAIRS.length];
    startRes = http.post(`${BASE}/api/practice-attempts/start-type?topic=${topic}&type=${type}`, null, { ...base, headers: hdr });
  } else {
    startRes = http.post(`${BASE}/api/practice-attempts/start-mock`, null, { ...base, headers: hdr });
  }
  if (startRes.status !== 200) { M[`fail_start_${ph}`].add(1); return; }
  const { attemptId, questionCount } = startRes.json();
  M[`questions_${ph}`].add(questionCount);

  const body = JSON.stringify(Array.from({ length: questionCount }, (_, i) => ({ index: i, size: SIZE, contentType: 'audio/webm' })));
  const urlsRes = http.post(`${BASE}/api/practice-attempts/${attemptId}/upload-urls`, body, { ...base, headers: { ...JSON_H, ...hdr } });
  if (urlsRes.status !== 200) { M[`fail_urls_${ph}`].add(1); return; }
  const urls = urlsRes.json().map((u) => u.url);

  // 브라우저처럼 병렬 PUT. 하나라도 실패하면 접수하지 않는다.
  const tPut = Date.now();
  const puts = http.batch(urls.map((u) => ['PUT', u, AUDIO, { headers: { 'Content-Type': 'audio/webm' }, timeout: '120s' }]));
  M[`put_ms_${ph}`].add(Date.now() - tPut);
  if (puts.some((r) => r.status !== 200)) { M[`fail_put_${ph}`].add(1); return; }

  const sub = http.post(`${BASE}/api/scoring-jobs`, JSON.stringify({ attemptId }), { ...base, headers: { ...JSON_H, ...hdr } });
  M[`submit_ms_${ph}`].add(sub.timings.duration);
  if (sub.status === 202) M[`sub202_${ph}`].add(1); else M[`sub_other_${ph}`].add(1);
  M[`iter_ms_${ph}`].add(Date.now() - t0);
}

export function handleSummary(data) {
  return { '/out/k6-summary.json': JSON.stringify(data) };
}

#!/usr/bin/env bash
# S3 (전환 후) — 피크: 동시 30명이 5분간 콤보를 계속 제출 (docs/performance/slo.md 검증 시나리오 S3)
# 사용자 1명 = 루프: start → upload-urls → R2 PUT → POST /api/scoring-jobs(202) → 폴링(2s)으로 완료 대기 → 다음 콤보.
# 실패 주입 없이 현실 지연(STT 3s + LLM 4.5s). SLO: S1 조건 유지 — 접수 p95 ≤ 0.5s, 접수 100% 202, 완료율 100%, 서버 생존.
# 함께 기록: 접수→완료 p50/p95/max(DB), 큐 깊이 최대(5초 샘플), GC(jvm_gc_pause 전후 차), 처리량(콤보/5분).
#
# 전제: set -a; . ./.env; set +a
#   SPRING_PROFILES_ACTIVE=dev STT_ENABLED=false LLM_ENABLED=false STT_MOCK_DELAY_MS=3000 LLM_MOCK_DELAY_MS=4500 \
#   JAVA_TOOL_OPTIONS="-Xms2g -Xmx2g" ./gradlew bootRun     (측정 중 컴파일 금지)
# 사용: scripts/s3-async.sh [동시 사용자=30] [지속 초=300] [라벨=after]   (슬롯 산정: 서버를 WORKER_CONCURRENCY=N으로 띄우고 라벨에 slots-N)  → docs/performance/<오늘>/s3-async-<라벨>.txt
set -euo pipefail
USERS=${1:-30}; DURATION=${2:-300}; LABEL=${3:-after}
BASE=${BASE:-http://localhost:8080}; AUDIO=${AUDIO:-scripts/test_audio.webm}
TOPIC=${TOPIC:-MOVIE_WATCHING}; DIFFICULTY=${DIFFICULTY:-LEVEL_3}
DB_PASS=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)
q() { docker exec "${MYSQL_CONTAINER:-opicnic_mysql_s1}" mysql -uroot -p"$DB_PASS" opicnic -N -e "$1" 2>/dev/null; }
OUT_DIR="docs/performance/$(date +%F)"; OUT="$OUT_DIR/s3-async-$LABEL.txt"; RAW=$(mktemp); SAMPLES=$(mktemp); mkdir -p "$OUT_DIR"
curl -sf "$BASE/actuator/health" >/dev/null || { echo "서버 없음" >&2; exit 1; }

user_loop() {  # $1 = user index. 각 줄: "user attemptId q submit_http submit_s complete_s"
  local U=$1 END=$2 JAR CSRF TOKEN HEADER START A COUNT SIZE BODY SUB T0
  while [ "$(date +%s)" -lt "$END" ]; do
    JAR=$(mktemp); CSRF=$(curl -s -c "$JAR" "$BASE/api/practice-attempts/csrf")
    TOKEN=$(jq -r .token <<<"$CSRF"); HEADER=$(jq -r .headerName <<<"$CSRF")
    START=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -X POST "$BASE/api/practice-attempts/start?topic=$TOPIC&difficulty=$DIFFICULTY")
    A=$(jq -r .attemptId <<<"$START"); COUNT=$(jq -r .questionCount <<<"$START"); SIZE=$(stat -f%z "$AUDIO")
    BODY=$(python3 -c "import json;print(json.dumps([{'index':i,'size':$SIZE,'contentType':'audio/webm'} for i in range($COUNT)]))")
    # 브라우저(question.html)와 같은 규칙: PUT 하나라도 실패하면 접수하지 않는다 (안 올라간 파일은 워커가 404로 FAILED 처리할 뿐)
    PUT_FAIL=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -H 'Content-Type: application/json' -d "$BODY" \
      -X POST "$BASE/api/practice-attempts/$A/upload-urls" | jq -r '.[].url' | while read -r u; do
        curl -s -o /dev/null -w '%{http_code}\n' -X PUT -H 'Content-Type: audio/webm' --data-binary @"$AUDIO" "$u"; done | grep -vc '^200$' || true)
    T0=$(date +%s.%N)
    if [ "$PUT_FAIL" != 0 ]; then SUB="PUTx$PUT_FAIL 0"; else
    SUB=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -H 'Content-Type: application/json' -d "{\"attemptId\":\"$A\"}" \
      -o /dev/null -w '%{http_code} %{time_total}' -X POST "$BASE/api/scoring-jobs"); fi
    if [ "${SUB%% *}" = 202 ]; then
      until curl -s -m 3 "$BASE/api/scoring-jobs/$A" | jq -e '.status | test("COMPLETED")' >/dev/null 2>&1; do sleep 2; done
    fi
    printf '%-3s %-37s %-3s %-5s %-9s %s\n' "$U" "$A" "$COUNT" "${SUB%% *}" "${SUB#* }" "$(python3 -c "import time;print(f'{time.time()-$T0:.1f}')")"
    rm -f "$JAR"
  done
}
sampler() { while true; do curl -s "$BASE/actuator/prometheus" | awk -v t="$(date +%T)" '
  /^opicnic_worker_(queued|in_flight) /{printf "%s %s %s\n", t, $1, $2}
  /^hikaricp_connections_(active|pending)\{/{split($1,a,"{"); printf "%s %s %s\n", t, a[1], $2}
  '; echo "$(date +%T) heap_mb $(curl -s "$BASE/actuator/metrics/jvm.memory.used?tag=area:heap" | jq -r '(.measurements[0].value/1048576|floor)')"; sleep 5; done; }
gc() { local g; g=$(curl -s "$BASE/actuator/prometheus" | awk '/^jvm_gc_pause_seconds_(count|sum)/{printf "%s ", $0}'); echo "${g:-(GC 없음)}"; }
export -f user_loop; export BASE AUDIO TOPIC DIFFICULTY

{ echo "# S3-async $LABEL — $(date '+%F %T')"; echo "# base=$BASE users=$USERS duration=${DURATION}s topic=$TOPIC/$DIFFICULTY audio=$AUDIO"
  echo "# 서버 기동 명령을 여기 손으로 적을 것:"; echo "#   "; } > "$OUT"
GC0=$(gc); T_START=$(date +%s); END=$((T_START + DURATION))
sampler > "$SAMPLES" & SAMPLER_PID=$!
seq 1 "$USERS" | xargs -P "$USERS" -I{} bash -c "user_loop {} $END" > "$RAW"
kill $SAMPLER_PID 2>/dev/null || true
T_END=$(date +%s); GC1=$(gc)
IDS=$(awk '{printf "%s\047%s\047", (n++?",":""), $2}' "$RAW")

{
echo "user attemptId                             q   http  submit_s  complete_s"; sort -k1,1n -k5 "$RAW"
python3 - "$RAW" "$SAMPLES" "$((T_END-T_START))" <<'PY'
import sys,statistics as st
rows=[l.split() for l in open(sys.argv[1]) if l.strip()]
sub=[float(r[4]) for r in rows if r[3]=='202']; comp=[float(r[5]) for r in rows if r[3]=='202']; codes=[r[3] for r in rows]
qs=sum(int(r[2]) for r in rows); el=int(sys.argv[3])
p=lambda xs,k: sorted(xs)[max(0,int(len(xs)*k)-1)] if xs else 0
print(f"\n# 콤보 {len(rows)}건 / 문항 {qs}개 / {el}s → {len(rows)*60/el:.0f} 콤보/분")
putx=sum(1 for c in codes if c.startswith('PUTx'))
print(f"# 접수 HTTP: 202={codes.count('202')} 비202={len(codes)-codes.count('202')-putx}  (SLO 100% 202)   R2 업로드 실패로 접수 안 함: {putx}건")
print(f"# 접수 응답: avg {st.mean(sub):.3f}s  p50 {p(sub,.5):.3f}s  p95 {p(sub,.95):.3f}s  max {max(sub):.3f}s  (SLO p95 ≤ 0.5s)")
print(f"# 접수→완료(클라이언트 폴링 기준, +≤2s): avg {st.mean(comp):.1f}s  p50 {p(comp,.5):.1f}s  p95 {p(comp,.95):.1f}s  max {max(comp):.1f}s")
qd=[int(float(l.split()[2])) for l in open(sys.argv[2]) if 'queued' in l]; inf=[int(float(l.split()[2])) for l in open(sys.argv[2]) if 'in_flight' in l]
if qd: print(f"# 큐 깊이(5s 샘플 {len(qd)}회): avg {st.mean(qd):.0f}  max {max(qd)}   처리 중: avg {st.mean(inf):.0f}  max {max(inf)}")
def col(name):
    out=[]
    for l in open(sys.argv[2]):
        f=l.split()
        if len(f)==3 and f[1]==name:
            try: out.append(float(f[2]))
            except ValueError: pass
    return out
ha,hp,hm=col('hikaricp_connections_active'),col('hikaricp_connections_pending'),col('heap_mb')
mx=lambda xs: f"{max(xs):.0f}" if xs else "-"; av=lambda xs: f"{st.mean(xs):.0f}" if xs else "-"
print(f"# HikariCP active: avg {av(ha)} max {mx(ha)}   pending max {mx(hp)}   힙 사용: avg {av(hm)}MB max {mx(hm)}MB")
PY
echo "# 잡 상태: $(q "select status, count(*) from scoring_job where id in ($IDS) group by status" | awk '{printf "%s=%s ", $1, $2}')"
echo "# 문항 상태: $(q "select status, count(*) from scoring_job_item where job_id in ($IDS) group by status" | awk '{printf "%s=%s ", $1, $2}')"
echo "# 접수→완료 DB(초): $(q "select round(min(timestampdiff(microsecond,created_at,completed_at))/1e6,1), round(avg(timestampdiff(microsecond,created_at,completed_at))/1e6,1), round(max(timestampdiff(microsecond,created_at,completed_at))/1e6,1) from scoring_job where id in ($IDS)" | awk '{print "min "$1" / avg "$2" / max "$3}')"
echo "# GC 전: $GC0"; echo "# GC 후: $GC1"
echo "# 서버 생존: $(curl -s "$BASE/actuator/health")"
} | tee -a "$OUT"
rm -f "$RAW" "$SAMPLES"; echo "→ $OUT"

#!/usr/bin/env bash
# S2 (전환 후) — 외부 API 실패 주입 시 우리 쪽 호출 증폭 + 완료율 (docs/performance/slo.md 검증 시나리오 S2)
#
# 콤보 N건을 비동기 경로(upload-urls → R2 PUT → POST /api/scoring-jobs 202)로 접수하고, 잡이 전부 끝날 때까지 기다린 뒤
# 전후의 opicnic_external_call_seconds_count 차이를 "실패가 없었다면 필요한 호출 수"로 나눈다.
# SLO: 증폭 ≤ 1.65배(실패 45% × 최대 3회 시도의 이론값 1+0.45+0.45²), 서버 생존, 접수 100% 202, 잡 완료율 100%.
# 전환 전과 다른 점: 문항 실패는 사용자 재제출이 아니라 워커 재시도로 흡수된다 — FAILED 문항 비율이 "사용자가 본 실패"다.
#
# 전제: 서버가 dev + mock + 실패 주입 + R2 키(.env)로 떠 있어야 한다. 예)
#   set -a; . ./.env; set +a
#   SPRING_PROFILES_ACTIVE=dev STT_ENABLED=false LLM_ENABLED=false STT_MOCK_DELAY_MS=3000 LLM_MOCK_DELAY_MS=4500 \
#   STT_MOCK_429_RATE=0.3 STT_MOCK_5XX_RATE=0.1 STT_MOCK_TIMEOUT_RATE=0.05 \
#   LLM_MOCK_429_RATE=0.3 LLM_MOCK_5XX_RATE=0.1 LLM_MOCK_TIMEOUT_RATE=0.05 \
#   JAVA_TOOL_OPTIONS="-Xms2g -Xmx2g" ./gradlew bootRun
# 주의: 측정 중 컴파일하지 말 것 — DevTools 핫 리스타트로 지표가 리셋된다.
#
# 사용: scripts/s2-async.sh [콤보 건수=50] [동시 접수 수=5] [라벨=after]  → docs/performance/<오늘>/s2-async-<라벨>.txt
set -euo pipefail
N=${1:-50}; PAR=${2:-5}; LABEL=${3:-after}
BASE=${BASE:-http://localhost:8080}; AUDIO=${AUDIO:-scripts/test_audio.webm}
TOPIC=${TOPIC:-MOVIE_WATCHING}; DIFFICULTY=${DIFFICULTY:-LEVEL_3}
DB_PASS=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)
q() { docker exec "${MYSQL_CONTAINER:-opicnic_mysql_s1}" mysql -uroot -p"$DB_PASS" opicnic -N -e "$1" 2>/dev/null; }
OUT_DIR="docs/performance/$(date +%F)"; OUT="$OUT_DIR/s2-async-$LABEL.txt"; mkdir -p "$OUT_DIR"
curl -sf "$BASE/actuator/health" >/dev/null || { echo "서버 없음" >&2; exit 1; }

snapshot() { curl -s "$BASE/actuator/prometheus" | { grep '^opicnic_external_call_seconds_count\|^opicnic_retry_total\|^opicnic_worker_items_total' || true; } | sort; }

one_combo() {  # $1 = run index. stdout: "run attemptId questions submit_http submit_s"
  local JAR CSRF TOKEN HEADER START A COUNT SIZE BODY SUB
  JAR=$(mktemp); CSRF=$(curl -s -c "$JAR" "$BASE/api/practice-attempts/csrf")
  TOKEN=$(jq -r .token <<<"$CSRF"); HEADER=$(jq -r .headerName <<<"$CSRF")
  START=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -X POST "$BASE/api/practice-attempts/start?topic=$TOPIC&difficulty=$DIFFICULTY")
  A=$(jq -r .attemptId <<<"$START"); COUNT=$(jq -r .questionCount <<<"$START"); SIZE=$(stat -f%z "$AUDIO")
  BODY=$(python3 -c "import json;print(json.dumps([{'index':i,'size':$SIZE,'contentType':'audio/webm'} for i in range($COUNT)]))")
  curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -H 'Content-Type: application/json' -d "$BODY" \
    -X POST "$BASE/api/practice-attempts/$A/upload-urls" | jq -r '.[].url' | while read -r u; do
      curl -s -o /dev/null -X PUT -H 'Content-Type: audio/webm' --data-binary @"$AUDIO" "$u"; done
  SUB=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -H 'Content-Type: application/json' -d "{\"attemptId\":\"$A\"}" \
    -o /dev/null -w '%{http_code} %{time_total}' -X POST "$BASE/api/scoring-jobs")
  printf '%-4s %-37s %-3s %-5s %s\n' "$1" "$A" "$COUNT" "${SUB%% *}" "${SUB#* }"
  rm -f "$JAR"
}
export -f one_combo; export BASE AUDIO TOPIC DIFFICULTY

BEFORE=$(snapshot)
{ echo "# S2-async $LABEL — $(date '+%F %T')"; echo "# base=$BASE combos=$N parallel=$PAR topic=$TOPIC/$DIFFICULTY audio=$AUDIO"
  echo "# 서버 환경(실패율·지연)은 기동 명령을 여기 손으로 적을 것:"; echo "#   "
  echo "run  attemptId                             q   http  submit_s"; } > "$OUT"

T0=$(date +%s)
seq 1 "$N" | xargs -P "$PAR" -I{} bash -c 'one_combo {}' | sort -n | tee -a "$OUT"
SUBMIT_S=$(( $(date +%s) - T0 ))
IDS=$(awk '$1 ~ /^[0-9]+$/ {printf "%s\047%s\047", (n++?",":""), $2}' "$OUT")

# 접수는 끝. 이제 워커가 다 끝낼 때까지 DB만 본다 — 클라이언트가 폴링하든 떠나든 결과는 같다
until [ "$(q "select count(*) from scoring_job where id in ($IDS) and status in ('QUEUED','PROCESSING')")" = 0 ]; do sleep 3; done
TOTAL_S=$(( $(date +%s) - T0 ))
AFTER=$(snapshot)

QUESTIONS=$(awk '$1 ~ /^[0-9]+$/ {s+=$3} END{print s+0}' "$OUT")
NON202=$(awk '$1 ~ /^[0-9]+$/ && $4 != 202' "$OUT" | wc -l | tr -d ' ')
python3 - "$BEFORE" "$AFTER" "$QUESTIONS" "$SUBMIT_S" "$TOTAL_S" "$NON202" <<'PY' | tee -a "$OUT"
import re,sys
def parse(txt):
    d={}
    for line in txt.splitlines():
        m=re.match(r'(\w+)\{([^}]*)\}\s+([0-9.eE+]+)',line)
        if not m: continue
        tags=dict(t.split('=',1) for t in m.group(2).replace('"','').split(','))
        d[(m.group(1),tags.get('kind'),tags.get('outcome') or tags.get('reason'))]=float(m.group(3))
    return d
b,a=parse(sys.argv[1]),parse(sys.argv[2]); questions=int(sys.argv[3]); submit_s,total_s,non202=map(int,sys.argv[4:7])
diff={k:a[k]-b.get(k,0) for k in a}
print(f"\n# 접수 {submit_s}s (비202: {non202}건) / 접수 시작→전부 완료 {total_s}s")
print("# 외부 호출 (후-전)"); calls={}
for (name,kind,oc),v in sorted(diff.items()):
    if name.startswith('opicnic_external_call') and v: print(f"#   {kind:5s} {oc:8s} {int(v)}"); calls[kind]=calls.get(kind,0)+v
print("# 워커 재시도 (후-전)")
for (name,kind,rs),v in sorted(diff.items()):
    if name.startswith('opicnic_retry') and v: print(f"#   {kind:5s} {rs:8s} {int(v)}")
print("# 워커 문항 결과 (후-전)")
for (name,_,oc),v in sorted(diff.items()):
    if name.startswith('opicnic_worker_items') and v: print(f"#   {oc:8s} {int(v)}")
expected=questions*2; actual=calls.get('stt',0)+calls.get('score',0)
print(f"# 증폭 = (stt {int(calls.get('stt',0))} + score {int(calls.get('score',0))}) / 기대 {expected} (문항 {questions} × 2) = {actual/expected:.2f}배  (SLO ≤ 1.65)")
PY
{
  echo "# 잡 상태: $(q "select status, count(*) from scoring_job where id in ($IDS) group by status" | tr '\t' '=' | tr '\n' ' ')"
  echo "# 문항 상태: $(q "select status, count(*) from scoring_job_item where job_id in ($IDS) group by status" | tr '\t' '=' | tr '\n' ' ')  ← FAILED가 사용자가 실패 카드를 본 문항"
  echo "# 문항 시도 분포: $(q "select attempts, count(*) from scoring_job_item where job_id in ($IDS) group by attempts order by attempts" | awk '{printf "%s회=%s ", $1, $2}')"
  echo "# 접수 시작 → 마지막 잡 완료: $(q "select timestampdiff(second,min(created_at),max(completed_at)) from scoring_job where id in ($IDS)")s"
  echo "# 접수→완료(초): $(q "select round(min(timestampdiff(microsecond,created_at,completed_at))/1e6,1), round(avg(timestampdiff(microsecond,created_at,completed_at))/1e6,1), round(max(timestampdiff(microsecond,created_at,completed_at))/1e6,1) from scoring_job where id in ($IDS)" | awk '{print "min "$1" / avg "$2" / max "$3}')"
  echo "# 서버 생존: $(curl -s "$BASE/actuator/health")"
} | tee -a "$OUT"
echo "→ $OUT"

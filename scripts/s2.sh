#!/usr/bin/env bash
# S2 — 외부 API 실패 주입 시 우리 쪽 호출 증폭 측정 (docs/performance/slo.md 검증 시나리오 S2)
#
# 콤보 N건을 제출하고, 전후의 opicnic_external_call_seconds_count 차이를 "실패가 없었다면 필요한 호출 수"로
# 나눈다. SLO: 증폭 ≤ 1.65배(실패 45%×최대 3회 시도의 이론값 1+0.45+0.45²), OOM 0, 서버 생존.
#
# 전제: 서버가 dev + mock + 실패 주입으로 떠 있어야 한다. 예)
#   SPRING_PROFILES_ACTIVE=dev STT_ENABLED=false LLM_ENABLED=false \
#   STT_MOCK_DELAY_MS=3000 LLM_MOCK_DELAY_MS=4500 \
#   STT_MOCK_429_RATE=0.3 STT_MOCK_5XX_RATE=0.1 STT_MOCK_TIMEOUT_RATE=0.05 \
#   LLM_MOCK_429_RATE=0.3 LLM_MOCK_5XX_RATE=0.1 LLM_MOCK_TIMEOUT_RATE=0.05 \
#   JAVA_TOOL_OPTIONS="-Xms2g -Xmx2g" ./gradlew bootRun
# 주의: 측정 중 컴파일하지 말 것 — DevTools가 컨텍스트를 핫 리스타트하면 지표가 리셋된다(2026-09-18 실제로 겪음).
#
# 사용: scripts/s2.sh [콤보 건수=50] [동시 실행 수=5] [라벨=before]
#   결과: docs/performance/<오늘>/s2-<라벨>.txt
set -euo pipefail

N=${1:-50}
PAR=${2:-5}
LABEL=${3:-before}
BASE=${BASE:-http://localhost:8080}
AUDIO=${AUDIO:-scripts/test_audio.webm}
TOPIC=${TOPIC:-MOVIE_WATCHING}
DIFFICULTY=${DIFFICULTY:-LEVEL_3}

OUT_DIR="docs/performance/$(date +%F)"
OUT="$OUT_DIR/s2-$LABEL.txt"
mkdir -p "$OUT_DIR"
curl -sf "$BASE/actuator/health" >/dev/null || { echo "서버가 $BASE 에 안 떠 있음" >&2; exit 1; }

# 외부 호출 지표 스냅샷: kind(stt/score/tag) × outcome 별 count, 그리고 재시도 카운터
snapshot() {
  # 첫 호출 전엔 지표 자체가 없어 grep이 1을 돌려준다 — 빈 스냅샷은 정상
  curl -s "$BASE/actuator/prometheus" | { grep '^opicnic_external_call_seconds_count\|^opicnic_retry_total' || true; } | sort
}

one_combo() {  # $1 = run index. stdout: "run attemptId questions http_code seconds failedIndexes"
  local JAR RESP CSRF TOKEN HEADER START ATTEMPT COUNT FILE_ARGS INDEXES T CODE FAILED
  JAR=$(mktemp); RESP=$(mktemp)
  CSRF=$(curl -s -c "$JAR" "$BASE/api/practice-attempts/csrf")
  TOKEN=$(jq -r .token <<<"$CSRF"); HEADER=$(jq -r .headerName <<<"$CSRF")
  START=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -X POST "$BASE/api/practice-attempts/start?topic=$TOPIC&difficulty=$DIFFICULTY")
  ATTEMPT=$(jq -r .attemptId <<<"$START"); COUNT=$(jq -r .questionCount <<<"$START")
  FILE_ARGS=(); for ((q=0; q<COUNT; q++)); do FILE_ARGS+=(-F "files=@$AUDIO;type=audio/webm"); done
  INDEXES=$(printf '%s,' $(seq 0 $((COUNT-1)))); INDEXES=${INDEXES%,}
  T=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -o "$RESP" -w '%{http_code} %{time_total}' \
    -F "questionIndexes=[$INDEXES]" "${FILE_ARGS[@]}" -X POST "$BASE/api/practice-attempts/$ATTEMPT/answers")
  CODE=${T%% *}; T=${T#* }
  FAILED=$(jq -c '.failedIndexes // []' "$RESP" 2>/dev/null || echo "parse-error")
  [ "$CODE" = 200 ] || FAILED="HTTP $CODE $(head -c 100 "$RESP" | tr '\n' ' ')"
  printf '%-4s %-37s %-3s %-5s %-9s %s\n' "$1" "$ATTEMPT" "$COUNT" "$CODE" "$T" "$FAILED"
  rm -f "$JAR" "$RESP"
}
export -f one_combo; export BASE AUDIO TOPIC DIFFICULTY

BEFORE=$(snapshot)
{
  echo "# S2 $LABEL — $(date '+%F %T')"
  echo "# base=$BASE combos=$N parallel=$PAR topic=$TOPIC/$DIFFICULTY audio=$AUDIO"
  echo "# 서버 환경(실패율·지연)은 기동 명령을 여기 손으로 적을 것:"
  echo "#   "
  echo "run  attemptId                             q   http  seconds   failed"
} > "$OUT"

START_TS=$(date +%s)
seq 1 "$N" | xargs -P "$PAR" -I{} bash -c 'one_combo {}' | sort -n | tee -a "$OUT"
ELAPSED=$(( $(date +%s) - START_TS ))
AFTER=$(snapshot)

# 후-전 차이를 kind×outcome 표로. 기대 호출 수(실패 0일 때) = 실제 문항 수 합 × (stt + score) — 콤보 패턴에 따라
# 문항이 2개인 것([9,10])도 있어 N×3으로 잡으면 과대. tag는 주입 대상이 아니라 제외.
QUESTIONS=$(awk '$1 ~ /^[0-9]+$/ {s+=$3} END{print s+0}' "$OUT")
python3 - "$BEFORE" "$AFTER" "$QUESTIONS" "$ELAPSED" <<'PY' | tee -a "$OUT"
import re,sys
def parse(txt):
    d={}
    for line in txt.splitlines():
        m=re.match(r'(\w+)\{([^}]*)\}\s+([0-9.eE+]+)',line)
        if not m: continue
        tags=dict(t.split('=',1) for t in m.group(2).replace('"','').split(','))
        d[(m.group(1),tags.get('kind'),tags.get('outcome') or tags.get('reason'))]=float(m.group(3))
    return d
b,a=parse(sys.argv[1]),parse(sys.argv[2]); questions=int(sys.argv[3]); elapsed=int(sys.argv[4])
diff={k:a[k]-b.get(k,0) for k in a}
print(f"\n# 소요 {elapsed}s")
print("# 외부 호출 (후-전)")
calls={}
for (name,kind,oc),v in sorted(diff.items()):
    if name.startswith('opicnic_external_call') and v: print(f"#   {kind:5s} {oc:8s} {int(v)}"); calls[kind]=calls.get(kind,0)+v
print("# 재시도 (후-전)")
for (name,kind,rs),v in sorted(diff.items()):
    if name.startswith('opicnic_retry') and v: print(f"#   {kind:5s} {rs:8s} {int(v)}")
expected=questions*2
actual=calls.get('stt',0)+calls.get('score',0)
print(f"# 증폭 = (stt {int(calls.get('stt',0))} + score {int(calls.get('score',0))}) / 기대 {expected} (문항 {questions} × 2) = {actual/expected:.2f}배  (SLO ≤ 1.65)")
PY

echo "# 서버 생존: $(curl -s "$BASE/actuator/health")" | tee -a "$OUT"
echo "→ $OUT"

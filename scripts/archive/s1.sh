#!/usr/bin/env bash
# [보관] 동기 채점 경로(/answers·/finalize)는 2026-09-21에 제거됨 — 전환 전 측정 기록용, 현재는 실행 불가
# S1 — 모의고사 15문항 제출 대기 시간 측정 (docs/performance/slo.md 검증 시나리오 S1)
#
# 전환 전(동기 구조)에는 응답 = 처리 완료라 "접수"와 "완료"가 같은 값이다. 이 스크립트는 그 값
# (사용자가 실제로 기다리는 시간)을 여러 번 재서 파일로 남긴다. SLO 판정용이 아니라 현상 기록용.
# 이탈·재시작 시나리오와 DB 검증은 전환 후 구현 때 붙인다 (slo.md 전/후 표 아래 설명 참고).
#
# 전제: 서버가 dev 프로파일 + mock으로 떠 있어야 한다. 예)
#   SPRING_PROFILES_ACTIVE=dev STT_ENABLED=false LLM_ENABLED=false \
#   STT_MOCK_DELAY_MS=3000 LLM_MOCK_DELAY_MS=4500 \
#   JAVA_TOOL_OPTIONS="-Xms2g -Xmx2g" ./gradlew bootRun
#
# 사용: scripts/s1.sh [반복횟수=5] [라벨=before]
#   결과: docs/performance/<오늘>/s1-<라벨>.txt
set -euo pipefail

RUNS=${1:-5}
LABEL=${2:-before}
BASE=${BASE:-http://localhost:8080}
AUDIO=${AUDIO:-scripts/test_audio.webm}

OUT_DIR="docs/performance/$(date +%F)"
OUT="$OUT_DIR/s1-$LABEL.txt"
mkdir -p "$OUT_DIR"

if ! curl -sf "$BASE/actuator/health" >/dev/null; then
  echo "서버가 $BASE 에 안 떠 있음" >&2; exit 1
fi

{
  echo "# S1 $LABEL — $(date '+%F %T')"
  echo "# base=$BASE audio=$AUDIO runs=$RUNS"
  echo "# 서버 환경(mock 지연 등)은 기동 명령을 여기 손으로 적을 것:"
  echo "#   "
  echo "run  attemptId                             questions  answers_s  finalize_s  failed"
} > "$OUT"

for i in $(seq 1 "$RUNS"); do
  JAR=$(mktemp)
  CSRF=$(curl -s -c "$JAR" "$BASE/api/practice-attempts/csrf")
  TOKEN=$(jq -r .token <<<"$CSRF"); HEADER=$(jq -r .headerName <<<"$CSRF")

  START=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -X POST "$BASE/api/practice-attempts/start-mock")
  ATTEMPT=$(jq -r .attemptId <<<"$START"); COUNT=$(jq -r .questionCount <<<"$START")

  FILE_ARGS=(); for ((q=0; q<COUNT; q++)); do FILE_ARGS+=(-F "files=@$AUDIO;type=audio/webm"); done
  INDEXES=$(printf '%s,' $(seq 0 $((COUNT-1)))); INDEXES=${INDEXES%,}   # macOS seq -s는 끝에 구분자를 붙임

  RESP=$(mktemp)
  ANSWERS_T=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -o "$RESP" -w '%{http_code} %{time_total}' \
    -F "questionIndexes=[$INDEXES]" "${FILE_ARGS[@]}" \
    -X POST "$BASE/api/practice-attempts/$ATTEMPT/answers")
  ANSWERS_CODE=${ANSWERS_T%% *}; ANSWERS_T=${ANSWERS_T#* }
  FAILED=$(jq -c '.failedIndexes // []' "$RESP" 2>/dev/null || echo "parse-error")
  if [ "$ANSWERS_CODE" != 200 ]; then FAILED="HTTP $ANSWERS_CODE $(head -c 120 "$RESP")"; fi

  FINALIZE_T=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -o /dev/null -w '%{time_total}' \
    -X POST "$BASE/api/practice-attempts/$ATTEMPT/finalize")

  printf '%-4s %-37s %-10s %-10s %-11s %s\n' "$i" "$ATTEMPT" "$COUNT" "$ANSWERS_T" "$FINALIZE_T" "$FAILED" | tee -a "$OUT"
  rm -f "$JAR" "$RESP"
done

echo "→ $OUT"

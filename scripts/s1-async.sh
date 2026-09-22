#!/usr/bin/env bash
# S1 (전환 후) — 비동기 경로로 모의고사 15문항 제출: 접수 응답 시간 + 완료 시간 + DB 검증.
#   start-mock → upload-urls → R2 PUT ×15 → POST /api/scoring-jobs(202) → 폴링 → DB에서 문항 상태·feedback_result 수 확인
# 전제: 서버가 dev + mock 지연 + R2 키(.env)로 떠 있어야 한다 (scripts/archive/s1.sh 헤더 참고). 측정 중 컴파일 금지.
# 사용: scripts/s1-async.sh [반복=1] [라벨=after]  → docs/performance/<오늘>/s1-async-<라벨>.txt
#   MODE=client-kill : 제출 직후 폴링하지 않고 종료(이탈) → 30s 뒤 DB만 확인
#   MODE=server-restart : 제출 KILL_DELAY초(기본 0) 뒤 RESTART_CMD 실행(기본: 앱 프로세스 kill) → 서버 복귀 대기 → 폴링
#                         KILL_DELAY=6 이면 문항들이 PROCESSING(STT 중)인 채로 죽는다 — 기동 시 회수 경로 검증
set -euo pipefail
RUNS=${1:-1}; LABEL=${2:-after}; MODE=${MODE:-normal}
BASE=${BASE:-http://localhost:8080}; AUDIO=${AUDIO:-scripts/test_audio.webm}
DB_PASS=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-); MYSQL="docker exec ${MYSQL_CONTAINER:-opicnic_mysql_s1} mysql -uroot -p$DB_PASS opicnic -N -e"
OUT_DIR="docs/performance/$(date +%F)"; OUT="$OUT_DIR/s1-async-$LABEL.txt"; mkdir -p "$OUT_DIR"
curl -sf "$BASE/actuator/health" >/dev/null || { echo "서버 없음" >&2; exit 1; }

{ echo "# S1-async $LABEL mode=$MODE kill_delay=${KILL_DELAY:-0} — $(date '+%F %T')"; echo "# base=$BASE audio=$AUDIO runs=$RUNS"; echo "# 서버 기동 명령을 여기 손으로 적을 것:"; echo "#   ";
  echo "run  attemptId                             submit_http  submit_s  upload_s  complete_s  job_status               done  failed  feedback_rows"; } > "$OUT"

for i in $(seq 1 "$RUNS"); do
  JAR=$(mktemp); CSRF=$(curl -s -c "$JAR" "$BASE/api/practice-attempts/csrf")
  TOKEN=$(jq -r .token <<<"$CSRF"); HEADER=$(jq -r .headerName <<<"$CSRF")
  A=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -X POST "$BASE/api/practice-attempts/start-mock" | jq -r .attemptId)
  SIZE=$(stat -f%z "$AUDIO")
  BODY=$(python3 -c "import json;print(json.dumps([{'index':i,'size':$SIZE,'contentType':'audio/webm'} for i in range(15)]))")
  U0=$(date +%s.%N)
  curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -H 'Content-Type: application/json' -d "$BODY" \
    -X POST "$BASE/api/practice-attempts/$A/upload-urls" | jq -r '.[].url' | while read -r u; do
      curl -s -o /dev/null -X PUT -H 'Content-Type: audio/webm' --data-binary @"$AUDIO" "$u"; done
  UPLOAD_S=$(python3 -c "import time;print(f'{time.time()-$U0:.2f}')")

  T0=$(date +%s.%N)
  SUB=$(curl -s -b "$JAR" -c "$JAR" -H "$HEADER: $TOKEN" -H 'Content-Type: application/json' -d "{\"attemptId\":\"$A\"}" \
    -o /dev/null -w '%{http_code} %{time_total}' -X POST "$BASE/api/scoring-jobs")
  SUB_CODE=${SUB%% *}; SUB_S=${SUB#* }
  if [ "$SUB_CODE" != 202 ]; then echo "submit 실패 HTTP $SUB_CODE — 서버가 재기동 중이거나(컴파일 후 DevTools) attempt가 만료됨. 중단" | tee -a "$OUT"; exit 1; fi

  case "$MODE" in
    client-kill)   sleep 30 ;;   # 클라이언트는 떠났다. 서버가 혼자 끝내는지 DB로만 본다
    server-restart) sleep "${KILL_DELAY:-0}"; ${RESTART_CMD:-pkill -f OpicnicApplication}; sleep 5
                    until curl -sf -m 2 "$BASE/actuator/health" >/dev/null; do sleep 3; done ;;
  esac
  if [ "$MODE" != client-kill ]; then
    until curl -s -m 3 "$BASE/api/scoring-jobs/$A" | jq -e '.status | test("COMPLETED")' >/dev/null; do sleep 2; done
  fi
  COMPLETE_S=$(python3 -c "import time;print(f'{time.time()-$T0:.1f}')")

  JOB=$($MYSQL "select status from scoring_job where id='$A'")
  DONE=$($MYSQL "select count(*) from scoring_job_item where job_id='$A' and status='DONE'")
  FAILED=$($MYSQL "select count(*) from scoring_job_item where job_id='$A' and status='FAILED'")
  ROWS=$($MYSQL "select count(*) from feedback_result where attempt_id='$A'")
  printf '%-4s %-37s %-12s %-9s %-9s %-11s %-24s %-5s %-7s %s\n' "$i" "$A" "$SUB_CODE" "$SUB_S" "$UPLOAD_S" "$COMPLETE_S" "$JOB" "$DONE" "$FAILED" "$ROWS" | tee -a "$OUT"
  rm -f "$JAR"
done
echo "→ $OUT"

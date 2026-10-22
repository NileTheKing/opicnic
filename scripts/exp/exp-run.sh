#!/usr/bin/env bash
# 실험 1회 실행 래퍼 (VM ~/opicnic-exp 에서 실행). 변수 통제(README "변수 통제")를 강제한다:
#   매 회 새 앱 / 예열 구간 분리 / 테이블 truncate / mock 기본값 / R2 버킷 비우기 / CPU 조용함 확인 / 중단 조건 감시
# 사용: scripts/exp/exp-run.sh <run_id> <stage 1|2> <slots> <circuit on|off>
#   1단계: 예열 60s + 측정 300s. 2단계: 예열 60s -> 완전 장애 300s -> 복구 후 대기 줄 0까지(상한 1200s) 계속 유입.
#   3단계: 예열 120s -> 제공자 용량 40% 300s -> 복구 후 대기 줄이 평소 수준(복구 전 30초 평균 + 50% + 20건 이하, 연속 3표본)까지(상한 1200s) 계속 유입.
# 사용(3단계): scripts/exp/exp-run.sh <run_id> 3 <slots> on <ladder L0|L1|L1.5|L2|L3|L4a|L4b>
# 환경 변수: STORAGE=r2(기본, 1·2단계)|mem(3단계 기본, 메모리 저장소). 스모크: STORAGE=mem 으로 1단계(stage=1)를 돌리고 LADDER=L2 RATE=13.2
# 종료 코드: 0 정상, 2 중단 조건(실험 컨테이너 정리됨), 3 사전 점검 실패
set -uo pipefail
cd "$(dirname "$0")/../.."          # ~/opicnic-exp
RUN=${1:?run_id}; STAGE=${2:?stage}; SLOTS=${3:?slots}; CIRCUIT=${4:?on|off}; LADDER=${5:-${LADDER:-none}}
if [ "$STAGE" = 3 ]; then WARM_DEF=120; RATE_DEF=13.2; STORAGE=${STORAGE:-mem}; else WARM_DEF=60; RATE_DEF=6.7; STORAGE=${STORAGE:-r2}; fi
WARM_S=${WARM_S:-$WARM_DEF}; MEASURE_S=${MEASURE_S:-300}; OUTAGE_S=${OUTAGE_S:-300}; MAX_RECOVER_S=${MAX_RECOVER_S:-360}
RATE=${RATE:-$RATE_DEF}
# 3단계 사다리 -> 설정. 제공자 한도는 STT 분당 5,000 / LLM 분당 10,000 (시즌 필요량의 약 1.8배)
BACKOFF_MODE=full-jitter; LIMITER_MODE=off; MOCK_HEADERS=false; STT_PM=0; LLM_PM=0
case "$LADDER" in
  none) ;;
  L0) BACKOFF_MODE=immediate ;;
  L1) BACKOFF_MODE=exponential ;;
  L1.5) BACKOFF_MODE=exponential-small-jitter ;;
  L2) BACKOFF_MODE=full-jitter ;;
  L3) LIMITER_MODE=fixed ;;
  L4a) LIMITER_MODE=aimd ;;
  L4b) LIMITER_MODE=header; MOCK_HEADERS=true ;;
  *) echo "알 수 없는 사다리 단계: $LADDER"; exit 3 ;;
esac
if [ "$LADDER" != none ]; then STT_PM=5000; LLM_PM=10000; fi
OUT=$HOME/opicnic-exp-results/$RUN; mkdir -p "$OUT"; chmod 777 "$OUT"
COMPOSE="docker compose -p opicnic-exp -f docker-compose.exp.yml"
BUCKET=opicnic-exp   # 이 스크립트는 이 버킷만 비운다(하드코딩)
set -a; . ./.env; set +a
export WORKER_CONCURRENCY=$SLOTS
export OPICNIC_WORKER_BACKOFF_MODE=$BACKOFF_MODE OPICNIC_OUTBOUND_LIMITER_MODE=$LIMITER_MODE
export OPICNIC_OUTBOUND_LIMITER_STT_PER_MINUTE=5000 OPICNIC_OUTBOUND_LIMITER_LLM_PER_MINUTE=10000
if [ "$STORAGE" = mem ]; then export APP_R2_ACCOUNT_ID="" OPICNIC_STORAGE_MEMORY_UPLOAD_BASE=http://app:8080; fi
if [ "$CIRCUIT" = off ]; then export OPICNIC_WORKER_CIRCUIT_FAILURE_RATIO=2; else export OPICNIC_WORKER_CIRCUIT_FAILURE_RATIO=0.8; fi
# 환경 변수 COUNT_RATE_LIMITED=false 면 서킷이 429를 창에서 제외(3단계 추가 실험). 기본 true
export OPICNIC_WORKER_CIRCUIT_COUNT_RATE_LIMITED=${COUNT_RATE_LIMITED:-true}
# 환경 변수 COUNT_REJECTED=true 면 가짜 제공자가 거절(429)한 요청도 한도에 친다(opicnic.mock.count-rejected, 3단계 새 가정). 기본 false
COUNT_REJECTED=${COUNT_REJECTED:-false}
export AWS_ACCESS_KEY_ID=$R2_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY=$R2_SECRET_ACCESS_KEY AWS_DEFAULT_REGION=auto
R2EP="https://$R2_ACCOUNT_ID.r2.cloudflarestorage.com"
APP=http://127.0.0.1:18081
STOP=$OUT/STOP; ABORT=$OUT/ABORT; rm -f "$STOP" "$ABORT"; : > "$OUT/marks.env"
log() { echo "$(date '+%F %T') $*" | tee -a "$OUT/run.log"; }
mark() { echo "$1=$2" >> "$OUT/marks.env"; }
mysql_cid() { docker ps -qf name=opicnic-exp-mysql-1; }
sql() { docker exec "$(mysql_cid)" mysql -uroot -pexp-root-pass opicnic -N -B -e "$1" 2>/dev/null; }
r2() { docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_DEFAULT_REGION amazon/aws-cli --endpoint-url "$R2EP" "$@"; }
snap() { curl -s -m 10 "$APP/actuator/prometheus" > "$OUT/prom_$1.txt"; mark "DBNOW_$1" "$(sql 'select now(6)' | tr ' ' '_')"; mark "EPOCH_$1" "$(date +%s.%N)"; }
queued() { curl -s -m 5 "$APP/actuator/prometheus" | awk '/^opicnic_worker_queued /{printf "%d", $2}'; }
inflight() { curl -s -m 5 "$APP/actuator/prometheus" | awk '/^opicnic_worker_in_flight /{printf "%d", $2}'; }

cleanup() {
  touch "$STOP"
  docker kill opicnic-exp-k6 >/dev/null 2>&1; docker rm -f opicnic-exp-k6 >/dev/null 2>&1
  $COMPOSE rm -sf app >/dev/null 2>&1
}
abort() { log "ABORT: $1"; echo "$1" > "$ABORT"; cleanup; mark RESULT abort; exit 2; }
check_abort() { [ -f "$ABORT" ] && abort "감시: $(cat "$ABORT")"; }
trap 'cleanup' EXIT

log "== run $RUN stage=$STAGE slots=$SLOTS circuit=$CIRCUIT rate=$RATE ladder=$LADDER storage=$STORAGE backoff=$BACKOFF_MODE limiter=$LIMITER_MODE"
mark COUNT_RATE_LIMITED "$OPICNIC_WORKER_CIRCUIT_COUNT_RATE_LIMITED"; mark COUNT_REJECTED "$COUNT_REJECTED"; mark LADDER "$LADDER"; mark STORAGE "$STORAGE"; mark BACKOFF_MODE "$BACKOFF_MODE"; mark LIMITER_MODE "$LIMITER_MODE"
mark RUN "$RUN"; mark STAGE "$STAGE"; mark SLOTS "$SLOTS"; mark CIRCUIT "$CIRCUIT"; mark WARM_S $WARM_S; mark MEASURE_S $MEASURE_S
mark OUTAGE_S $OUTAGE_S; mark MAX_RECOVER_S $MAX_RECOVER_S; mark RATE $RATE

# ---- 1. 사전 점검
docker kill opicnic-exp-k6 >/dev/null 2>&1; docker rm -f opicnic-exp-k6 >/dev/null 2>&1
$COMPOSE rm -sf app >/dev/null 2>&1
MEM=$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo); mark MEM_PRE_MB "$MEM"
[ "$MEM" -ge 2048 ] || { log "여유 메모리 ${MEM}MB < 2048MB"; trap - EXIT; exit 3; }
CODE=$(curl -s -o /dev/null -m 10 -w '%{http_code}' https://opicnic.xyz/); [ "${CODE:0:1}" = 2 ] || [ "${CODE:0:1}" = 3 ] || { log "운영 사이트 $CODE"; trap - EXIT; exit 3; }
PROD_ERR_BASE=$(date +%s)
log "CPU 조용함 대기 (60초 평균 < 10% 이고 5초 표본 최대 < 25%, 최대 25분)"
QUIET=0; DEADLINE=$(( $(date +%s) + 1500 ))
while [ "$(date +%s)" -lt "$DEADLINE" ]; do
  read -r CM CX < <(python3 scripts/exp/exp-cpu.py 60 5)
  log "  CPU 평균 $CM% 최대 $CX%"
  if awk -v m="$CM" -v x="$CX" 'BEGIN{exit !(m<10 && x<25)}'; then QUIET=1; mark CPU_PRE_MEAN "$CM"; mark CPU_PRE_MAX "$CX"; break; fi
done
[ "$QUIET" = 1 ] || { log "CPU가 조용해지지 않음"; trap - EXIT; exit 3; }
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' 2>/dev/null | grep -v opicnic-exp > "$OUT/docker_stats_pre.txt"

# ---- 2. 상태 초기화
$COMPOSE up -d mysql >/dev/null 2>&1
until [ "$(docker inspect -f '{{.State.Health.Status}}' "$(mysql_cid)" 2>/dev/null)" = healthy ]; do sleep 2; done
TBL=$(sql "select count(*) from information_schema.tables where table_schema='opicnic' and table_name in ('scoring_job','scoring_job_item','feedback_result','feedback_tag')")
if [ "$TBL" = 4 ]; then
  sql "set foreign_key_checks=0; truncate table feedback_tag; truncate table feedback_result; truncate table scoring_job_item; truncate table scoring_job; set foreign_key_checks=1;" >/dev/null
  LEFT=$(sql "select (select count(*) from scoring_job)+(select count(*) from scoring_job_item)+(select count(*) from feedback_result)+(select count(*) from feedback_tag)")
  [ "$LEFT" = 0 ] || abort "truncate 실패 (남은 행 $LEFT)"
else
  log "채점 테이블이 아직 없음(최초 기동 전) - 앱이 만든다"
fi
if [ "$STORAGE" = r2 ]; then
  log "R2 $BUCKET 비우기"
  r2 s3 rm "s3://$BUCKET" --recursive --only-show-errors >/dev/null 2>&1
  OBJ=$(r2 s3 ls "s3://$BUCKET" --summarize 2>/dev/null | awk '/Total Objects/{print $3}')
  [ "$OBJ" = 0 ] || abort "R2 버킷이 비워지지 않음 (객체 ${OBJ:-?})"
else
  log "메모리 저장소 - R2 사용 안 함"
fi

# ---- 3. 새 앱
$COMPOSE up -d --force-recreate --no-deps app >/dev/null 2>&1
for i in $(seq 1 90); do curl -sf -m 3 "$APP/actuator/health" >/dev/null 2>&1 && break; sleep 2; done
curl -sf -m 3 "$APP/actuator/health" >/dev/null 2>&1 || abort "앱 기동 실패"
log "앱 기동 완료 (slots=$SLOTS circuit=$CIRCUIT)"
CSRF=$(curl -s -c "$OUT/csrf.jar" "$APP/api/practice-attempts/csrf")
TOKEN=$(python3 -c 'import json,sys;print(json.loads(sys.argv[1])["token"])' "$CSRF"); HDR=$(python3 -c 'import json,sys;print(json.loads(sys.argv[1])["headerName"])' "$CSRF")
mock() { curl -sf -m 5 -b "$OUT/csrf.jar" -H "$HDR: $TOKEN" -X POST "$APP/api/practice-attempts/mock-provider?down=$1&limitPerSecond=0&sttPerMinute=$STT_PM&llmPerMinute=$LLM_PM&capacity=${2:-1.0}&headers=$MOCK_HEADERS&countRejected=$COUNT_REJECTED"; }
log "mock 기본값 확인: $(mock false)"

# ---- 4. 샘플러 + 부하
python3 scripts/exp/exp-sampler.py "$OUT/sampler.tsv" "$STOP" "$ABORT" "$(mysql_cid)" 5 >/dev/null 2>&1 &
SAMPLER=$!
sleep 6
if [ "$STAGE" = 1 ]; then TOTAL_S=$((WARM_S + MEASURE_S)); else TOTAL_S=$((WARM_S + OUTAGE_S + MAX_RECOVER_S)); fi
TOPICS=MOVIE_WATCHING,COOKING,SHOPPING,TV_WATCHING,MUSIC_LISTENING,READING,WALKING,FOOD,TECHNOLOGY,HOTEL_STAY,FITNESS_GYM,SINGING
TYPE_PAIRS=COOKING:TYPE_1,SHOPPING:TYPE_3,READING:TYPE_5,WALKING:TYPE_2,FOOD:TYPE_7,MOVIE_WATCHING:TYPE_4,MUSIC_LISTENING:TYPE_6,TECHNOLOGY:TYPE_8,TV_WATCHING:TYPE_9
docker run -d --name opicnic-exp-k6 --network opicnic-exp_default \
  -v "$PWD/scripts/exp/exp-k6.js:/k6/exp-k6.js:ro" -v "$PWD/exp_recording.webm:/data/exp_recording.webm:ro" -v "$OUT:/out" \
  -e PUT_AUTH=$([ "$STORAGE" = mem ] && echo 1 || echo 0) \
  -e BASE=http://app:8080 -e WARM_S=$WARM_S -e TOTAL_S=$TOTAL_S -e RATE=$RATE -e TOPICS=$TOPICS -e TYPE_PAIRS=$TYPE_PAIRS \
  grafana/k6:latest run --no-usage-report /k6/exp-k6.js >/dev/null
for i in $(seq 1 120); do docker logs opicnic-exp-k6 2>&1 | grep -q 'running (' && break; sleep 1; check_abort; done
T0=$(date +%s); mark T0 "$T0"; log "k6 시작 (예열 ${WARM_S}s)"
wait_until() { while [ "$(date +%s)" -lt "$1" ]; do sleep 1; check_abort; done; }

wait_until $((T0 + WARM_S)); snap A; mark T_MEASURE_START "$(date +%s)"; log "예열 끝, 측정 시작"
if [ "$STAGE" = 1 ]; then
  wait_until $((T0 + WARM_S + MEASURE_S)); snap B; mark T_MEASURE_END "$(date +%s)"; log "측정 끝, 유입 종료 후 마무리 대기"
  timeout 90 docker wait opicnic-exp-k6 >/dev/null 2>&1
  for i in $(seq 1 18); do [ "$(queued)" = 0 ] && [ "$(inflight)" = 0 ] && break; sleep 5; check_abort; done
  mark LEFT_QUEUED "$(queued)"; mark LEFT_INFLIGHT "$(inflight)"
elif [ "$STAGE" = 3 ]; then
  # 예열 끝 = 용량 감소 시작. 평소 대기 줄 수준은 그 직전 30초 샘플러 평균
  BASEQ=$(awk -F'\t' -v t=$(( $(date +%s) - 30 )) '$2=="opicnic_worker_queued" && $1>=t {s+=$3;n++} END{printf "%d", n ? s/n : 0}' "$OUT/sampler.tsv")
  THRESH=$(( BASEQ + BASEQ / 2 + 20 )); mark BASE_Q "$BASEQ"; mark NORMAL_Q_THRESH "$THRESH"
  T_DOWN=$(date +%s); R=$(mock false 0.4) || abort "mock 용량 감소 실패"; mark T_DOWN "$T_DOWN"; log "용량 40% 시작 (평소 대기 줄 $BASEQ, 복귀 기준 <= $THRESH): $R"
  wait_until $((T_DOWN + OUTAGE_S)); snap B
  T_UP=$(date +%s); R=$(mock false 1.0) || abort "mock 용량 회복 실패"; mark T_UP "$T_UP"; log "용량 회복: $R"
  mark Q_AT_UP "$(queued)"; mark BACKOFF_AT_UP "$(sql "select count(*), coalesce(max(timestampdiff(second, now(6), next_attempt_at)),0) from scoring_job_item where status='QUEUED' and next_attempt_at>now(6)" | tr '\t' ',')"
  ZERO=0; DRAINED=0
  while [ $(( $(date +%s) - T_UP )) -lt "$MAX_RECOVER_S" ]; do
    sleep 5; check_abort
    if [ "$(queued)" -le "$THRESH" ] 2>/dev/null; then ZERO=$((ZERO + 1)); else ZERO=0; fi
    [ "$ZERO" -ge 3 ] && { DRAINED=1; break; }
  done
  T_DRAIN=$(date +%s); mark T_DRAIN "$T_DRAIN"; mark DRAINED "$DRAINED"; snap C
  log "회복 후 $((T_DRAIN - T_UP))s: 평소 수준 도달=$DRAINED (queued=$(queued))"
  docker kill -s INT opicnic-exp-k6 >/dev/null 2>&1; timeout 120 docker wait opicnic-exp-k6 >/dev/null 2>&1
  for i in $(seq 1 24); do [ "$(queued)" = 0 ] && [ "$(inflight)" = 0 ] && break; sleep 5; check_abort; done
  mark LEFT_QUEUED "$(queued)"; mark LEFT_INFLIGHT "$(inflight)"
else
  T_DOWN=$(date +%s); R=$(mock true) || abort "mock 장애 켜기 실패"; mark T_DOWN "$T_DOWN"; log "장애 시작: $R"
  wait_until $((T_DOWN + OUTAGE_S)); snap B
  T_UP=$(date +%s); R=$(mock false) || abort "mock 장애 끄기 실패"; mark T_UP "$T_UP"; log "복구: $R"
  mark Q_AT_UP "$(queued)"; mark BACKOFF_AT_UP "$(sql "select count(*), coalesce(max(timestampdiff(second, now(6), next_attempt_at)),0) from scoring_job_item where status='QUEUED' and next_attempt_at>now(6)" | tr '\t' ',')"
  ZERO=0; DRAINED=0
  while [ $(( $(date +%s) - T_UP )) -lt "$MAX_RECOVER_S" ]; do
    sleep 5; check_abort
    if [ "$(queued)" = 0 ]; then ZERO=$((ZERO + 1)); else ZERO=0; fi
    [ "$ZERO" -ge 3 ] && { DRAINED=1; break; }
  done
  T_DRAIN=$(date +%s); mark T_DRAIN "$T_DRAIN"; mark DRAINED "$DRAINED"; snap C
  log "복구 후 $((T_DRAIN - T_UP))s: 대기 줄 0 도달=$DRAINED (queued=$(queued))"
  docker kill -s INT opicnic-exp-k6 >/dev/null 2>&1; timeout 120 docker wait opicnic-exp-k6 >/dev/null 2>&1
  for i in $(seq 1 24); do [ "$(queued)" = 0 ] && [ "$(inflight)" = 0 ] && break; sleep 5; check_abort; done
  mark LEFT_QUEUED "$(queued)"; mark LEFT_INFLIGHT "$(inflight)"
fi
snap END; mark T_END "$(date +%s)"

# ---- 5. 수집
touch "$STOP"; wait "$SAMPLER" 2>/dev/null
docker logs opicnic-exp-k6 > "$OUT/k6.log" 2>&1
docker logs "$($COMPOSE ps -q app)" 2>&1 | gzip > "$OUT/app.log.gz"
sql "select j.id, j.mode, j.status, j.created_at, j.completed_at, (select count(*) from scoring_job_item i where i.job_id=j.id) from scoring_job j" > "$OUT/jobs.tsv"
sql "select status, count(*), sum(attempts), max(attempts) from scoring_job_item group by status" > "$OUT/items_by_status.tsv"
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' 2>/dev/null | grep -v opicnic-exp > "$OUT/docker_stats_post.txt"
mark MEM_POST_MB "$(awk '/MemAvailable/{print int($2/1024)}' /proc/meminfo)"
PE=$(docker logs opicnic_app --since "$PROD_ERR_BASE" 2>&1 | grep -c ' ERROR ')
mark PROD_ERROR_LINES "$PE"
[ "$PE" = 0 ] || log "경고: 운영 앱 ERROR 로그 $PE줄"
check_abort
mark RESULT ok
cleanup
python3 scripts/exp/exp-collect.py "$OUT" > "$OUT/summary.txt" 2>&1
log "== 완료. $OUT/summary.txt"
trap - EXIT
exit 0

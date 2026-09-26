#!/usr/bin/env bash
# 재시도 폭주 실험 — 외부 장애가 났다가 풀리는 순간, 쌓인 재시도가 제공자에게 한꺼번에 몰리는가.
#
# S2(확률 실패 주입)는 몇 건을 부르든 실패율이 같아 폭주가 안 생긴다. 여기선 MockProvider가 "초당 LIMIT건만 받고
# 넘으면 즉시 429"라서 몰려서 부를수록 더 실패한다. 시나리오:
#   0초          제공자 완전 장애(모든 호출 즉시 503) 상태에서 콤보 N건 접수(업로드 포함)
#   접수 끝+DOWN_S초  복구 — 단, 초당 LIMIT건 한도(STT·채점·태깅 합산)
#   → 잡이 전부 끝날 때까지 1초마다 /actuator/prometheus의 외부 호출 누적값을 기록
#
# 대조군과 실험군은 서버 설정만 다르다(.claude/launch.json storm-a / storm-b, 서킷은 둘 다 끔):
#   A 보호 없음: opicnic.worker.backoff-enabled=false(실패하면 다음 폴링 1초 뒤 재시도), 동시 처리 1000
#   B 지금 설계: 지수 백오프 + jitter, 동시 처리 60
#
# 사용: scripts/retry-storm.sh <라벨> [콤보=30] [DOWN_S=60] [LIMIT=10]  → docs/performance/<오늘>/retry-storm-<라벨>.{csv,txt}
# 주의: 측정 중 컴파일하지 말 것(DevTools 재시작으로 지표 리셋). 서버를 바꾼 뒤엔 새 PID의 "Started"를 확인하고 돌릴 것.
set -euo pipefail
LABEL=${1:?라벨}; N=${2:-30}; DOWN_S=${3:-60}; LIMIT=${4:-10}; PAR=${PAR:-30}
BASE=${BASE:-http://localhost:8080}; AUDIO=${AUDIO:-scripts/test_audio.webm}
TOPIC=${TOPIC:-MOVIE_WATCHING}; DIFFICULTY=${DIFFICULTY:-LEVEL_3}
DB_PASS=$(grep '^DB_PASSWORD=' .env | cut -d= -f2-)
q() { docker exec "${MYSQL_CONTAINER:-opicnic_mysql_s1}" mysql -uroot -p"$DB_PASS" opicnic -N -e "$1" 2>/dev/null; }
OUT_DIR="docs/performance/$(date +%F)"; CSV="$OUT_DIR/retry-storm-$LABEL.csv"; OUT="$OUT_DIR/retry-storm-$LABEL.txt"; mkdir -p "$OUT_DIR"
curl -sf "$BASE/actuator/health" >/dev/null || { echo "서버 없음" >&2; exit 1; }

JAR=$(mktemp); CSRF=$(curl -s -c "$JAR" "$BASE/api/practice-attempts/csrf")
TOKEN=$(jq -r .token <<<"$CSRF"); HEADER=$(jq -r .headerName <<<"$CSRF")
provider() { curl -sf -b "$JAR" -H "$HEADER: $TOKEN" -X POST "$BASE/api/practice-attempts/mock-provider?$1" >/dev/null; }

one_combo() {  # $1 = run index. stdout: "run attemptId questions submit_http"
  local J C T H START A COUNT SIZE BODY SUB
  J=$(mktemp); C=$(curl -s -c "$J" "$BASE/api/practice-attempts/csrf")
  T=$(jq -r .token <<<"$C"); H=$(jq -r .headerName <<<"$C")
  START=$(curl -s -b "$J" -c "$J" -H "$H: $T" -X POST "$BASE/api/practice-attempts/start?topic=$TOPIC&difficulty=$DIFFICULTY")
  A=$(jq -r .attemptId <<<"$START"); COUNT=$(jq -r .questionCount <<<"$START"); SIZE=$(stat -f%z "$AUDIO")
  BODY=$(python3 -c "import json;print(json.dumps([{'index':i,'size':$SIZE,'contentType':'audio/webm'} for i in range($COUNT)]))")
  curl -s -b "$J" -c "$J" -H "$H: $T" -H 'Content-Type: application/json' -d "$BODY" \
    -X POST "$BASE/api/practice-attempts/$A/upload-urls" | jq -r '.[].url' | while read -r u; do
      curl -s -o /dev/null -X PUT -H 'Content-Type: audio/webm' --data-binary @"$AUDIO" "$u"; done
  SUB=$(curl -s -b "$J" -c "$J" -H "$H: $T" -H 'Content-Type: application/json' -d "{\"attemptId\":\"$A\"}" \
    -o /dev/null -w '%{http_code}' -X POST "$BASE/api/scoring-jobs")
  echo "$1 $A $COUNT $SUB"; rm -f "$J"
}
export -f one_combo; export BASE AUDIO TOPIC DIFFICULTY

# 1초 샘플러: 외부 호출 누적값(모든 kind 합산, outcome별)과 워커 처리 중 수
STOP=$(mktemp -u)
python3 - "$BASE" "$CSV" "$STOP" <<'PY' &
import os, re, sys, time, urllib.request
base, csv, stop = sys.argv[1:4]
t0 = time.time()
with open(csv, "w") as f:
    f.write("t,ok,r429,r5xx,other,in_flight\n")
    while not os.path.exists(stop):
        txt = urllib.request.urlopen(base + "/actuator/prometheus").read().decode()
        c = {"ok": 0, "429": 0, "5xx": 0, "other": 0}; inflight = 0
        for line in txt.splitlines():
            m = re.match(r'opicnic_external_call_seconds_count\{([^}]*)\}\s+([0-9.eE+]+)', line)
            if m:
                o = re.search(r'outcome="([^"]+)"', m.group(1)).group(1)
                c[o if o in c else "other"] += float(m.group(2))
            elif line.startswith("opicnic_worker_in_flight"):
                inflight = float(line.split()[-1])
        f.write(f"{time.time()-t0:.1f},{c['ok']:.0f},{c['429']:.0f},{c['5xx']:.0f},{c['other']:.0f},{inflight:.0f}\n"); f.flush()
        time.sleep(max(0, 1 - (time.time() - t0) % 1))
PY
SAMPLER=$!
trap 'touch "$STOP"; provider "down=false&limitPerSecond=0" || true' EXIT
sleep 2

provider "down=true&limitPerSecond=$LIMIT"; T_DOWN=$(date +%s)
echo "[$(date +%T)] 장애 시작 — 콤보 $N건 접수"
RUNS=$(seq 1 "$N" | xargs -P "$PAR" -I{} bash -c 'one_combo {}')
IDS=$(awk '{printf "%s\047%s\047", (n++?",":""), $2}' <<<"$RUNS")
QUESTIONS=$(awk '{s+=$3} END{print s+0}' <<<"$RUNS"); NON202=$(awk '$4 != 202' <<<"$RUNS" | wc -l | tr -d ' ')
echo "[$(date +%T)] 접수 끝 (문항 $QUESTIONS, 비202 $NON202건)"
# 장애는 접수가 다 끝난 뒤 DOWN_S초 더 — 업로드 속도(wifi)에 따라 장애 길이가 달라지지 않게
sleep "$DOWN_S"
provider "down=false&limitPerSecond=$LIMIT"; T_UP=$(date +%s)
echo "[$(date +%T)] 복구 (초당 한도 $LIMIT)"
until [ "$(q "select count(*) from scoring_job where id in ($IDS) and status in ('QUEUED','PROCESSING')")" = 0 ]; do sleep 2; done
T_DONE=$(date +%s); sleep 2; touch "$STOP"; wait "$SAMPLER" || true
FAILED=$(q "select count(*) from scoring_job_item where job_id in ($IDS) and status='FAILED'")

python3 - "$CSV" "$((T_UP - T_DOWN))" "$((T_DONE - T_UP))" "$QUESTIONS" "$FAILED" "$LIMIT" "$LABEL" "$N" "$DOWN_S" <<'PY' | tee "$OUT"
import csv, sys
rows = list(csv.DictReader(open(sys.argv[1])))
up_after, drain, questions, failed, limit = map(int, sys.argv[2:7]); label, n, down_s = sys.argv[7], sys.argv[8], sys.argv[9]
tot = lambda r: sum(int(r[k]) for k in ("ok", "r429", "r5xx", "other"))
t0 = float(rows[0]["t"]) + 2  # 샘플러가 장애 시작 2초 전에 켜짐
per_sec = [(float(b["t"]) - t0, {k: int(b[k]) - int(a[k]) for k in ("ok", "r429", "r5xx", "other")}) for a, b in zip(rows, rows[1:])]
down = [d for t, d in per_sec if t < up_after]; up = [d for t, d in per_sec if t >= up_after]
s = lambda xs, k: sum(x[k] for x in xs); calls = lambda x: sum(x.values())
first = rows[0]; last = rows[-1]
print(f"# retry-storm {label} — 콤보 {n}건(문항 {questions}), 장애 {down_s}s 후 복구·초당 한도 {limit}")
print(f"장애 중   호출 {sum(map(calls, down))}건 (초당 평균 {sum(map(calls, down))/max(1,len(down)):.1f}, 최대 {max(map(calls, down), default=0)})")
print(f"복구 후   호출 {sum(map(calls, up))}건 = 성공 {s(up,'ok')} + 429 {s(up,'r429')} + 5xx {s(up,'r5xx')} + 기타 {s(up,'other')}")
print(f"복구 후   429 비율 {s(up,'r429')/max(1,sum(map(calls, up)))*100:.1f}%, 초당 최대 호출 {max(map(calls, up), default=0)} (한도 {limit})")
print(f"복구 후   첫 10초 호출 {sum(map(calls, up[:10]))}건 중 429 {s(up[:10],'r429')}")
print(f"전체      호출 {tot(last)-tot(first)}건 / 성공 {int(last['ok'])-int(first['ok'])}건 = 증폭 {(tot(last)-tot(first))/max(1,int(last['ok'])-int(first['ok'])):.1f}배")
print(f"복구 → 전부 완료 {drain}s, FAILED {failed}")
PY
echo "(초 단위 기록: $CSV)" | tee -a "$OUT"

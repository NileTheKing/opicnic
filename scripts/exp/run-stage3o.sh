#!/usr/bin/env bash
# 3단계 과부하 가정: 제공자 사정으로 받아주는 양이 줄면 429가 아니라 과부하 503이 온다(MOCK_OVERLOAD=true, 거절은 한도를 쓰지 않음).
# 2칸, 짧은 길이(예열 60 / 감소 120 / 관찰 180). 5분치는 exp-s3n.py로 환산한다(감소 기울기 x 300, 회복 후 기울기로 0까지 외삽).
#   A: 지금 운영 설정(aimd 한도 제한 + 서킷은 429를 세지 않음, 혼잡 제한 없음) -> 503은 서킷만 본다
#   B: A + 혼잡 제한(5xx와 성공이 섞이면 속도를 줄임)
# VM ~/opicnic-exp 에서 nohup 으로 실행. 사용: run-stage3o.sh <slots> [ONLY="B" 환경 변수로 일부만]
cd "$(dirname "$0")/../.."
SLOTS=${1:-500}
P=$HOME/opicnic-exp-results/progress.log
for spec in A:false B:true; do
  n=${spec%%:*}; g=${spec##*:}
  [ -n "$ONLY" ] && case " $ONLY " in *" $n "*) ;; *) continue ;; esac
  id="s3o-slots${SLOTS}-${n}${DECREASE:+-d$DECREASE}"
  echo "$(date '+%F %T') START $id (congestion=$g)" >> "$P"
  MOCK_OVERLOAD=true CONGESTION=$g COUNT_REJECTED=false COUNT_RATE_LIMITED=false WARM_S=60 OUTAGE_S=120 MAX_RECOVER_S=180 \
    scripts/exp/exp-run.sh "$id" 3 "$SLOTS" on L4a >> "$HOME/opicnic-exp-results/master.log" 2>&1
  rc=$?
  echo "$(date '+%F %T') END $id rc=$rc" >> "$P"
  [ "$rc" = 0 ] || { echo "$(date '+%F %T') STAGE3O STOPPED rc=$rc at $id" >> "$P"; exit "$rc"; }
done
echo "$(date '+%F %T') STAGE3O DONE" >> "$P"

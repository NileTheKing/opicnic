#!/usr/bin/env bash
# 3단계 새 가정: 가짜 제공자가 거절한 요청도 한도에 친다(COUNT_REJECTED=true). 8칸 = 사다리 {L0,L2,L4a,L4b} x 서킷 429 세기 {true,false}.
# 칸 2(L0, 서킷이 429를 안 셈)는 검증 회차: 원래 길이(예열 120 / 감소 300 / 관찰 360). 나머지는 SHORT=1 이면 짧은 길이(예열 60 / 감소 120 / 관찰 180).
# 순서: 2(L0 false 원래 길이) 3(L2 true) 1(L0 true) 4(L2 false) 5(L4a true) 6(L4a false) 7(L4b true) 8(L4b false). VM ~/opicnic-exp 에서 nohup 으로 실행.
# 사용: run-stage3n.sh <slots> [SKIP="L0:false ..." 환경 변수로 건너뜀] [SHORT=0 이면 전부 원래 길이]
cd "$(dirname "$0")/../.."
SLOTS=${1:-500}
SHORT=${SHORT:-1}
P=$HOME/opicnic-exp-results/progress.log
for spec in L0:false L2:true L0:true L2:false L4a:true L4a:false L4b:true L4b:false; do
  case " $SKIP " in *" $spec "*) continue ;; esac
  l=${spec%%:*}; c=${spec##*:}
  id="s3n-slots${SLOTS}-${l}-c${c}"
  if [ "$spec" = L0:false ] || [ "$SHORT" != 1 ]; then W=120; O=300; M=360; else W=60; O=120; M=180; fi
  echo "$(date '+%F %T') START $id (예열 $W 감소 $O 관찰 $M)" >> "$P"
  COUNT_REJECTED=true COUNT_RATE_LIMITED=$c WARM_S=$W OUTAGE_S=$O MAX_RECOVER_S=$M scripts/exp/exp-run.sh "$id" 3 "$SLOTS" on "$l" >> "$HOME/opicnic-exp-results/master.log" 2>&1
  rc=$?
  echo "$(date '+%F %T') END $id rc=$rc" >> "$P"
  [ "$rc" = 0 ] || { echo "$(date '+%F %T') STAGE3N STOPPED rc=$rc at $id" >> "$P"; exit "$rc"; }
  if [ "$spec" = L0:false ]; then echo "$(date '+%F %T') STAGE3N 검증 회차(칸 2) 끝: 외삽 오차 확인 필요(SHORT 결정)" >> "$P"; touch "$HOME/opicnic-exp-results/s3n-verify-done"; exit 0; fi
done
echo "$(date '+%F %T') STAGE3N DONE" >> "$P"

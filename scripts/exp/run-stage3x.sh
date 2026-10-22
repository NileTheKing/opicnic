#!/usr/bin/env bash
# 3단계 추가: 서킷이 429를 세지 않을 때(COUNT_RATE_LIMITED=false). 슬롯 500, 회복 후 관찰 360초(exp-run.sh 기본값). VM ~/opicnic-exp 에서 실행.
# 순서: L2 L0 L0 L2 (중간 보고) L4b L4a L4a L4b. SKIP="L2:1 ..." 로 건너뜀. 3번째 회차는 사람이 따로.
cd "$(dirname "$0")/../.."
SLOTS=${1:-500}
P=$HOME/opicnic-exp-results/progress.log
for spec in L2:1 L0:1 L0:2 L2:2 L4b:1 L4a:1 L4a:2 L4b:2; do
  case " $SKIP " in *" $spec "*) continue ;; esac
  l=${spec%%:*}; r=${spec##*:}
  id="s3x-slots${SLOTS}-${l}-r${r}"
  echo "$(date '+%F %T') START $id" >> "$P"
  COUNT_RATE_LIMITED=false scripts/exp/exp-run.sh "$id" 3 "$SLOTS" on "$l" >> "$HOME/opicnic-exp-results/master.log" 2>&1
  rc=$?
  echo "$(date '+%F %T') END $id rc=$rc" >> "$P"
  [ "$rc" = 0 ] || { echo "$(date '+%F %T') STAGE3X STOPPED rc=$rc at $id" >> "$P"; exit "$rc"; }
done
echo "$(date '+%F %T') STAGE3X DONE" >> "$P"

#!/usr/bin/env bash
# 3단계 전체: 사다리 L0 L1 L1.5 L2 L3 L4a L4b 각 2회. 첫 바퀴 정순, 두 번째 바퀴 역순 (README "반복"). VM ~/opicnic-exp 에서 실행.
# 사용: run-stage3.sh <slots> [시작 목록 "L0:1 L1:1 ..." 를 건너뛰려면 SKIP="L0:1 L1:1" 환경 변수]
# 세 번째 회차(두 번의 핵심 값 차이 20% 초과)는 사람이 결과를 보고 따로 돌린다: exp-run.sh s3-<slots>-<L>-r3 3 <slots> on <L>
cd "$(dirname "$0")/../.."
SLOTS=${1:?slots}
P=$HOME/opicnic-exp-results/progress.log
for spec in L0:1 L1:1 L1.5:1 L2:1 L3:1 L4a:1 L4b:1 L4b:2 L4a:2 L3:2 L2:2 L1.5:2 L1:2 L0:2; do
  case " $SKIP " in *" $spec "*) continue ;; esac
  l=${spec%%:*}; r=${spec##*:}
  id="s3-slots${SLOTS}-${l}-r${r}"
  echo "$(date '+%F %T') START $id" >> "$P"
  scripts/exp/exp-run.sh "$id" 3 "$SLOTS" on "$l" >> "$HOME/opicnic-exp-results/master.log" 2>&1
  rc=$?
  echo "$(date '+%F %T') END $id rc=$rc" >> "$P"
  [ "$rc" = 0 ] || { echo "$(date '+%F %T') STAGE3 STOPPED rc=$rc at $id" >> "$P"; exit "$rc"; }
done
echo "$(date '+%F %T') STAGE3 DONE" >> "$P"

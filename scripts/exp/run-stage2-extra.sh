#!/usr/bin/env bash
# 2단계 보충(계획 밖): 슬롯 300에서 서킷 끔/켬 x 2회(off,on / on,off). 슬롯 200은 장애 후 20분 안에 대기 줄이 안 빠져서
# "복구 시간"을 못 쟀기 때문에 슬롯 여유 비교용으로 추가. 사용: run-stage2-extra.sh <slots>
cd "$(dirname "$0")/../.."
SLOTS=${1:?slots}
P=$HOME/opicnic-exp-results/progress.log
for spec in off:1 on:1 on:2 off:2; do
  c=${spec%%:*}; r=${spec##*:}
  id="s2x-slots${SLOTS}-circuit${c}-r${r}"
  echo "$(date '+%F %T') START $id" >> "$P"
  scripts/exp/exp-run.sh "$id" 2 "$SLOTS" "$c" >> "$HOME/opicnic-exp-results/master.log" 2>&1
  rc=$?
  echo "$(date '+%F %T') END $id rc=$rc" >> "$P"
  [ "$rc" = 0 ] || { echo "$(date '+%F %T') STAGE2X STOPPED rc=$rc" >> "$P"; exit "$rc"; }
done
echo "$(date '+%F %T') STAGE2X DONE" >> "$P"

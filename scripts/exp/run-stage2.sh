#!/usr/bin/env bash
# 2단계 전체: 서킷 끔/켬 x 3회. 순서 off,on / on,off / off,on (정순, 역순, 정순). 슬롯은 인자(1단계에서 고른 값).
# 서킷 끔 = failure-ratio 2 (1보다 크게). 사용: run-stage2.sh <slots>
cd "$(dirname "$0")/../.."
SLOTS=${1:?slots}
P=$HOME/opicnic-exp-results/progress.log
for spec in off:1 on:1 on:2 off:2 off:3 on:3; do
  c=${spec%%:*}; r=${spec##*:}
  id="s2-slots${SLOTS}-circuit${c}-r${r}"
  echo "$(date '+%F %T') START $id" >> "$P"
  scripts/exp/exp-run.sh "$id" 2 "$SLOTS" "$c" >> "$HOME/opicnic-exp-results/master.log" 2>&1
  rc=$?
  echo "$(date '+%F %T') END $id rc=$rc" >> "$P"
  [ "$rc" = 0 ] || { echo "$(date '+%F %T') STAGE2 STOPPED rc=$rc" >> "$P"; exit "$rc"; }
done
echo "$(date '+%F %T') STAGE2 DONE" >> "$P"

#!/usr/bin/env bash
# 1단계 전체: 슬롯 60/200/300 x 2회. 첫 바퀴 정순, 두 번째 바퀴 역순 (README "반복"). VM ~/opicnic-exp 에서 실행.
cd "$(dirname "$0")/../.."
P=$HOME/opicnic-exp-results/progress.log
n=0
for slots in 60 200 300 300 200 60; do
  n=$((n + 1)); round=$(( n <= 3 ? 1 : 2 ))
  id="s1-slots${slots}-r${round}"
  echo "$(date '+%F %T') START $id" >> "$P"
  scripts/exp/exp-run.sh "$id" 1 "$slots" on >> "$HOME/opicnic-exp-results/master.log" 2>&1
  rc=$?
  echo "$(date '+%F %T') END $id rc=$rc" >> "$P"
  [ "$rc" = 0 ] || { echo "$(date '+%F %T') STAGE1 STOPPED rc=$rc" >> "$P"; exit "$rc"; }
done
echo "$(date '+%F %T') STAGE1 DONE" >> "$P"

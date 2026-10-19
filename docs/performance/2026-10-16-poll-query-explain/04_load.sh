#!/bin/sh
# 로드 스크립트. M은 mysql 클라이언트 래퍼(컨테이너 안에서 root로 실행)
M=${M:-mysql}
$M < 02_gen_jobs.sql
for lo in 1 66668 133335 200002 266669; do
  hi=$((lo+66666)); [ $hi -gt 333334 ] && hi=333334
  (echo "USE opicnic_explain; SET @lo=$lo, @hi=$hi;"; cat 03_gen_items_chunk.sql) | $M
  echo "chunk $lo-$hi done $(date +%T)"
done
$M -e "USE opicnic_explain; ANALYZE TABLE scoring_job, scoring_job_item;"

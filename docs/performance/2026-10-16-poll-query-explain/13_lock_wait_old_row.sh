#!/bin/sh
# 12_lock_wait.sh 후속. 새 INSERT(PK 끝)는 안 막혔다. 스캔이 이미 지나간 앞쪽 행(id 작은 오래된 행)을 고치는 요청은 막히는지 본다.
# 세션 B: A 시작 0.5초 뒤 id=1 행(끝난 지 오래된 DONE 행) UPDATE. 게스트 정리처럼 오래된 행을 건드리는 작업에 해당.
M=${M:-mysql}
ALLIDX="idx_scoring_job_item_status, idx_item_status_next, idx_item_status_next_upd"
run() { # name hint iso rep
  name=$1; hint=$2; iso=$3; rep=$4
  echo "SET SESSION TRANSACTION ISOLATION LEVEL $iso; SET @t=SYSDATE(6);
        UPDATE scoring_job_item $hint SET status = 'QUEUED' WHERE status = 'PROCESSING' AND updated_at < NOW(6) - INTERVAL 10 MINUTE;
        SELECT 'A', TIMESTAMPDIFF(MICROSECOND, @t, SYSDATE(6));" | $M -D opicnic_explain -N > ${T:-/tmp}/lockA.$$ 2>/dev/null &
  apid=$!
  sleep 0.5
  echo "SET SESSION TRANSACTION ISOLATION LEVEL $iso; SET innodb_lock_wait_timeout = 30; SET @t=SYSDATE(6);
        UPDATE scoring_job_item SET last_error = 'lock test' WHERE id = 1;
        SELECT 'B', TIMESTAMPDIFF(MICROSECOND, @t, SYSDATE(6));" | $M -D opicnic_explain -N 2>&1 | grep -v Warning > ${T:-/tmp}/lockB.$$
  wait $apid
  a=$(awk '{print $2}' ${T:-/tmp}/lockA.$$); b=$(awk '{print $2}' ${T:-/tmp}/lockB.$$)
  printf "%s\t%s\t%d\tA_update_ms=%s\tB_old_row_update_ms=%s\n" "$name" "$iso" "$rep" "$(echo "$a/1000" | bc)" "$(echo "$b/1000" | bc)"
  echo "UPDATE scoring_job_item SET last_error = NULL WHERE id = 1;" | $M -D opicnic_explain 2>/dev/null
  rm -f ${T:-/tmp}/lockA.$$ ${T:-/tmp}/lockB.$$
}
for rep in 1 2 3; do
  run INDEX ""                         "REPEATABLE READ" $rep
  run NOINDEX "IGNORE INDEX ($ALLIDX)" "REPEATABLE READ" $rep
  run NOINDEX "IGNORE INDEX ($ALLIDX)" "READ COMMITTED"  $rep
done

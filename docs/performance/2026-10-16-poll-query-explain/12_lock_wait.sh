#!/bin/sh
# 정리 UPDATE(requeueStale)가 도는 동안 녹음 제출의 INSERT가 기다리는지 잰다.
# 세션 A: requeueStale과 같은 UPDATE를 autocommit으로 실행(실제 코드처럼 바로 커밋).
# 세션 B: A 시작 0.5초 뒤 새 item INSERT. B의 소요 시간 = 기다린 시간 + INSERT 자체.
# 변형: 인덱스 사용 / 인덱스 무시, 격리 수준 REPEATABLE READ / READ COMMITTED.
M=${M:-mysql}
ALLIDX="idx_scoring_job_item_status, idx_item_status_next, idx_item_status_next_upd"
JOB=$(echo "SELECT job_id FROM opicnic_explain.scoring_job_item WHERE id = 1;" | $M -N 2>/dev/null)

upd() { # $1 = hint
  echo "UPDATE scoring_job_item $1 SET status = 'QUEUED' WHERE status = 'PROCESSING' AND updated_at < NOW(6) - INTERVAL 10 MINUTE"
}

run() { # name hint iso rep
  name=$1; hint=$2; iso=$3; rep=$4
  qi=$((900000 + rep))
  echo "SET SESSION TRANSACTION ISOLATION LEVEL $iso; SET @t=SYSDATE(6); $(upd "$hint"); SELECT 'A', TIMESTAMPDIFF(MICROSECOND, @t, SYSDATE(6));" \
    | $M -D opicnic_explain -N > ${T:-/tmp}/lockA.$$ 2>/dev/null &
  apid=$!
  sleep 0.5
  echo "SET SESSION TRANSACTION ISOLATION LEVEL $iso; SET innodb_lock_wait_timeout = 30; SET @t=SYSDATE(6);
        INSERT INTO scoring_job_item (attempts, audio_key, question_id, question_index, status, updated_at, job_id, invalid_outputs)
        VALUES (0, 'audio/lock/test.webm', 1, $qi, 'QUEUED', NOW(6), '$JOB', 0);
        SELECT 'B', TIMESTAMPDIFF(MICROSECOND, @t, SYSDATE(6));" | $M -D opicnic_explain -N 2>&1 | grep -v Warning > ${T:-/tmp}/lockB.$$
  wait $apid
  a=$(awk '{print $2}' ${T:-/tmp}/lockA.$$); b=$(awk '{print $2}' ${T:-/tmp}/lockB.$$)
  printf "%s\t%s\t%d\tA_update_ms=%s\tB_insert_ms=%s\n" "$name" "$iso" "$rep" "$(echo "$a/1000" | bc)" "$(echo "$b/1000" | bc)"
  echo "DELETE FROM scoring_job_item WHERE question_index = $qi AND job_id = '$JOB';" | $M -D opicnic_explain 2>/dev/null
  rm -f ${T:-/tmp}/lockA.$$ ${T:-/tmp}/lockB.$$
}

for rep in 1 2 3; do
  run INDEX ""                      "REPEATABLE READ" $rep
  run NOINDEX "IGNORE INDEX ($ALLIDX)" "REPEATABLE READ" $rep
  run NOINDEX "IGNORE INDEX ($ALLIDX)" "READ COMMITTED"  $rep
done

#!/bin/sh
# 분리 후 가정: 큐 테이블에 대기 행만 있을 때(끝난 500만 행 없음) 같은 폴링 쿼리를 잰다.
# 본 테이블에 시나리오를 깔고(06_scenario.sql) QUEUED 행만 queue_only로 복사한다. 인덱스 구성은 본 테이블과 같다(CREATE TABLE LIKE).
M=${M:-mysql}
LIM=${LIM:-60}
COLS="s.id, s.attempts, s.audio_key, s.failure_reason, s.feedback_result_id, s.invalid_outputs, s.job_id, s.last_error, s.next_attempt_at, s.question_id, s.question_index, s.status, s.stt_text, s.updated_at"
ALLIDX="idx_scoring_job_item_status, idx_item_status_next, idx_item_status_next_upd"
hint() { case $1 in
  I0) echo "USE INDEX (idx_scoring_job_item_status)";;
  I1) echo "USE INDEX (idx_item_status_next)";;
  I2) echo "USE INDEX (idx_item_status_next_upd)";;
  NONE) echo "IGNORE INDEX ($ALLIDX)";;
  AUTO) echo "";;
esac; }
q1() { echo "select $COLS from queue_only s $(hint $1) where s.status = 'QUEUED' and (s.next_attempt_at is null or s.next_attempt_at <= now(6)) order by s.updated_at limit $LIM"; }

out=raw/queue_only.txt
rm -f $out
for scn in S2 S3; do
  (echo "USE opicnic_explain; SET @scn='$scn', @n=5000;"; cat 06_scenario.sql) | $M > /dev/null
  echo "USE opicnic_explain; DROP TABLE IF EXISTS queue_only; CREATE TABLE queue_only LIKE scoring_job_item;
        INSERT INTO queue_only SELECT * FROM scoring_job_item WHERE status = 'QUEUED'; ANALYZE TABLE queue_only;" | $M > /dev/null
  for var in I0 I1 I2 AUTO NONE; do
    sql=$(q1 $var)
    esc=$(printf %s "$sql" | sed "s/'/''/g")
    echo "USE opicnic_explain; CALL measure('Q$scn','Q1','$var','$esc', 3, 20);" | $M > /dev/null
    { echo "### Q$scn $var"; echo "$sql"; echo; echo "-- EXPLAIN"; echo "EXPLAIN $sql;" | $M -D opicnic_explain -t 2>&1;
      echo "-- EXPLAIN ANALYZE"; echo "EXPLAIN ANALYZE $sql;" | $M -D opicnic_explain -r -s 2>&1 | sed 's/\\n/\n/g'; echo; } >> $out
  done
done
# 본 테이블은 측정 전 상태(S2, QUEUED 5천)로 남는다
echo "USE opicnic_explain; SELECT scn, variant, COUNT(*) n, ROUND(AVG(micros)/1000,3) avg_ms, MIN(micros) min_us, MAX(micros) max_us FROM results WHERE scn LIKE 'QS%' GROUP BY scn, variant ORDER BY scn, variant;" | $M -t

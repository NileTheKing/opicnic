#!/bin/sh
# 콜드 캐시 1회 측정: 5M 건 풀스캔(NONE)으로 버퍼 풀(128MB)을 씻은 직후 첫 실행 시간.
# S2(5000건 백오프) 데이터가 들어 있는 상태에서 실행한다. 변형마다 풀스캔으로 다시 씻는다.
M=${M:-mysql}
COLS="s.id, s.attempts, s.audio_key, s.failure_reason, s.feedback_result_id, s.invalid_outputs, s.job_id, s.last_error, s.next_attempt_at, s.question_id, s.question_index, s.status, s.stt_text, s.updated_at"
for h in "USE INDEX (idx_scoring_job_item_status)" "USE INDEX (idx_item_status_next)" "USE INDEX (idx_item_status_next_upd)"; do
  echo "== $h"
  for i in 1 2 3; do
  echo "USE opicnic_explain;
  SELECT COUNT(*), MAX(stt_text) INTO @a, @b FROM scoring_job_item IGNORE INDEX (idx_scoring_job_item_status, idx_item_status_next, idx_item_status_next_upd);
  SET @t=SYSDATE(6);
  select $COLS from scoring_job_item s $h where s.status = 'QUEUED' and (s.next_attempt_at is null or s.next_attempt_at <= now(6)) order by s.updated_at limit 60;
  SELECT TIMESTAMPDIFF(MICROSECOND,@t,SYSDATE(6))/1000 AS cold_ms;" | $M -N | tail -1
  done
done

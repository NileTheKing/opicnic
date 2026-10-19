#!/bin/sh
# 폴링이 인덱스를 못 쓸 때 다른 조회(fr, feedback_result 흉내)가 버퍼 풀에서 밀려나는지 본다.
# 조건: BASE(폴링 없음) / POLL_IDX(인덱스 쓰는 폴링) / POLL_NOIDX(인덱스 무시 폴링)
# 폴링 한 바퀴 = ScoringWorker.poll()과 같은 쿼리들: requeueStale UPDATE, countByStatus, findClaimable(60), countByStatus. 끝나고 1초 쉼.
M=${M:-mysql}
DUR=${DUR:-180}
ALLIDX="idx_scoring_job_item_status, idx_item_status_next, idx_item_status_next_upd"
COLS="s.id, s.attempts, s.audio_key, s.failure_reason, s.feedback_result_id, s.invalid_outputs, s.job_id, s.last_error, s.next_attempt_at, s.question_id, s.question_index, s.status, s.stt_text, s.updated_at"

poll_sql() { h=$1
  echo "UPDATE scoring_job_item $h SET status = 'QUEUED' WHERE status = 'PROCESSING' AND updated_at < NOW(6) - INTERVAL 10 MINUTE;
        SELECT COUNT(*) FROM scoring_job_item s $h WHERE s.status = 'QUEUED';
        SELECT $COLS FROM scoring_job_item s $h WHERE s.status = 'QUEUED' AND (s.next_attempt_at IS NULL OR s.next_attempt_at <= NOW(6)) ORDER BY s.updated_at LIMIT 60;
        SELECT COUNT(*) FROM scoring_job_item s $h WHERE s.status = 'QUEUED';"
}
poller() { h=$1; end=$2; cycles=0
  while [ $(date +%s) -lt $end ]; do poll_sql "$h" | $M -D opicnic_explain > /dev/null 2>&1; cycles=$((cycles+1)); sleep 1; done
  echo $cycles > ${T:-/tmp}/cycles.$$
}
resident() { echo "SELECT ROUND(SUM(IF(TABLE_NAME LIKE '%\`fr\`%', 1, 0)) * 16 / 1024, 1) fr_mb_in_pool,
                     ROUND(SUM(IF(TABLE_NAME LIKE '%scoring_job_item%', 1, 0)) * 16 / 1024, 1) item_mb_in_pool
              FROM information_schema.INNODB_BUFFER_PAGE;" | $M -N 2>/dev/null; }

warm() { echo "CALL opicnic_explain.victim('warm', 0, 3000);" | $M > /dev/null 2>&1; }

run() { label=$1; hint=$2
  warm
  echo "== $label  before: fr_mb item_mb = $(resident)"
  end=$(( $(date +%s) + DUR ))
  if [ "$label" != BASE ]; then poller "$hint" $end & ppid=$!; fi
  b=0
  while [ $(date +%s) -lt $end ]; do b=$((b+1)); echo "CALL opicnic_explain.victim('$label', $b, 200);" | $M > /dev/null 2>&1; done
  [ "$label" != BASE ] && { wait $ppid; echo "   poll cycles: $(cat ${T:-/tmp}/cycles.$$)"; }
  echo "   after:  fr_mb item_mb = $(resident)"
}

(echo "USE opicnic_explain; SET @scn='S1', @n=10;"; cat 06_scenario.sql) | $M > /dev/null 2>&1
echo "DELETE FROM opicnic_explain.victim_results;" | $M 2>/dev/null
run BASE ""
run POLL_IDX ""
run POLL_NOIDX "IGNORE INDEX ($ALLIDX)"
run POLL_IDX2 ""
echo "SELECT label, COUNT(*) batches, SUM(n) queries, ROUND(SUM(total_us)/SUM(n)/1000, 3) avg_ms_per_query,
             ROUND(SUM(bp_reads)/SUM(n), 2) disk_reads_per_query, MAX(ROUND(total_us/n/1000,3)) worst_batch_avg_ms
      FROM opicnic_explain.victim_results WHERE label <> 'warm' GROUP BY label ORDER BY MIN(id);" | $M -t 2>/dev/null

#!/bin/sh
# 시나리오 x 인덱스 변형 측정. M은 mysql 클라이언트 래퍼. 한 시나리오씩 데이터를 갈아끼운다.
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
q1() { echo "select $COLS from scoring_job_item s $(hint $1) where s.status = 'QUEUED' and (s.next_attempt_at is null or s.next_attempt_at <= now(6)) order by s.updated_at limit $LIM"; }
q2() { echo "select $COLS from scoring_job_item s $(hint $1) where s.status = 'QUEUED' and s.next_attempt_at <= now(6) order by s.updated_at limit $LIM"; }

run_case() { # scn qver variant reps
  scn=$1; qv=$2; var=$3; reps=$4; warm=3; [ $var = NONE ] && warm=1
  if [ $qv = Q1 ]; then sql=$(q1 $var); else sql=$(q2 $var); fi
  esc=$(printf %s "$sql" | sed "s/'/''/g")
  echo "USE opicnic_explain; CALL measure('$scn','$qv','$var','$esc', $warm, $reps);" | $M > /dev/null
  { echo "### $scn $qv $var"; echo "$sql"; echo; echo "-- EXPLAIN"; echo "EXPLAIN $sql;" | $M -D opicnic_explain -t 2>&1; echo "-- EXPLAIN ANALYZE"; echo "EXPLAIN ANALYZE $sql;" | $M -D opicnic_explain -r -s 2>&1 | sed 's/\\n/\n/g' ; echo; } >> raw/explain_$scn.txt
}

for scn in $SCENARIOS; do
  rm -f raw/explain_$scn.txt
  case $scn in
    S1)   n=10;    mscn=S1;;
    S1Q2) n=10;    mscn=S1Q2;;
    S2)   n=5000;  mscn=S2;;
    S3)   n=5000;  mscn=S3;;
    S4)   n=50000; mscn=S2;;   # 대형 장애: 5만 건 전부 백오프
  esac
  (echo "USE opicnic_explain; SET @scn='$mscn', @n=$n;"; cat 06_scenario.sql) | $M > raw/scenario_$scn.txt
  if [ $scn != S1Q2 ]; then
    for var in I0 I1 I2 AUTO; do run_case $scn Q1 $var 20; done
    run_case $scn Q1 NONE 5
  fi
  if [ $scn != S1 ]; then   # S1은 next_attempt_at이 NULL이라 Q2는 0건. Q2의 평소 시나리오는 S1Q2
    for var in I0 I1 I2; do run_case $scn Q2 $var 20; done
  fi
  echo "$scn done"
done

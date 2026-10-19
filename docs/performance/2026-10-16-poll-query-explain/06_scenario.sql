-- 사용: (echo "USE opicnic_explain; SET @scn='S2', @n=5000;"; cat 06_scenario.sql) | mysql
-- 끝난 5M 행은 그대로 두고 QUEUED 행만 갈아끼운다. QUEUED 행은 id가 100000000 이상, question_index 1000 이상으로 구분.
-- @scn: S1(next_attempt_at NULL), S1Q2(전부 과거 시각, Q2용), S2(전부 미래), S3(절반 과거 절반 미래)
SET foreign_key_checks=0, unique_checks=0;
DELETE FROM scoring_job_item WHERE id >= 100000000;
SET @now = NOW(6);
INSERT INTO scoring_job_item (id, attempts, audio_key, feedback_result_id, last_error, question_id, question_index,
                              status, updated_at, job_id, next_attempt_at, stt_text, failure_reason, invalid_outputs)
SELECT 100000000 + q.n, IF(@scn='S1', 0, 2), CONCAT('audio/q/', q.n, '.webm'), NULL,
       IF(@scn='S1', NULL, 'Gemini 429 rate limited'), q.n MOD 700 + 1, 1000 + q.n,
       'QUEUED',
       @now - INTERVAL (q.n MOD 3600) SECOND,
       (SELECT id FROM job_ids WHERE rn = 333334 - (q.n MOD 1000)),
       CASE @scn
         WHEN 'S1'   THEN NULL
         WHEN 'S1Q2' THEN @now - INTERVAL 1 MINUTE
         WHEN 'S2'   THEN @now + INTERVAL (1 + q.n MOD 20) MINUTE
         WHEN 'S3'   THEN IF(q.n MOD 2 = 0, @now - INTERVAL (1 + q.n MOD 20) MINUTE, @now + INTERVAL (1 + q.n MOD 20) MINUTE)
       END,
       NULL, NULL, 0
FROM seq q WHERE q.n < @n;
SELECT @scn AS scenario, COUNT(*) AS queued_rows,
       SUM(next_attempt_at IS NULL) AS next_null, SUM(next_attempt_at <= NOW(6)) AS ready, SUM(next_attempt_at > NOW(6)) AS future
FROM scoring_job_item WHERE status = 'QUEUED';

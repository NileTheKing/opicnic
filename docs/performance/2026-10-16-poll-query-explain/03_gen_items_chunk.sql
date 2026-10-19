-- 사용: (echo "USE opicnic_explain; SET @lo=1, @hi=66667;"; cat 03_gen_items_chunk.sql) | mysql
-- 잡 15개 문항씩. 97% DONE, 3% FAILED. updated_at은 잡 created_at + 문항 순서*30초
SET foreign_key_checks=0, unique_checks=0;
SET @now = NOW();
INSERT INTO scoring_job_item (attempts, audio_key, feedback_result_id, last_error, question_id, question_index,
                              status, updated_at, job_id, next_attempt_at, stt_text, failure_reason, invalid_outputs)
SELECT IF(f.is_failed, 3, 1),
       CONCAT('audio/', j.id, '/', q.n, '.webm'),
       IF(f.is_failed, NULL, j.rn * 15 + q.n),
       IF(f.is_failed, 'Gemini 503 Service Unavailable: model is overloaded, retry budget exceeded after 30m', NULL),
       (j.rn * 15 + q.n) MOD 700 + 1,
       q.n,
       IF(f.is_failed, 'FAILED', 'DONE'),
       @now - INTERVAL (333334 - j.rn) * 94.6 SECOND + INTERVAL q.n * 30 SECOND,
       j.id,
       NULL,
       IF((j.rn + q.n) MOD 3 = 0,
          CONCAT('I usually go to the park near my house on weekends because it helps me relax and spend time with my family. ',
                 'Last weekend I went there with my friends and we played badminton for two hours. After that we had some snacks and talked about our plans. ',
                 'It was really fun and I want to go again next week, hopefully the weather will be nice and I can bring my camera too. ', q.n),
          CONCAT('I like to go to the park on weekends. ', q.n)),
       IF(f.is_failed, 'RETRY_BUDGET_EXCEEDED', NULL),
       0
FROM job_ids j
JOIN seq q ON q.n < 15
JOIN LATERAL (SELECT ((j.rn * 15 + q.n) MOD 100 < 3) AS is_failed) f ON 1=1
WHERE j.rn BETWEEN @lo AND @hi;

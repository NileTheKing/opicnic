USE opicnic_explain;
-- I0 = idx_scoring_job_item_status (status, updated_at) 는 이미 있다 (현재 코드와 같은 인덱스)
-- I1: 백오프 시각으로 먼저 거른다. OR(IS NULL / <=)을 range 두 개로 쓸 수 있다
ALTER TABLE scoring_job_item ADD INDEX idx_item_status_next (status, next_attempt_at);
-- I2: I1에 updated_at을 붙여 커버링에 가깝게. 단 ORDER BY updated_at은 next_attempt_at 범위 때문에 인덱스 순서로 못 얻는다
ALTER TABLE scoring_job_item ADD INDEX idx_item_status_next_upd (status, next_attempt_at, updated_at);
ANALYZE TABLE scoring_job_item;

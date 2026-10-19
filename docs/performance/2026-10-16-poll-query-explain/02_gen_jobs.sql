USE opicnic_explain;
SET foreign_key_checks=0, unique_checks=0;
SET @now = NOW();
-- 잡 created_at: 365일에 걸쳐 균등
INSERT INTO scoring_job (id, completed_at, created_at, mode, status, member_id, combo_category, combo_pattern_key)
SELECT id,
       @now - INTERVAL (333334 - rn) * 94.6 SECOND + INTERVAL 5 MINUTE,
       @now - INTERVAL (333334 - rn) * 94.6 SECOND,
       'COMBO', 'COMPLETED', rn MOD 5000, 'GENERAL', 'COMBO_3'
FROM job_ids;

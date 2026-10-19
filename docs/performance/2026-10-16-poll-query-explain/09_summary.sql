USE opicnic_explain;
-- 중앙값과 p95(nearest-rank). 20회면 p95는 19번째, 5회면 최댓값. 단위 ms
WITH r AS (
  SELECT scn, query_ver, variant, micros, hr_first, hr_key, hr_next, hr_rnd, hr_rnd_next,
         ROW_NUMBER() OVER (PARTITION BY scn, query_ver, variant ORDER BY micros) rk,
         COUNT(*) OVER (PARTITION BY scn, query_ver, variant) cnt
  FROM results)
SELECT scn, query_ver q, variant v, MAX(cnt) n,
       ROUND(AVG(IF(rk IN (FLOOR((cnt+1)/2), CEIL((cnt+1)/2)), micros, NULL))/1000, 3) p50_ms,
       ROUND(MAX(IF(rk = CEIL(cnt*0.95), micros, NULL))/1000, 3) p95_ms,
       ROUND(MAX(micros)/1000, 3) max_ms,
       MAX(hr_key) h_key, MAX(hr_next) h_next, MAX(hr_rnd) h_rnd, MAX(hr_first) h_first, MAX(hr_rnd_next) h_rnd_next
FROM r GROUP BY scn, query_ver, variant
ORDER BY FIELD(scn,'S1','S1Q2','S2','S3','S4'), query_ver, FIELD(variant,'I0','I1','I2','AUTO','NONE');

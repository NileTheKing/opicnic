-- 버퍼 풀 실험용 "다른 화면" 테이블. feedback_result의 요약 조회(사용자별 최근 답변)를 흉내 낸다.
-- 2,000명 x 100개 = 20만 행, 행당 약 300바이트 → 약 60MB. 혼자면 버퍼 풀(128MB)에 다 올라가는 크기.
USE opicnic_explain;
DROP TABLE IF EXISTS fr;
CREATE TABLE fr (
  id bigint AUTO_INCREMENT PRIMARY KEY,
  member_id bigint NOT NULL,
  created_at datetime(6) NOT NULL,
  question_type varchar(20) NOT NULL,
  overall_grade varchar(10) NOT NULL,
  score1 int, score2 int, score3 int, score4 int, score5 int,
  summary varchar(300),
  KEY idx_fr_member_created (member_id, created_at)
);
INSERT INTO fr (member_id, created_at, question_type, overall_grade, score1, score2, score3, score4, score5, summary)
SELECT 1 + (n MOD 2000), NOW(6) - INTERVAL (n DIV 2000) HOUR, CONCAT('TYPE_', 1 + n MOD 10), 'IM2',
       n MOD 5, n MOD 4, n MOD 3, n MOD 5, n MOD 4, REPEAT('x', 250)
FROM seq WHERE n < 200000;
ANALYZE TABLE fr;
DROP TABLE IF EXISTS victim_results;
CREATE TABLE victim_results (id int AUTO_INCREMENT PRIMARY KEY, label varchar(20), batch int, n int, total_us bigint, bp_reads bigint, ts datetime(6));
DROP PROCEDURE IF EXISTS victim;
DELIMITER $$
-- 무작위 사용자 p_n명의 최근 답변 100개를 읽는다(행 본문까지 읽도록 summary 길이 합을 구함)
CREATE PROCEDURE victim(IN p_label varchar(20), IN p_batch int, IN p_n int)
BEGIN
  DECLARE i INT DEFAULT 0;
  DECLARE t0 DATETIME(6);
  DECLARE r0, r1 BIGINT;
  DECLARE s BIGINT;
  DECLARE m BIGINT;
  SELECT variable_value INTO r0 FROM performance_schema.global_status WHERE variable_name = 'Innodb_buffer_pool_reads';
  SET t0 = SYSDATE(6);
  WHILE i < p_n DO
    -- RAND()를 WHERE에 직접 쓰면 행마다 다시 계산돼 인덱스를 못 탄다(첫 실행의 버그). 변수로 먼저 뽑는다
    SET m = 1 + FLOOR(RAND() * 2000);
    SELECT SUM(LENGTH(x.summary)) INTO s FROM (
      SELECT summary FROM fr WHERE member_id = m ORDER BY created_at DESC LIMIT 100) x;
    SET i = i + 1;
  END WHILE;
  SELECT variable_value INTO r1 FROM performance_schema.global_status WHERE variable_name = 'Innodb_buffer_pool_reads';
  INSERT INTO victim_results (label, batch, n, total_us, bp_reads, ts)
  VALUES (p_label, p_batch, p_n, TIMESTAMPDIFF(MICROSECOND, t0, SYSDATE(6)), r1 - r0, SYSDATE(6));
END$$
DELIMITER ;

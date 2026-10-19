USE opicnic_explain;
DROP TABLE IF EXISTS results;
CREATE TABLE results (
  id int AUTO_INCREMENT PRIMARY KEY, scn varchar(10), query_ver varchar(4), variant varchar(10), run int, micros bigint,
  hr_first bigint, hr_key bigint, hr_next bigint, hr_rnd bigint, hr_rnd_next bigint, rows_sent int
);
DROP PROCEDURE IF EXISTS measure;
DELIMITER $$
CREATE PROCEDURE measure(IN p_scn varchar(10), IN p_qv varchar(4), IN p_var varchar(10), IN p_sql text, IN p_warm int, IN p_reps int)
BEGIN
  DECLARE i INT DEFAULT 0;
  DECLARE t0 DATETIME(6);
  DECLARE h0, h1, h2, h3, h4, g0, g1, g2, g3, g4 BIGINT;
  SET @q = p_sql;
  PREPARE st FROM @q;
  -- 워밍업 (결과는 클라이언트가 버린다)
  WHILE i < p_warm DO EXECUTE st; SET i = i + 1; END WHILE;
  -- Handler_read_* 델타는 별도 1회로 잰다
  SELECT SUM(IF(variable_name='Handler_read_first',variable_value,0)), SUM(IF(variable_name='Handler_read_key',variable_value,0)),
         SUM(IF(variable_name='Handler_read_next',variable_value,0)), SUM(IF(variable_name='Handler_read_rnd',variable_value,0)),
         SUM(IF(variable_name='Handler_read_rnd_next',variable_value,0))
    INTO h0, h1, h2, h3, h4 FROM performance_schema.session_status WHERE variable_name LIKE 'Handler_read%';
  EXECUTE st;
  SELECT SUM(IF(variable_name='Handler_read_first',variable_value,0)), SUM(IF(variable_name='Handler_read_key',variable_value,0)),
         SUM(IF(variable_name='Handler_read_next',variable_value,0)), SUM(IF(variable_name='Handler_read_rnd',variable_value,0)),
         SUM(IF(variable_name='Handler_read_rnd_next',variable_value,0))
    INTO g0, g1, g2, g3, g4 FROM performance_schema.session_status WHERE variable_name LIKE 'Handler_read%';
  SET i = 0;
  WHILE i < p_reps DO
    SET t0 = SYSDATE(6);
    EXECUTE st;
    INSERT INTO results (scn, query_ver, variant, run, micros, hr_first, hr_key, hr_next, hr_rnd, hr_rnd_next)
    VALUES (p_scn, p_qv, p_var, i + 1, TIMESTAMPDIFF(MICROSECOND, t0, SYSDATE(6)), g0-h0, g1-h1, g2-h2, g3-h3, g4-h4);
    SET i = i + 1;
  END WHILE;
  DEALLOCATE PREPARE st;
END$$
DELIMITER ;

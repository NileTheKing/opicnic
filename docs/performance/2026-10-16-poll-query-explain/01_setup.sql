-- 새 DB. 앱 DB(opicnic)는 SHOW CREATE TABLE로 읽기만 했고 아무것도 쓰지 않았다.
CREATE DATABASE IF NOT EXISTS opicnic_explain DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE opicnic_explain;
DROP TABLE IF EXISTS scoring_job_item;
DROP TABLE IF EXISTS scoring_job;

-- member FK는 member 테이블을 복사하지 않으므로 생략. 나머지는 앱 DB의 DDL과 같다.
CREATE TABLE scoring_job (
  id varchar(36) NOT NULL,
  completed_at datetime(6) DEFAULT NULL,
  created_at datetime(6) NOT NULL,
  mode enum('COMBO','MOCK_EXAM') NOT NULL,
  status enum('COMPLETED','COMPLETED_WITH_FAILURES','PROCESSING','QUEUED') NOT NULL,
  member_id bigint NOT NULL,
  combo_category varchar(50) DEFAULT NULL,
  combo_pattern_key varchar(100) DEFAULT NULL,
  result_seen_at datetime(6) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_scoring_job_member_created (member_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE scoring_job_item (
  id bigint NOT NULL AUTO_INCREMENT,
  attempts int NOT NULL,
  audio_key varchar(200) NOT NULL,
  feedback_result_id bigint DEFAULT NULL,
  last_error varchar(500) DEFAULT NULL,
  question_id bigint DEFAULT NULL,
  question_index int NOT NULL,
  status enum('DONE','FAILED','PROCESSING','QUEUED') NOT NULL,
  updated_at datetime(6) NOT NULL,
  job_id varchar(36) NOT NULL,
  next_attempt_at datetime(6) DEFAULT NULL,
  stt_text text,
  failure_reason enum('AUDIO','INVALID_OUTPUT','RETRY_BUDGET_EXCEEDED') DEFAULT NULL,
  invalid_outputs int NOT NULL DEFAULT '0',
  PRIMARY KEY (id),
  UNIQUE KEY uk_scoring_job_item_job_index (job_id, question_index),
  KEY idx_scoring_job_item_status (status, updated_at),
  CONSTRAINT fk_item_job FOREIGN KEY (job_id) REFERENCES scoring_job (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 보조 테이블: 0..999999
DROP TABLE IF EXISTS seq;
CREATE TABLE seq (n int NOT NULL PRIMARY KEY) ENGINE=InnoDB;
DROP TABLE IF EXISTS digits;
CREATE TABLE digits (d int NOT NULL PRIMARY KEY);
INSERT INTO digits VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);
INSERT INTO seq SELECT a.d + 10*b.d + 100*c.d + 1000*d.d + 10000*e.d + 100000*f.d
FROM digits a, digits b, digits c, digits d, digits e, digits f;

-- 잡 ID 스테이징 (rn 1..333334)
DROP TABLE IF EXISTS job_ids;
CREATE TABLE job_ids (rn int NOT NULL PRIMARY KEY, id char(36) NOT NULL) ENGINE=InnoDB;
INSERT INTO job_ids SELECT n, UUID() FROM seq WHERE n BETWEEN 1 AND 333334;

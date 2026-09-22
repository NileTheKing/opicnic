-- 부하 측정(docker-compose.loadtest.yml)이 운영 DB에 남긴 dev 테스터 회원 명의의 잡·결과 삭제.
-- 실행: docker exec -i opicnic_mysql mysql -u"$DB_USERNAME" -p"$DB_PASSWORD" opicnic < scripts/loadtest-cleanup.sql
SET @dev := (SELECT id FROM member WHERE provider = 'dev' LIMIT 1);
DELETE ft FROM feedback_tag ft JOIN feedback_result fr ON fr.id = ft.feedback_result_id WHERE fr.member_id = @dev;
DELETE FROM feedback_result WHERE member_id = @dev;
DELETE i FROM scoring_job_item i JOIN scoring_job j ON j.id = i.job_id WHERE j.member_id = @dev;
DELETE FROM scoring_job WHERE member_id = @dev;
SELECT 'remaining dev rows' AS what, (SELECT COUNT(*) FROM scoring_job WHERE member_id = @dev) AS jobs, (SELECT COUNT(*) FROM feedback_result WHERE member_id = @dev) AS results;

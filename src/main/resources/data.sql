INSERT INTO member (member_id, nickname, provider, provider_id, role)
VALUES (2, '테스트유저', 'kakao', '1234567890', 'USER');

INSERT INTO study_post (id, title, content, study_type, status, max_participants, writer_member_id)
VALUES (1, '백엔드 스터디 모집', '내용입니다.', 'PROJECT', 'RECRUITING', 5, 2);
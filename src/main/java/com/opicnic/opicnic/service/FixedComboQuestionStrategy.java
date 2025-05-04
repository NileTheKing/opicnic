package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.Question;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service("fixedQuestionSelector")
public class FixedComboQuestionStrategy implements ComboQuestionStrategy {


    @Override
    public List<Question> selectQuestions(String topic, String difficulty) {
        // DB 연결 없이 고정된 문제 3개를 반환합니다.
        // 실제 애플리케이션에서는 DB에서 가져오거나, 외부 API를 호출하는 등의 로직이 들어갈 수 있습니다.

        return Arrays.asList(
                new Question(1L, "자주 가는 공원에 대해 묘사해보세요.", "topic1", "easy"),
                new Question(2L, "최근에 갔던 공원 대해 이야기해주세요.", "topic1", "easy"),
                new Question(3L, "가장 기억에 남는 공원에서의 기억은 무엇인가요?", "topic1", "easy")
        );

    }
}

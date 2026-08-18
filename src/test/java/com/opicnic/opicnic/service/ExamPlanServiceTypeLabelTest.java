package com.opicnic.opicnic.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// PC-01 회귀 테스트: 기록 화면(history.html 등)이 자기소개(questionType=null) 결과를
// 렌더링할 때 typeLabel(null)이 NPE 대신 안전한 라벨을 반환해야 한다.
class ExamPlanServiceTypeLabelTest {

    @Test
    void typeLabelIsNullSafe() {
        ExamPlanService service = new ExamPlanService();
        assertThat(service.typeLabel(null)).isEqualTo("자기소개");
    }
}

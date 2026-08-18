package com.opicnic.opicnic.service;

import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.ComboQuestionsResult;
import com.opicnic.opicnic.dto.QuestionDto;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// PC-04 회귀 테스트: 현재 난이도에 없는 category(C5@LEVEL_4, C4@LEVEL_5)를 요청하면
// 다른 category로 조용히 대체되지 않고 명시적으로 실패해야 한다.
class ComboPracticeServiceTest {

    @Test
    void unsupportedCategoryAtLowDifficultyThrowsInsteadOfSilentFallback() {
        ComboPracticeService service = new ComboPracticeService(
                new OpicComboPatternProvider(), Mockito.mock(QuestionAssemblyService.class), new Random());

        assertThatThrownBy(() -> service.getComboQuestionsByCategory(SurveyTopic.PARK_GOING, SurveyDifficulty.LEVEL_4, "C5"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unsupportedCategoryAtHighDifficultyThrowsInsteadOfSilentFallback() {
        ComboPracticeService service = new ComboPracticeService(
                new OpicComboPatternProvider(), Mockito.mock(QuestionAssemblyService.class), new Random());

        assertThatThrownBy(() -> service.getComboQuestionsByCategory(SurveyTopic.PARK_GOING, SurveyDifficulty.LEVEL_5, "C4"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void supportedCategoryAlwaysMatchesRequestedCategory() {
        QuestionAssemblyService questionAssemblyService = Mockito.mock(QuestionAssemblyService.class);
        when(questionAssemblyService.assemble(any(), any())).thenReturn(List.of(Mockito.mock(QuestionDto.class)));
        ComboPracticeService service = new ComboPracticeService(
                new OpicComboPatternProvider(), questionAssemblyService, new Random());

        ComboQuestionsResult result = service.getComboQuestionsByCategory(SurveyTopic.PARK_GOING, SurveyDifficulty.LEVEL_4, "C3");
        assertThat(result.comboCategory()).isEqualTo("C3");
    }
}

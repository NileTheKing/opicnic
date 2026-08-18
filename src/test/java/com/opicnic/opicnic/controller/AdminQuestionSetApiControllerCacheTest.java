package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionSetApiDto;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.service.QuestionAssemblyService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// CACHE-01 회귀 테스트: 관리자 CRUD가 QuestionAssemblyService의 캐시를 실제로 무효화하는지 검증.
// update()는 topic이 바뀔 수 있으므로 이전/이후 topic을 모두 evict해야 한다.
class AdminQuestionSetApiControllerCacheTest {

    @Test
    void createEvictsTargetTopic() {
        QuestionSetRepository repository = Mockito.mock(QuestionSetRepository.class);
        QuestionAssemblyService questionAssemblyService = Mockito.mock(QuestionAssemblyService.class);
        AdminQuestionSetApiController controller = new AdminQuestionSetApiController(repository, questionAssemblyService);

        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.create(new QuestionSetApiDto(null, "새 세트", SurveyTopic.JOGGING));

        verify(questionAssemblyService).evict(SurveyTopic.JOGGING);
    }

    @Test
    void updateEvictsBothPreviousAndNewTopicWhenTopicChanges() {
        QuestionSetRepository repository = Mockito.mock(QuestionSetRepository.class);
        QuestionAssemblyService questionAssemblyService = Mockito.mock(QuestionAssemblyService.class);
        AdminQuestionSetApiController controller = new AdminQuestionSetApiController(repository, questionAssemblyService);

        QuestionSet existing = new QuestionSet("기존 세트", SurveyTopic.JOGGING);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.update(1L, new QuestionSetApiDto(1L, "수정된 세트", SurveyTopic.WALKING));

        verify(questionAssemblyService).evict(SurveyTopic.JOGGING);
        verify(questionAssemblyService).evict(SurveyTopic.WALKING);
    }

    @Test
    void deleteEvictsTopic() {
        QuestionSetRepository repository = Mockito.mock(QuestionSetRepository.class);
        QuestionAssemblyService questionAssemblyService = Mockito.mock(QuestionAssemblyService.class);
        AdminQuestionSetApiController controller = new AdminQuestionSetApiController(repository, questionAssemblyService);

        QuestionSet existing = new QuestionSet("삭제될 세트", SurveyTopic.JOGGING);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.delete(1L);

        verify(questionAssemblyService).evict(SurveyTopic.JOGGING);
    }
}

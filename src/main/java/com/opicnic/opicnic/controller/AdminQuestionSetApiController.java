package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.dto.QuestionSetApiDto;
import com.opicnic.opicnic.exception.ResourceNotFoundException;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import com.opicnic.opicnic.service.QuestionAssemblyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/question-sets")
@RequiredArgsConstructor
public class AdminQuestionSetApiController {

    private final QuestionSetRepository questionSetRepository;
    private final QuestionAssemblyService questionAssemblyService;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody QuestionSetApiDto request) {
        QuestionSet saved = questionSetRepository.save(new QuestionSet(request.name(), request.topic()));
        // 이 topic이 이전에 빈 목록으로 캐시됐을 수 있으므로(setCache.computeIfAbsent), 새 세트가
        // 바로 출제 후보에 반영되도록 무효화한다 (CACHE-01).
        questionAssemblyService.evict(saved.getTopic());
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody QuestionSetApiDto request) {
        // @Where(clause = "deleted = false")가 적용되어 findById는 삭제되지 않은 것만 찾는다.
        QuestionSet existing = questionSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("질문 세트를 찾을 수 없습니다. id=" + id));
        SurveyTopic previousTopic = existing.getTopic();
        existing.setName(request.name());
        existing.setTopic(request.topic());
        QuestionSet saved = questionSetRepository.save(existing);
        // topic이 바뀌었을 수 있으므로 이전/이후 topic 캐시를 모두 무효화한다 (CACHE-01).
        questionAssemblyService.evict(previousTopic);
        questionAssemblyService.evict(saved.getTopic());
        return ResponseEntity.ok(toDto(saved));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        QuestionSet existing = questionSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("질문 세트를 찾을 수 없습니다. id=" + id));
        existing.setDeleted(true);
        questionSetRepository.save(existing);
        questionAssemblyService.evict(existing.getTopic()); // CACHE-01: 삭제된 세트가 계속 출제되지 않도록
        return ResponseEntity.noContent().build();
    }

    private QuestionSetApiDto toDto(QuestionSet questionSet) {
        return new QuestionSetApiDto(questionSet.getId(), questionSet.getName(), questionSet.getTopic());
    }
}

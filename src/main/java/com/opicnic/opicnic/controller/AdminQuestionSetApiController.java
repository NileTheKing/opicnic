package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.dto.QuestionSetApiDto;
import com.opicnic.opicnic.exception.ResourceNotFoundException;
import com.opicnic.opicnic.repository.QuestionSetRepository;
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

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody QuestionSetApiDto request) {
        QuestionSet saved = questionSetRepository.save(new QuestionSet(request.name(), request.topic()));
        return ResponseEntity.ok(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody QuestionSetApiDto request) {
        // @Where(clause = "deleted = false")가 적용되어 findById는 삭제되지 않은 것만 찾는다.
        QuestionSet existing = questionSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("질문 세트를 찾을 수 없습니다. id=" + id));
        existing.setName(request.name());
        existing.setTopic(request.topic());
        return ResponseEntity.ok(toDto(questionSetRepository.save(existing)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        QuestionSet existing = questionSetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("질문 세트를 찾을 수 없습니다. id=" + id));
        existing.setDeleted(true);
        questionSetRepository.save(existing);
        return ResponseEntity.noContent().build();
    }

    private QuestionSetApiDto toDto(QuestionSet questionSet) {
        return new QuestionSetApiDto(questionSet.getId(), questionSet.getName(), questionSet.getTopic());
    }
}

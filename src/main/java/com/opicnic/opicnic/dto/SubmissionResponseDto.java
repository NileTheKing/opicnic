package com.opicnic.opicnic.dto;

import java.util.List;

public record SubmissionResponseDto(List<SubmissionResultItemDto> results, List<Integer> failedIndexes) {
}

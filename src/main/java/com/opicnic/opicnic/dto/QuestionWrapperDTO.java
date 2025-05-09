package com.opicnic.opicnic.dto;

import com.opicnic.opicnic.domain.Question;
import lombok.Data;

import java.util.List;

@Data
public class QuestionWrapperDto {
    private List<Question> questions;
}

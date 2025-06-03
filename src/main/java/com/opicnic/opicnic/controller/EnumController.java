package com.opicnic.opicnic.controller;

import com.opicnic.opicnic.domain.enums.Region;
import com.opicnic.opicnic.domain.enums.StudyType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.dto.EnumResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/enums")
public class EnumController {

    @GetMapping("/regions")
    public List<EnumResponse> getRegions() {
        return Arrays.stream(Region.values())
                .map(region -> new EnumResponse(region.name(), region.getLabel()))
                .collect(Collectors.toList());
    }

    @GetMapping("/study-types")
    public List<EnumResponse> getStudyTypes() {
        return Arrays.stream(StudyType.values())
                .map(type -> new EnumResponse(type.name(), type.getLabel()))
                .collect(Collectors.toList());
    }

    // SurveyTopic enum 값을 반환하는 엔드포인트 추가
    @GetMapping("/survey-topics")
    public List<EnumResponse> getSurveyTopics() {
        return Arrays.stream(

                SurveyTopic.values())
                .map(topic -> new EnumResponse(topic.name(), topic.getLabel())) // getLabel() 사용
                .collect(Collectors.toList());
    }

    // SurveyDifficulty enum 값을 반환하는 엔드포인트 추가
    @GetMapping("/survey-difficulties")
    public List<EnumResponse> getSurveyDifficulties() {
        return Arrays.stream(SurveyDifficulty.values())
                .map(difficulty -> new EnumResponse(difficulty.name(), difficulty.getLabel())) // getLabel() 사용
                .collect(Collectors.toList());
    }
}
package com.opicnic.opicnic.exception;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// FU-04 테스트 전용 컨트롤러: handler가 이미 확정된 뒤(컨트롤러 내부)에서 PayloadTooLargeException을
// 던지는 상황을 재현하기 위한 최소 fixture. MultipartFrameworkExceptionIntegrationTest에서만 사용한다.
@RestController
public class ThrowingPayloadTooLargeTestController {

    @PostMapping("/test-support/throws-payload-too-large")
    public String throwsPayloadTooLarge() {
        throw new PayloadTooLargeException("답변 파일이 너무 큽니다.");
    }
}

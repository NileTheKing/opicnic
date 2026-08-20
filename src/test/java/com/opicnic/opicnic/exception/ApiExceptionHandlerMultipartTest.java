package com.opicnic.opicnic.exception;

import com.opicnic.opicnic.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.assertj.core.api.Assertions.assertThat;

// REVIEW-05 회귀 테스트: multipart 관련 프레임워크 예외가 (FU-04 이후) MultipartExceptionHandler에서
// catch-all(500)이 아니라 의미에 맞는 4xx로 매핑되는지 핸들러 메서드 단위로 빠르게 검증한다.
// 이 매핑이 실제 DispatcherServlet의 handler-탐색-전 예외 경로에서도 동작하는지는
// MultipartFrameworkExceptionIntegrationTest(FU-04)가 별도로 증명한다 — 핸들러 메서드를
// 직접 호출하는 것만으로는 "handlerType을 모를 때도 이 advice가 선택되는지"를 증명하지 못한다.
class ApiExceptionHandlerMultipartTest {

    private final MultipartExceptionHandler handler = new MultipartExceptionHandler();

    @Test
    void payloadTooLargeExceptionMapsTo413() {
        ResponseEntity<ErrorResponse> response = handler.handlePayloadTooLarge(new PayloadTooLargeException("too big"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void springMaxUploadSizeExceededMapsTo413() {
        ResponseEntity<ErrorResponse> response =
                handler.handlePayloadTooLarge(new MaxUploadSizeExceededException(150L * 1024 * 1024));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void missingRequestPartMapsTo400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMultipartInputError(new MissingServletRequestPartException("files"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void genericMultipartExceptionMapsTo400() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMultipartInputError(new MultipartException("broken boundary"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

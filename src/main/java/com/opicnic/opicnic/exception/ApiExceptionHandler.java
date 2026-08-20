package com.opicnic.opicnic.exception;

import com.opicnic.opicnic.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

// @RestController가 붙은 API 컨트롤러(EnumController, AdminQuestionSetApiController,
// PracticeAttemptApiController)에만 적용된다. 뷰를 반환하는 @Controller는 대상이 아니다.
@RestControllerAdvice(annotations = RestController.class)
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(RateLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    // API-01: 깨진 JSON body — 클라이언트 실수인데 기존엔 catch-all(500)로 서버 장애 지표에 섞였다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("요청 형식이 올바르지 않습니다."));
    }

    // API-01: URL/쿼리 파라미터 타입 불일치(예: /api/admin/question-sets/not-a-long) — 위와 동일한 이유로 400.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getName() + " 파라미터 형식이 올바르지 않습니다."));
    }

    // REVIEW-05: multipart Content-Type이 아닌 요청이 멀티파트 전용 엔드포인트로 들어옴(Spring이
    // @RequestBody 등에서 요청 Content-Type을 처리할 수 없을 때도 던진다) — catch-all(500)로 새지 않도록 415.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("지원하지 않는 콘텐츠 타입입니다."));
    }

    // FU-04: PayloadTooLargeException/MaxUploadSizeExceededException, MissingServletRequestPartException,
    // MultipartException 매핑은 MultipartExceptionHandler(selector 없는 전역 advice)로 옮겼다.
    // DispatcherServlet이 handler를 찾기 전(checkMultipart 단계)에 이 예외들을 던지면 handlerType이
    // 없어 여기(annotations = RestController.class로 범위가 좁혀진 advice)는 애초에 적용되지 않기 때문이다.

    // 만료/이미 제출된 세션 등 "요청 시점엔 유효했으나 더 이상 유효하지 않은 상태"를 나타내는 데 사용 (예: PracticeAttempt 세션 만료)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleGone(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.GONE).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.internalServerError().body(new ErrorResponse("처리 중 오류가 발생했습니다."));
    }
}

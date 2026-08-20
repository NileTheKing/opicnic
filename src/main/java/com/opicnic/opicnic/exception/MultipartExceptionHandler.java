package com.opicnic.opicnic.exception;

import com.opicnic.opicnic.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

// FU-04: DispatcherServlet은 handler를 찾기 전에(getHandler() 호출 전) checkMultipart()로 멀티파트를
// 먼저 파싱한다. 업로드 용량 초과 같은 예외가 이 시점에 나면 handler/handlerType이 아직 없으므로,
// ApiExceptionHandler처럼 @RestControllerAdvice(annotations = RestController.class)로 대상을
// 좁혀놓은 advice는 "이 예외가 어느 컨트롤러 타입에서 났는지" 판단할 수 없어 아예 적용되지 않는다
// (ExceptionHandlerExceptionResolver가 handlerType=null일 때 selector 있는 advice를 건너뜀).
// 그 결과 오버사이즈 업로드 같은 흔한 클라이언트 실수가 공통 ErrorResponse가 아니라 기본
// Whitelabel/500 에러 페이지로 노출됐다. 그래서 이 advice는 selector 없이(모든 handler에 적용)
// multipart 프레임워크 예외만 다룬다 — ApiExceptionHandler와 매핑이 겹치지 않도록 그쪽에 있던
// 동일 핸들러를 이쪽으로 옮겼다. catch-all은 두지 않는다(다른 예외는 여전히 ApiExceptionHandler 담당).
//
// FU-04 재리뷰 수정: handler가 @RestController로 이미 확정된 경우(예: 컨트롤러 내부에서 직접
// PayloadTooLargeException을 던지는 경로)엔 ApiExceptionHandler도 함께 "적용 가능한" advice가 된다.
// Spring의 ExceptionHandlerExceptionResolver는 advice 목록을 순서대로 훑다가 먼저 매칭되는 advice
// 하나에서 결정하고 멈춘다(여러 advice의 핸들러를 예외 타입 특정도로 비교해 전역 최적을 고르지
// 않는다) — order가 없으면(둘 다 기본 우선순위) 어느 쪽이 먼저 훑히는지가 빈 등록 순서에 좌우돼,
// ApiExceptionHandler의 catch-all(Exception.class)이 먼저 걸려 500으로 새는 게 실제로 재현됐다.
// 이 advice에 HIGHEST_PRECEDENCE를 줘 항상 먼저 검토되도록 강제한다.
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MultipartExceptionHandler {

    @ExceptionHandler({PayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(Exception e) {
        log.warn("업로드 용량 초과: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ErrorResponse("첨부파일이 너무 큽니다."));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ErrorResponse> handleMultipartInputError(Exception e) {
        log.warn("멀티파트 파싱 실패: {}", e.getMessage());
        return ResponseEntity.badRequest().body(new ErrorResponse("첨부파일 요청 형식이 올바르지 않습니다."));
    }
}

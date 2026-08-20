package com.opicnic.opicnic.exception;

import com.opicnic.opicnic.config.RateLimiterService;
import com.opicnic.opicnic.controller.EnumController;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// FU-04 회귀 테스트. 두 가지 서로 다른 경로를 모두 고정한다.
//
// (1) DispatcherServlet.doDispatch()는 handler를 찾기(getHandler()) 전에 checkMultipart()로
// 먼저 멀티파트를 파싱한다. 이 시점에 예외가 나면 handler/handlerType이 아직 없어
// ApiExceptionHandler(@RestControllerAdvice(annotations = RestController.class))처럼 selector가
// 있는 advice는 적용되지 않고 기본 에러 페이지/500으로 샌다.
//
// (2) 컨트롤러 내부에서 직접 PayloadTooLargeException을 던지는 경우(handler가 이미 확정된 뒤)엔
// ApiExceptionHandler도 "적용 가능한" advice가 된다. 재리뷰에서 실측된 문제: order가 없으면
// ExceptionHandlerExceptionResolver가 advice를 훑다가 먼저 걸리는 ApiExceptionHandler의
// catch-all(Exception.class)에서 멈춰버려 413이 아니라 500을 반환했다. MultipartExceptionHandler에
// @Order(HIGHEST_PRECEDENCE)를 줘서 항상 먼저 검토되도록 고정했다 — 이 테스트는 그 순서 자체를 검증한다.
@WebMvcTest(controllers = {EnumController.class, ThrowingPayloadTooLargeTestController.class},
        excludeAutoConfiguration = MultipartAutoConfiguration.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({ApiExceptionHandler.class, MultipartExceptionHandler.class,
        MultipartFrameworkExceptionIntegrationTest.ThrowingMultipartResolverConfig.class})
class MultipartFrameworkExceptionIntegrationTest {

    // 테스트마다 DispatcherServlet.checkMultipart() 단계에서 던질 예외를 이 필드로 제어한다.
    // MockMvc는 이 테스트 메서드와 같은 스레드에서 동기적으로 실행되므로 경쟁 조건이 없다.
    static volatile MultipartException exceptionToThrowOnResolve;

    @TestConfiguration
    static class ThrowingMultipartResolverConfig {
        @Bean
        MultipartResolver multipartResolver() {
            return new MultipartResolver() {
                @Override
                public boolean isMultipart(HttpServletRequest request) {
                    return exceptionToThrowOnResolve != null;
                }

                @Override
                public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) {
                    throw exceptionToThrowOnResolve;
                }

                @Override
                public void cleanupMultipart(MultipartHttpServletRequest request) {
                    // no-op
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // WebConfig(WebMvcConfigurer)가 @WebMvcTest에 자동 포함되면서 RateLimitInterceptor ->
    // RateLimiterService 의존성 체인이 함께 로드되므로 목으로 채워줘야 한다.
    @MockBean
    private RateLimiterService rateLimiterService;

    @AfterEach
    void resetResolverState() {
        exceptionToThrowOnResolve = null;
    }

    @Test
    void oversizedMultipartBeforeHandlerMappingReturns413CommonBody() throws Exception {
        exceptionToThrowOnResolve = new MaxUploadSizeExceededException(1024);

        mockMvc.perform(post("/api/enums/regions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("첨부파일이 너무 큽니다."));
    }

    @Test
    void genericMultipartParsingFailureBeforeHandlerMappingReturns400CommonBody() throws Exception {
        exceptionToThrowOnResolve = new MultipartException("broken boundary");

        mockMvc.perform(post("/api/enums/regions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("첨부파일 요청 형식이 올바르지 않습니다."));
    }

    // 커스텀 리졸버가 예외적인 상황(exceptionToThrowOnResolve != null)이 아닐 때는 checkMultipart()가
    // 원본 request를 그대로 통과시켜야 한다 — 리졸버 교체 자체가 정상 경로를 깨지 않는지 확인한다.
    // 실제 멀티파트 바이너리가 컨트롤러까지 정상 도달하는 경로는 (기본 리졸버로) PracticeAttemptApiControllerCostGuardTest에서 검증한다.
    @Test
    void nonExceptionalRequestStillReachesController() throws Exception {
        exceptionToThrowOnResolve = null;

        mockMvc.perform(get("/api/enums/regions"))
                .andExpect(status().isOk());
    }

    // 재리뷰 회귀 테스트: handler가 이미 확정된 뒤(컨트롤러 내부) 던져진 PayloadTooLargeException은
    // ApiExceptionHandler의 catch-all(500)이 아니라 MultipartExceptionHandler의 413으로 처리돼야 한다.
    @Test
    void controllerInternalPayloadTooLargeReturns413NotCaughtByGenericAdvice() throws Exception {
        exceptionToThrowOnResolve = null; // 이 요청 자체는 멀티파트가 아니다 — 컨트롤러 내부에서 예외가 난다

        mockMvc.perform(post("/test-support/throws-payload-too-large"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("첨부파일이 너무 큽니다."));
    }
}

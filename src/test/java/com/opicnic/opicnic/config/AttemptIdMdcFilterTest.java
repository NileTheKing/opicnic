package com.opicnic.opicnic.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AttemptIdMdcFilterTest {

    private String mdcSeenDownstream(String uri) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {},
                (req, res, next) -> { seen.set(MDC.get("attemptId")); next.doFilter(req, res); });
        new AttemptIdMdcFilter().doFilter(new MockHttpServletRequest("POST", uri), new MockHttpServletResponse(), chain);
        return seen.get();
    }

    @Test
    @DisplayName("/api/practice-attempts/{attemptId}/** 요청 동안 MDC에 attemptId가 있고, 끝나면 제거된다")
    void attemptPath_putsAndRemovesMdc() throws Exception {
        assertThat(mdcSeenDownstream("/api/practice-attempts/abc-123/upload-urls")).isEqualTo("abc-123");
        assertThat(MDC.get("attemptId")).isNull();
    }

    @Test
    @DisplayName("/start, /start-mock, /csrf 처럼 하위 경로가 없는 URL은 attemptId로 잡지 않는다")
    void nonAttemptPath_doesNotTouchMdc() throws Exception {
        assertThat(mdcSeenDownstream("/api/practice-attempts/start")).isNull();
        assertThat(mdcSeenDownstream("/api/practice-attempts/start-mock")).isNull();
        assertThat(mdcSeenDownstream("/analytics/coaching")).isNull();
    }
}

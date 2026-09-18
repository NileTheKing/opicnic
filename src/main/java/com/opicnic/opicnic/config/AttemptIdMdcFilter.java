package com.opicnic.opicnic.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// /api/practice-attempts/{attemptId}/** 요청의 모든 로그 줄에 attemptId를 붙인다(logging.pattern.correlation).
// 하위 경로가 있는 URL만 잡으므로 /start, /start-mock, /csrf는 해당 없음.
// 요청 스레드에만 붙고, FeedbackService가 fork하는 subtask엔 거기서 따로 복사해 넣는다.
@Component
public class AttemptIdMdcFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "attemptId";

    private static final Pattern ATTEMPT_PATH = Pattern.compile("^/api/practice-attempts/([^/]+)/.+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Matcher m = ATTEMPT_PATH.matcher(request.getRequestURI());
        if (!m.matches()) {
            chain.doFilter(request, response);
            return;
        }
        MDC.put(MDC_KEY, m.group(1));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}

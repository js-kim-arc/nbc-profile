package nbc.profile.common.web.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** ADR-0009 — [API - LOG] 표준 포맷 출력. /actuator, /h2-console 제외. */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") || uri.startsWith("/h2-console");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();

        log.info("[API - LOG] {} {} START", method, uri);

        try {
            chain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            int status = response.getStatus();
            log.info("[API - LOG] {} {} {} ({}ms)", method, uri, status, duration);
        }
    }
}

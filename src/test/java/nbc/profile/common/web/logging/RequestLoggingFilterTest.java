package nbc.profile.common.web.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private RequestLoggingFilter filter;
    private ListAppender<ILoggingEvent> appender;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter();
        filterLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        appender = new ListAppender<>();
        appender.start();
        filterLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(appender);
    }

    @Test
    void doFilter_일반요청_START_와_완료_2건_INFO_로그() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/members");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(0).getFormattedMessage())
                .isEqualTo("[API - LOG] POST /api/members START");
        assertThat(appender.list.get(1).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(1).getFormattedMessage())
                .matches("\\[API - LOG\\] POST /api/members 201 \\(\\d+ms\\)");
    }

    @Test
    void shouldNotFilter_actuator_path_제외() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_h2_console_path_제외() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/h2-console/");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void shouldNotFilter_일반_api_path_통과() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/members/1");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void doFilter_chain_예외_finally가_완료로그_출력() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/members/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        FilterChain failingChain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        try {
            filter.doFilter(request, response, failingChain);
        } catch (RuntimeException ignored) {
        }

        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getFormattedMessage())
                .isEqualTo("[API - LOG] GET /api/members/boom START");
        assertThat(appender.list.get(1).getFormattedMessage())
                .matches("\\[API - LOG\\] GET /api/members/boom 500 \\(\\d+ms\\)");
    }
}

package nbc.profile.config;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.auditing.DateTimeProvider;

@TestConfiguration
public class TestDateTimeProviderConfig {

    @Bean
    @Primary
    public DateTimeProvider auditingDateTimeProvider() {
        AtomicLong counter = new AtomicLong(0);
        return () -> Optional.of(
                LocalDateTime.now().plusNanos(counter.incrementAndGet() * 1_000_000L));
    }
}

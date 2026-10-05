package online.wanan.xingchen.core;

import online.wanan.xingchen.console.ConsoleLoginRateLimiter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ConsoleLoginRateLimiterTest {
    @Test void auth009_limitsByRemoteAddressAndNormalizedUsernameAndClearsAfterSuccess() {
        var limiter = new ConsoleLoginRateLimiter();
        for (int i=0;i<5;i++) limiter.failure("127.0.0.1", "Admin");
        assertThat(limiter.blocked("127.0.0.1", "admin")).isTrue();
        assertThat(limiter.blocked("127.0.0.2", "admin")).isFalse();
        limiter.success("127.0.0.1", "admin");
        assertThat(limiter.blocked("127.0.0.1", "admin")).isFalse();
    }
}

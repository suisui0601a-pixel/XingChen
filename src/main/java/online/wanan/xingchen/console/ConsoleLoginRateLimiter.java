package online.wanan.xingchen.console;

import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class ConsoleLoginRateLimiter {
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public ConsoleLoginRateLimiter() { this(Clock.systemUTC()); }
    ConsoleLoginRateLimiter(Clock clock) { this.clock = clock; }

    public boolean blocked(String address, String username) {
        Bucket bucket = buckets.get(key(address, username));
        if (bucket == null) return false;
        synchronized (bucket) {
            if (Duration.between(bucket.firstFailure, clock.instant()).compareTo(WINDOW) >= 0) {
                buckets.remove(key(address, username), bucket); return false;
            }
            return bucket.failures >= MAX_FAILURES;
        }
    }

    public void failure(String address, String username) {
        String key = key(address, username); Instant now = clock.instant();
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> new Bucket(now));
        synchronized (bucket) {
            if (Duration.between(bucket.firstFailure, now).compareTo(WINDOW) >= 0) {
                bucket.firstFailure = now; bucket.failures = 0;
            }
            bucket.failures++;
        }
        if (buckets.size() > 10_000) buckets.entrySet().removeIf(entry -> Duration.between(entry.getValue().firstFailure, now).compareTo(WINDOW.multipliedBy(2)) >= 0);
    }

    public void success(String address, String username) { buckets.remove(key(address, username)); }
    private static String key(String address, String username) { return address + "\u0000" + username.toLowerCase(java.util.Locale.ROOT); }
    private static final class Bucket { private Instant firstFailure; private int failures; private Bucket(Instant firstFailure) { this.firstFailure = firstFailure; } }
}

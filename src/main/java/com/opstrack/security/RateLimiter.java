package com.opstrack.security;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Fixed-window limiter: each client gets a number of requests per minute, and
 * the count resets when its window ends. Counts live in this process's memory,
 * so they reset on restart and are not shared between instances.
 */
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000;

    private record Window(long start, int count) {
    }

    private final int limit;
    private final LongSupplier clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private volatile long nextCleanup;

    public RateLimiter(int limit) {
        this(limit, System::currentTimeMillis);
    }

    RateLimiter(int limit, LongSupplier clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("Rate limit must be at least 1 request per minute");
        }
        this.limit = limit;
        this.clock = clock;
    }

    /** Counts one request and returns whether the client is still within its limit. */
    public boolean tryAcquire(String client) {
        long now = clock.getAsLong();
        removeExpired(now);
        // compute() is atomic per key, so concurrent requests cannot lose a count.
        Window window = windows.compute(client, (key, current) ->
                current == null || now - current.start() >= WINDOW_MILLIS
                        ? new Window(now, 1)
                        : new Window(current.start(), Math.min(current.count() + 1, limit + 1)));
        return window.count() <= limit;
    }

    public long secondsUntilReset(String client) {
        Window window = windows.get(client);
        if (window == null) {
            return 0;
        }
        long remaining = window.start() + WINDOW_MILLIS - clock.getAsLong();
        return Math.max(1, (remaining + 999) / 1000);
    }

    /** Stops the map growing forever as new clients appear. */
    private void removeExpired(long now) {
        if (now >= nextCleanup) {
            nextCleanup = now + WINDOW_MILLIS;
            windows.values().removeIf(window -> now - window.start() >= WINDOW_MILLIS);
        }
    }
}

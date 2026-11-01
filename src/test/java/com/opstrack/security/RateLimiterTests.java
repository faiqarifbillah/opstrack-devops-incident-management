package com.opstrack.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Uses a fake clock, so the test can jump a minute ahead without waiting. */
class RateLimiterTests {

    private final AtomicLong now = new AtomicLong(1_000_000);

    @Test
    void allowsUpToTheLimitThenBlocks() {
        RateLimiter limiter = new RateLimiter(2, now::get);

        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isTrue();
        assertThat(limiter.tryAcquire("a")).isFalse();
    }

    @Test
    void countResetsWhenTheWindowEnds() {
        RateLimiter limiter = new RateLimiter(1, now::get);
        limiter.tryAcquire("a");
        assertThat(limiter.tryAcquire("a")).isFalse();

        now.addAndGet(59_999);
        assertThat(limiter.tryAcquire("a")).isFalse();

        now.addAndGet(1);
        assertThat(limiter.tryAcquire("a")).isTrue();
    }

    @Test
    void reportsSecondsUntilReset() {
        RateLimiter limiter = new RateLimiter(1, now::get);
        limiter.tryAcquire("a");

        now.addAndGet(30_500);

        assertThat(limiter.secondsUntilReset("a")).isEqualTo(30);
    }

    @Test
    void expiredClientsAreRemovedFromMemory() {
        RateLimiter limiter = new RateLimiter(1, now::get);
        limiter.tryAcquire("old");

        now.addAndGet(60_000);
        limiter.tryAcquire("new");

        assertThat(limiter.secondsUntilReset("old")).isZero();
    }

    @Test
    void concurrentRequestsCannotExceedTheLimit() throws Exception {
        RateLimiter limiter = new RateLimiter(100, now::get);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                tasks.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < 50; j++) {
                        if (limiter.tryAcquire("a")) {
                            allowed.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> task : tasks) {
                task.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(allowed).hasValue(100);
    }

    @Test
    void rejectsALimitBelowOne() {
        assertThatThrownBy(() -> new RateLimiter(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

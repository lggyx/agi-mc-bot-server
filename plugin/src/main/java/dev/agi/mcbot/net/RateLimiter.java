package dev.agi.mcbot.net;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple sliding window rate limiter.
 */
public class RateLimiter {
    private final int maxPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(int maxPerMinute) {
        this.maxPerMinute = maxPerMinute;
    }

    public boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        Window w = windows.computeIfAbsent(key, k -> new Window(now));
        synchronized (w) {
            if (now - w.start >= 60_000) {
                w.start = now;
                w.count.set(0);
            }
            return w.count.incrementAndGet() <= maxPerMinute;
        }
    }

    public void cleanup() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().start >= 120_000);
    }

    private static class Window {
        volatile long start;
        final AtomicInteger count = new AtomicInteger();

        Window(long start) {
            this.start = start;
        }
    }
}

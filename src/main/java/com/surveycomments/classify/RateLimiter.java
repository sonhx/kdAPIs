package com.surveycomments.classify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Token-bucket / interval rate limiter to enforce strict requests-per-minute (RPM) limits
 * on Gemini Developer API calls.
 */
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final int requestsPerMinute;
    private final long minIntervalMs;
    private long lastRequestTimeMs = 0;

    public RateLimiter(int requestsPerMinute) {
        this.requestsPerMinute = Math.max(1, requestsPerMinute);
        // Interval between requests with small safety margin (e.g. 10 RPM -> 6000ms)
        this.minIntervalMs = 60_000L / this.requestsPerMinute;
    }

    /**
     * Acquire permission to execute an API request, blocking if necessary to honour the RPM limit.
     */
    public synchronized void acquire() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTimeMs;
        if (elapsed < minIntervalMs) {
            long waitTime = minIntervalMs - elapsed;
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[survey-comments] RateLimiter sleep interrupted");
            }
        }
        lastRequestTimeMs = System.currentTimeMillis();
    }
}


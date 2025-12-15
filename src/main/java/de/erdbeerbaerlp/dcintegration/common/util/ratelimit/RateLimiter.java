package de.erdbeerbaerlp.dcintegration.common.util.ratelimit;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiter for Discord API requests.
 * Tracks rate limits per endpoint/channel and implements exponential backoff.
 */
public class RateLimiter {
    private final AtomicInteger remainingRequests = new AtomicInteger(50); // Default Discord limit
    private final AtomicLong resetTime = new AtomicLong(System.currentTimeMillis() + 1000); // Reset in 1 second
    private final AtomicInteger consecutiveErrors = new AtomicInteger(0);
    private final AtomicLong lastRequestTime = new AtomicLong(0);
    
    private static final int MAX_REQUESTS_PER_SECOND = 50; // Discord's rate limit
    private static final long RATE_LIMIT_WINDOW_MS = 1000; // 1 second window
    private static final long MAX_BACKOFF_MS = 60000; // Max 60 second backoff
    
    /**
     * Checks if a request can be made now
     * 
     * @return true if request can be made, false if rate limited
     */
    public boolean canMakeRequest() {
        long now = System.currentTimeMillis();
        
        // Reset if window expired
        if (now >= resetTime.get()) {
            remainingRequests.set(MAX_REQUESTS_PER_SECOND);
            resetTime.set(now + RATE_LIMIT_WINDOW_MS);
            consecutiveErrors.set(0);
        }
        
        return remainingRequests.get() > 0;
    }
    
    /**
     * Records that a request was made
     */
    public void recordRequest() {
        remainingRequests.decrementAndGet();
        lastRequestTime.set(System.currentTimeMillis());
        consecutiveErrors.set(0); // Reset error count on successful request
    }
    
    /**
     * Records a rate limit hit
     * 
     * @param retryAfterMs Milliseconds to wait before retrying
     */
    public void recordRateLimit(long retryAfterMs) {
        resetTime.set(System.currentTimeMillis() + retryAfterMs);
        remainingRequests.set(0);
        consecutiveErrors.incrementAndGet();
        DiscordIntegration.LOGGER.warn("Rate limit hit! Retry after {}ms", retryAfterMs);
    }
    
    /**
     * Records an error (for exponential backoff)
     */
    public void recordError() {
        consecutiveErrors.incrementAndGet();
    }
    
    /**
     * Gets the time to wait before next request (for backoff)
     * 
     * @return Milliseconds to wait
     */
    public long getBackoffTime() {
        long now = System.currentTimeMillis();
        long resetAt = resetTime.get();
        
        if (now < resetAt) {
            return resetAt - now;
        }
        
        // Exponential backoff based on consecutive errors
        int errors = consecutiveErrors.get();
        if (errors > 0) {
            long backoff = Math.min((long) Math.pow(2, errors) * 1000, MAX_BACKOFF_MS);
            return backoff;
        }
        
        return 0;
    }
    
    /**
     * Gets remaining requests in current window
     * 
     * @return Number of remaining requests
     */
    public int getRemainingRequests() {
        long now = System.currentTimeMillis();
        if (now >= resetTime.get()) {
            return MAX_REQUESTS_PER_SECOND;
        }
        return Math.max(0, remainingRequests.get());
    }
    
    /**
     * Gets time until rate limit resets
     * 
     * @return Milliseconds until reset
     */
    public long getTimeUntilReset() {
        long now = System.currentTimeMillis();
        long resetAt = resetTime.get();
        return Math.max(0, resetAt - now);
    }
    
    /**
     * Resets the rate limiter (for testing or manual reset)
     */
    public void reset() {
        remainingRequests.set(MAX_REQUESTS_PER_SECOND);
        resetTime.set(System.currentTimeMillis() + RATE_LIMIT_WINDOW_MS);
        consecutiveErrors.set(0);
    }
}


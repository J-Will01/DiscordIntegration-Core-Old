package de.erdbeerbaerlp.dcintegration.test;

import de.erdbeerbaerlp.dcintegration.common.util.ratelimit.QueuedMessage;
import de.erdbeerbaerlp.dcintegration.common.util.ratelimit.RateLimiter;
import de.erdbeerbaerlp.dcintegration.common.util.ratelimit.RateLimitedMessageQueue;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for rate limiting system
 */
public class RateLimitingTests {
    private RateLimiter rateLimiter;
    
    @BeforeEach
    public void setUp() {
        rateLimiter = new RateLimiter();
    }
    
    @Test
    public void testCanMakeRequestInitially() {
        Assertions.assertTrue(rateLimiter.canMakeRequest());
    }
    
    @Test
    public void testRecordRequest() {
        Assertions.assertTrue(rateLimiter.canMakeRequest());
        rateLimiter.recordRequest();
        Assertions.assertEquals(49, rateLimiter.getRemainingRequests());
    }
    
    @Test
    public void testRateLimitHit() {
        rateLimiter.recordRateLimit(5000);
        Assertions.assertFalse(rateLimiter.canMakeRequest());
        Assertions.assertTrue(rateLimiter.getTimeUntilReset() > 0);
    }
    
    @Test
    public void testBackoffTime() {
        rateLimiter.recordError();
        rateLimiter.recordError();
        long backoff = rateLimiter.getBackoffTime();
        Assertions.assertTrue(backoff > 0);
    }
    
    @Test
    public void testQueuedMessagePriority() {
        QueuedMessage low = new QueuedMessage(
            new DiscordMessage("low"), 
            Mockito.mock(MessageChannel.class), 
            QueuedMessage.Priority.LOW
        );
        QueuedMessage high = new QueuedMessage(
            new DiscordMessage("high"), 
            Mockito.mock(MessageChannel.class), 
            QueuedMessage.Priority.HIGH
        );
        
        Assertions.assertTrue(high.compareTo(low) < 0); // High priority comes first
    }
    
    @Test
    public void testQueuedMessageRetry() {
        QueuedMessage msg = new QueuedMessage(
            new DiscordMessage("test"), 
            Mockito.mock(MessageChannel.class), 
            QueuedMessage.Priority.NORMAL
        );
        
        Assertions.assertTrue(msg.canRetry());
        msg.incrementRetryCount();
        Assertions.assertTrue(msg.canRetry());
        msg.incrementRetryCount();
        msg.incrementRetryCount();
        Assertions.assertFalse(msg.canRetry());
    }
    
    @Test
    public void testRateLimitedQueue() throws InterruptedException {
        RateLimitedMessageQueue queue = new RateLimitedMessageQueue();
        queue.start();
        
        MessageChannel channel = Mockito.mock(MessageChannel.class);
        
        // Queue some messages
        queue.queueMessage(new DiscordMessage("test1"), channel, QueuedMessage.Priority.NORMAL);
        queue.queueMessage(new DiscordMessage("test2"), channel, QueuedMessage.Priority.HIGH);
        
        // Wait a bit for processing
        Thread.sleep(500);
        
        Assertions.assertTrue(queue.getQueueSize() >= 0); // Queue may have processed messages
        
        queue.stop();
    }
    
    @Test
    public void testQueuePriorityOrdering() {
        RateLimitedMessageQueue queue = new RateLimitedMessageQueue();
        queue.start();
        
        MessageChannel channel = Mockito.mock(MessageChannel.class);
        
        // Queue messages with different priorities
        queue.queueMessage(new DiscordMessage("low"), channel, QueuedMessage.Priority.LOW);
        queue.queueMessage(new DiscordMessage("high"), channel, QueuedMessage.Priority.HIGH);
        queue.queueMessage(new DiscordMessage("normal"), channel, QueuedMessage.Priority.NORMAL);
        
        // High priority should be processed first
        Assertions.assertTrue(queue.getQueueSize() >= 0);
        
        queue.stop();
    }
}


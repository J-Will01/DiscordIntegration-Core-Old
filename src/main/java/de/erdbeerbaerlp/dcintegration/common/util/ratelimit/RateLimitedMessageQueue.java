package de.erdbeerbaerlp.dcintegration.common.util.ratelimit;

import de.erdbeerbaerlp.dcintegration.common.DiscordIntegration;
import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rate-limited message queue with priority support.
 * Automatically handles rate limits, backoff, and retries.
 */
public class RateLimitedMessageQueue {
    private final PriorityBlockingQueue<QueuedMessage> queue = new PriorityBlockingQueue<>();
    private final RateLimiter rateLimiter = new RateLimiter();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    
    private static final int PROCESSING_INTERVAL_MS = 100; // Check queue every 100ms
    private static final int MAX_BATCH_SIZE = 5; // Max messages to send per batch
    
    public RateLimitedMessageQueue() {
        start();
    }
    
    /**
     * Starts the queue processor
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::processQueue, 0, PROCESSING_INTERVAL_MS, TimeUnit.MILLISECONDS);
            DiscordIntegration.LOGGER.info("Rate-limited message queue started");
        }
    }
    
    /**
     * Stops the queue processor
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            DiscordIntegration.LOGGER.info("Rate-limited message queue stopped");
        }
    }
    
    /**
     * Pauses queue processing (useful during rate limit periods)
     */
    public void pause() {
        paused.set(true);
    }
    
    /**
     * Resumes queue processing
     */
    public void resume() {
        paused.set(false);
    }
    
    /**
     * Queues a message for sending
     * 
     * @param message Message to send
     * @param channel Target channel
     * @param priority Message priority
     */
    public void queueMessage(@NotNull DiscordMessage message, @NotNull MessageChannel channel, 
                            @NotNull QueuedMessage.Priority priority) {
        queueMessage(message, channel, priority, null);
    }
    
    /**
     * Queues a message for sending with callback
     * 
     * @param message Message to send
     * @param channel Target channel
     * @param priority Message priority
     * @param callback Callback for success/failure
     */
    public void queueMessage(@NotNull DiscordMessage message, @NotNull MessageChannel channel,
                            @NotNull QueuedMessage.Priority priority, java.util.function.Consumer<Boolean> callback) {
        if (!running.get()) {
            DiscordIntegration.LOGGER.warn("Queue is not running, message will be lost");
            if (callback != null) {
                callback.accept(false);
            }
            return;
        }
        
        QueuedMessage queuedMessage = new QueuedMessage(message, channel, priority, callback);
        queue.offer(queuedMessage);
        DiscordIntegration.LOGGER.debug("Message queued: {}", queuedMessage);
    }
    
    /**
     * Processes the message queue
     */
    private void processQueue() {
        if (!running.get() || paused.get()) {
            return;
        }
        
        // Check rate limit
        if (!rateLimiter.canMakeRequest()) {
            long waitTime = rateLimiter.getBackoffTime();
            if (waitTime > 0) {
                DiscordIntegration.LOGGER.debug("Rate limited, waiting {}ms", waitTime);
                pause();
                scheduler.schedule(() -> resume(), waitTime, TimeUnit.MILLISECONDS);
            }
            return;
        }
        
        // Process up to MAX_BATCH_SIZE messages
        int processed = 0;
        while (processed < MAX_BATCH_SIZE && !queue.isEmpty() && rateLimiter.canMakeRequest()) {
            QueuedMessage queuedMessage = queue.poll();
            if (queuedMessage == null) {
                break;
            }
            
            try {
                sendMessage(queuedMessage);
                rateLimiter.recordRequest();
                processed++;
            } catch (Exception e) {
                DiscordIntegration.LOGGER.error("Error sending queued message", e);
                handleSendError(queuedMessage, e);
            }
        }
    }
    
    /**
     * Sends a queued message
     */
    private void sendMessage(@NotNull QueuedMessage queuedMessage) {
        DiscordIntegration.INSTANCE.sendMessage(
            queuedMessage.getMessage(),
            queuedMessage.getChannel()
        );
        queuedMessage.notifyCallback(true);
    }
    
    /**
     * Handles send errors with retry logic
     */
    private void handleSendError(@NotNull QueuedMessage queuedMessage, @NotNull Exception error) {
        // Check if it's a rate limit error
        String errorMsg = error.getMessage();
        if (errorMsg != null && (errorMsg.contains("rate limit") || errorMsg.contains("429"))) {
            // Extract retry-after if available
            long retryAfter = 1000; // Default 1 second
            rateLimiter.recordRateLimit(retryAfter);
            pause();
            scheduler.schedule(() -> resume(), retryAfter, TimeUnit.MILLISECONDS);
        } else {
            rateLimiter.recordError();
        }
        
        // Retry if possible
        if (queuedMessage.canRetry()) {
            queuedMessage.incrementRetryCount();
            // Re-queue with slight delay
            scheduler.schedule(() -> queue.offer(queuedMessage), 
                rateLimiter.getBackoffTime(), TimeUnit.MILLISECONDS);
            DiscordIntegration.LOGGER.debug("Retrying message (attempt {})", queuedMessage.getRetryCount());
        } else {
            DiscordIntegration.LOGGER.error("Message failed after {} retries, dropping", queuedMessage.getRetryCount());
            queuedMessage.notifyCallback(false);
        }
    }
    
    /**
     * Gets the current queue size
     * 
     * @return Number of messages in queue
     */
    public int getQueueSize() {
        return queue.size();
    }
    
    /**
     * Gets queue size by priority
     * 
     * @param priority Priority level
     * @return Number of messages with this priority
     */
    public int getQueueSize(@NotNull QueuedMessage.Priority priority) {
        return (int) queue.stream()
            .filter(msg -> msg.getPriority() == priority)
            .count();
    }
    
    /**
     * Clears the queue
     */
    public void clear() {
        queue.clear();
    }
    
    /**
     * Gets the rate limiter instance
     * 
     * @return Rate limiter
     */
    @NotNull
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }
}


package de.erdbeerbaerlp.dcintegration.common.util.ratelimit;

import de.erdbeerbaerlp.dcintegration.common.util.DiscordMessage;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Represents a message queued for sending with priority and metadata.
 */
public class QueuedMessage implements Comparable<QueuedMessage> {
    public enum Priority {
        LOW(0),
        NORMAL(1),
        HIGH(2),
        URGENT(3);
        
        private final int value;
        
        Priority(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    private final DiscordMessage message;
    private final MessageChannel channel;
    private final Priority priority;
    private final long timestamp;
    private final Consumer<Boolean> callback; // Called with success/failure
    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;
    
    public QueuedMessage(@NotNull DiscordMessage message, @NotNull MessageChannel channel, @NotNull Priority priority) {
        this(message, channel, priority, null);
    }
    
    public QueuedMessage(@NotNull DiscordMessage message, @NotNull MessageChannel channel, 
                        @NotNull Priority priority, Consumer<Boolean> callback) {
        this.message = message;
        this.channel = channel;
        this.priority = priority;
        this.timestamp = System.currentTimeMillis();
        this.callback = callback;
    }
    
    public DiscordMessage getMessage() {
        return message;
    }
    
    public MessageChannel getChannel() {
        return channel;
    }
    
    public Priority getPriority() {
        return priority;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getRetryCount() {
        return retryCount;
    }
    
    public void incrementRetryCount() {
        retryCount++;
    }
    
    public boolean canRetry() {
        return retryCount < MAX_RETRIES;
    }
    
    public void notifyCallback(boolean success) {
        if (callback != null) {
            try {
                callback.accept(success);
            } catch (Exception e) {
                de.erdbeerbaerlp.dcintegration.common.DiscordIntegration.LOGGER.error("Error in message callback", e);
            }
        }
    }
    
    @Override
    public int compareTo(@NotNull QueuedMessage other) {
        // Higher priority first
        int priorityCompare = Integer.compare(other.priority.getValue(), this.priority.getValue());
        if (priorityCompare != 0) {
            return priorityCompare;
        }
        // Older messages first (FIFO for same priority)
        return Long.compare(this.timestamp, other.timestamp);
    }
    
    @Override
    public String toString() {
        return "QueuedMessage{priority=" + priority + ", timestamp=" + timestamp + ", retryCount=" + retryCount + "}";
    }
}


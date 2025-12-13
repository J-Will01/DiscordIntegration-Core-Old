package de.erdbeerbaerlp.dcintegration.test.util;

import de.erdbeerbaerlp.dcintegration.common.WorkThread;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * Helper utility for testing WorkThread async operations.
 */
public class WorkThreadTestHelper {
    
    /**
     * Waits for all pending WorkThread jobs to complete.
     * Uses reflection to check the job queue and unpark the thread.
     */
    public static void waitForWorkThreadJobs(long timeoutMs) throws InterruptedException {
        long start = System.currentTimeMillis();
        long checkInterval = 50; // Check every 50ms
        
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                // Get the job queue
                Field queueField = WorkThread.class.getDeclaredField("jobQueue");
                queueField.setAccessible(true);
                @SuppressWarnings("unchecked")
                LinkedList<?> queue = (LinkedList<?>) queueField.get(null);
                
                // Get the runner thread
                Field runnerField = WorkThread.class.getDeclaredField("runner");
                runnerField.setAccessible(true);
                Thread runner = (Thread) runnerField.get(null);
                
                // If queue is empty and thread is parked, we're done
                if (queue.isEmpty()) {
                    // Unpark once more to ensure any final jobs are processed
                    if (runner != null) {
                        java.util.concurrent.locks.LockSupport.unpark(runner);
                    }
                    Thread.sleep(checkInterval);
                    // Check again to make sure it's still empty
                    if (queue.isEmpty()) {
                        break;
                    }
                } else {
                    // Unpark to process jobs
                    if (runner != null) {
                        java.util.concurrent.locks.LockSupport.unpark(runner);
                    }
                }
                
                Thread.sleep(checkInterval);
            } catch (Exception e) {
                // If reflection fails, just sleep
                Thread.sleep(checkInterval);
            }
        }
        
        // Final wait to ensure all async operations complete
        Thread.sleep(100);
    }
    
    /**
     * Waits for WorkThread jobs with default timeout (2 seconds)
     */
    public static void waitForWorkThreadJobs() throws InterruptedException {
        waitForWorkThreadJobs(2000);
    }
}


package com.yingshi.server.service.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PushDispatchSupport {

    private static final Logger log = LoggerFactory.getLogger(PushDispatchSupport.class);

    /**
     * Dedicated virtual-thread executor for push side effects.
     * Avoids ForkJoinPool.commonPool() which can starve when threads sleep for delays.
     */
    private static final ExecutorService PUSH_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private PushDispatchSupport() {
    }

    public static void afterCommitAsync(Runnable action) {
        afterCommitAsync(action, 0L);
    }

    /**
     * Execute an action asynchronously after the current transaction commits.
     * If no transaction is active, the action runs immediately (after the optional delay).
     *
     * @param action       the action to execute
     * @param delayMillis  delay in milliseconds before executing the action (0 for no delay)
     */
    public static void afterCommitAsync(Runnable action, long delayMillis) {
        Runnable asyncAction = () -> PUSH_EXECUTOR.submit(() -> {
            try {
                if (delayMillis > 0L) {
                    Thread.sleep(delayMillis);
                }
                action.run();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Async push side effect interrupted.", exception);
            } catch (Exception exception) {
                log.warn("Async push side effect failed.", exception);
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncAction.run();
                }
            });
            return;
        }
        asyncAction.run();
    }
}

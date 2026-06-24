package com.yingshi.server.service.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

public final class PushDispatchSupport {

    private static final Logger log = LoggerFactory.getLogger(PushDispatchSupport.class);

    private PushDispatchSupport() {
    }

    public static void afterCommitAsync(Runnable action) {
        Runnable asyncAction = () -> CompletableFuture.runAsync(() -> {
            try {
                action.run();
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

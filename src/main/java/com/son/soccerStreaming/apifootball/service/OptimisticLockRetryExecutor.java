package com.son.soccerStreaming.apifootball.service;

import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Slf4j
@Component
public class OptimisticLockRetryExecutor {

    private final TransactionTemplate transactionTemplate;
    private final int maxRetries;

    @Autowired
    public OptimisticLockRetryExecutor(
            PlatformTransactionManager transactionManager,
            @Value("${api-football.sync.optimistic-lock.max-retries:2}") int maxRetries
    ) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate = template;
        this.maxRetries = Math.max(0, maxRetries);
    }

    OptimisticLockRetryExecutor(TransactionTemplate transactionTemplate, int maxRetries) {
        this.transactionTemplate = transactionTemplate;
        this.maxRetries = Math.max(0, maxRetries);
    }

    public <T> T execute(String operation, Supplier<T> action) {
        int totalAttempts = maxRetries + 1;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                return transactionTemplate.execute(status -> action.get());
            } catch (RuntimeException exception) {
                if (!isOptimisticLockConflict(exception) || attempt == totalAttempts) {
                    throw exception;
                }
                log.warn("Optimistic lock conflict during sync persistence. Retrying in a new transaction. "
                                + "operation={}, attempt={}, maxAttempts={}",
                        operation, attempt, totalAttempts);
            }
        }
        throw new IllegalStateException("Optimistic lock retry loop completed unexpectedly. operation=" + operation);
    }

    public static boolean isOptimisticLockConflict(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException
                    || current instanceof OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

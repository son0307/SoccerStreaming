package com.son.soccerStreaming.apifootball.service;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OptimisticLockRetryExecutorTest {

    @Test
    void retriesOptimisticLockConflictTwiceAndThenReturnsSuccessfulResult() {
        OptimisticLockRetryExecutor executor = executor(2);
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute("fixture-player-stat", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ObjectOptimisticLockingFailureException("PlayerFixtureStat", 1L);
            }
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void propagatesOptimisticLockConflictAfterConfiguredRetriesAreExhausted() {
        OptimisticLockRetryExecutor executor = executor(2);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute("team", () -> {
            attempts.incrementAndGet();
            throw new OptimisticLockException("conflict");
        })).isInstanceOf(OptimisticLockException.class);

        assertThat(attempts).hasValue(3);
    }

    @Test
    void doesNotRetryUnrelatedFailure() {
        OptimisticLockRetryExecutor executor = executor(2);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> executor.execute("team", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(attempts).hasValue(1);
    }

    @Test
    void retriesConflictRaisedDuringCommitWithRequiresNewTransactionEachTime() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        doThrow(new ObjectOptimisticLockingFailureException("FixtureStat", 1L))
                .doThrow(new ObjectOptimisticLockingFailureException("FixtureStat", 1L))
                .doNothing()
                .when(transactionManager).commit(transactionStatus);
        OptimisticLockRetryExecutor executor = new OptimisticLockRetryExecutor(transactionManager, 2);
        AtomicInteger attempts = new AtomicInteger();

        String result = executor.execute("fixture-stat", () -> {
            attempts.incrementAndGet();
            return "success";
        });

        assertThat(result).isEqualTo("success");
        assertThat(attempts).hasValue(3);
        var definitionCaptor = org.mockito.ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(3)).getTransaction(definitionCaptor.capture());
        assertThat(definitionCaptor.getAllValues())
                .allMatch(definition -> definition.getPropagationBehavior()
                        == TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private OptimisticLockRetryExecutor executor(int maxRetries) {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return new OptimisticLockRetryExecutor(transactionTemplate, maxRetries);
    }
}

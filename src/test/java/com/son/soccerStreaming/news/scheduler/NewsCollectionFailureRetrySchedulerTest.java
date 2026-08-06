package com.son.soccerStreaming.news.scheduler;

import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.news.service.NewsCollectionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NewsCollectionFailureRetrySchedulerTest {

    private NewsCollectionFailureRetryScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    @Test
    void schedulesAndRetriesOnlyRetryableFailedTeams() {
        NewsCollectionService collectionService = mock(NewsCollectionService.class);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        @SuppressWarnings("unchecked")
        ScheduledFuture<Object> future = mock(ScheduledFuture.class);
        Runnable[] scheduledAction = new Runnable[1];
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    scheduledAction[0] = invocation.getArgument(0);
                    return future;
                });
        when(collectionService.collectTeam(1L)).thenReturn(2);
        scheduler = scheduler(collectionService, executor);

        scheduler.replacePendingRetries(List.of(
                new NewsCollectionService.FailedTeam(
                        1L, "Arsenal", ExternalApiErrorCategory.TIMEOUT, true, null),
                new NewsCollectionService.FailedTeam(
                        2L, "Chelsea", ExternalApiErrorCategory.QUOTA_EXHAUSTED, false, null)
        ));

        assertThat(scheduler.pendingRetryCount()).isEqualTo(1);
        assertThat(scheduledAction[0]).isNotNull();

        scheduledAction[0].run();

        verify(collectionService).collectTeam(1L);
        verify(collectionService, never()).collectTeam(2L);
        assertThat(scheduler.pendingRetryCount()).isZero();
    }

    private NewsCollectionFailureRetryScheduler scheduler(
            NewsCollectionService collectionService,
            ScheduledExecutorService executor
    ) {
        NewsCollectionFailureRetryScheduler created =
                new NewsCollectionFailureRetryScheduler(collectionService, executor);
        ReflectionTestUtils.setField(created, "enabled", true);
        ReflectionTestUtils.setField(created, "maxAttempts", 2);
        ReflectionTestUtils.setField(created, "initialDelay", Duration.ZERO);
        ReflectionTestUtils.setField(created, "delayMultiplier", 3L);
        ReflectionTestUtils.setField(created, "maxDelay", Duration.ofMinutes(30));
        return created;
    }
}

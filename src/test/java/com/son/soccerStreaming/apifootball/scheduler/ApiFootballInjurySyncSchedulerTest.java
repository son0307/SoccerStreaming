package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballInjuryReferenceSyncException;
import com.son.soccerStreaming.apifootball.service.ApiFootballInjurySyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiFootballInjurySyncSchedulerTest {

    @Test
    void referenceNotFoundFailureIsNotRetryable() {
        ApiFootballSyncFailureRetryScheduler retryScheduler = new ApiFootballSyncFailureRetryScheduler(
                mock(ApiFootballSyncStatusService.class),
                new ApiFootballSyncExecutionGuard()
        );
        try {
            org.assertj.core.api.Assertions.assertThat(retryScheduler.shouldRetry(
                    new ApiFootballInjuryReferenceSyncException(1, 0, 0)
            )).isFalse();
        } finally {
            retryScheduler.shutdown();
        }
    }

    @Test
    void delegatesNonRetryableFailureToTheBatchSchedulerForTerminalAggregation() {
        ApiFootballInjurySyncService syncService = mock(ApiFootballInjurySyncService.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        ApiFootballInjuryReferenceSyncException failure =
                new ApiFootballInjuryReferenceSyncException(1, 0, 0);
        org.mockito.Mockito.when(syncService.syncInjuries(39, 2025)).thenThrow(failure);
        ApiFootballInjurySyncScheduler scheduler = new ApiFootballInjurySyncScheduler(
                syncService,
                retryScheduler,
                new ApiFootballSyncExecutionGuard()
        );
        ReflectionTestUtils.setField(scheduler, "league", 39);
        ReflectionTestUtils.setField(scheduler, "season", 2025);

        scheduler.syncInjuriesDaily();

        verify(retryScheduler).schedule(
                org.mockito.ArgumentMatchers.eq("injuries:39:2025"),
                org.mockito.ArgumentMatchers.eq("injuries:league=39; season=2025"),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.same(failure),
                org.mockito.ArgumentMatchers.any(Runnable.class)
        );
    }
}

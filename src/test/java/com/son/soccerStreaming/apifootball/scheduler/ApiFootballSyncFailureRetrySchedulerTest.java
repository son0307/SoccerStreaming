package com.son.soccerStreaming.apifootball.scheduler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiFootballSyncFailureRetrySchedulerTest {

    private final ApiFootballSyncStatusService syncStatusService = mock(ApiFootballSyncStatusService.class);
    private final ApiFootballSyncExecutionGuard executionGuard = new ApiFootballSyncExecutionGuard();
    private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
    private final ApiFootballSyncFailureRetryScheduler scheduler =
            new ApiFootballSyncFailureRetryScheduler(syncStatusService, executionGuard, executor);
    private Logger schedulerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    ApiFootballSyncFailureRetrySchedulerTest() {
        ReflectionTestUtils.setField(scheduler, "enabled", true);
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 2);
        ReflectionTestUtils.setField(scheduler, "initialDelayMinutes", 1L);
        ReflectionTestUtils.setField(scheduler, "delayMultiplier", 5L);
        ReflectionTestUtils.setField(scheduler, "maxDelayMinutes", 30L);
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> mock(ScheduledFuture.class));
    }

    @BeforeEach
    void attachLogAppender() {
        schedulerLogger = (Logger) LoggerFactory.getLogger(ApiFootballSyncFailureRetryScheduler.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        schedulerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        schedulerLogger.detachAppender(logAppender);
        logAppender.stop();
        scheduler.shutdown();
    }

    @Test
    void increasesFallbackDelayAndCapsItAtConfiguredMaximum() {
        assertThat(scheduler.retryDelay(0, new RuntimeException())).isEqualTo(Duration.ofMinutes(1));
        assertThat(scheduler.retryDelay(1, new RuntimeException())).isEqualTo(Duration.ofMinutes(5));
        assertThat(scheduler.retryDelay(2, new RuntimeException())).isEqualTo(Duration.ofMinutes(25));
        assertThat(scheduler.retryDelay(3, new RuntimeException())).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void retryAfterOverridesFallbackDelayWithoutSlowRetryCap() {
        ExternalApiException failure = retryableFailure(Duration.ofHours(2));

        assertThat(scheduler.retryDelay(1, failure)).isEqualTo(Duration.ofHours(2));
        assertThat(scheduler.retryDelay(1, new RuntimeException(failure))).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void retryFailureUsesTheLatestRetryAfterForTheNextSchedule() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        ExternalApiException latestFailure = retryableFailure(Duration.ofMinutes(7));

        scheduler.schedule("fixtures:daily:39:2025", "fixtures:league=39; season=2025",
                "fixture sync", new RuntimeException(), () -> {
                    throw latestFailure;
                });
        verify(executor).schedule(taskCaptor.capture(), delayCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        verify(executor, times(2)).schedule(any(Runnable.class), delayCaptor.capture(), eq(TimeUnit.MILLISECONDS));
        assertThat(delayCaptor.getAllValues()).contains(Duration.ofMinutes(7).toMillis());
    }

    @Test
    void latestBatchSupersedesAnOlderBatchWithTheSameExecutionKey() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        Runnable oldAction = mock(Runnable.class);
        Runnable latestAction = mock(Runnable.class);

        scheduler.schedule("teams:39:2025", "teams:league=39; season=2025",
                "old team sync", new RuntimeException(), oldAction);
        scheduler.schedule("teams:39:2025", "teams:league=39; season=2025",
                "latest team sync", new RuntimeException(), latestAction);

        verify(executor, times(2)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getAllValues().get(0).run();
        taskCaptor.getAllValues().get(1).run();

        assertThat(scheduler.pendingRetryCount()).isZero();
        verify(oldAction, never()).run();
        verify(latestAction).run();
        assertThat(eventsWithCode("API_FOOTBALL_SYNC_RETRY_BATCH_SUPERSEDED")).hasSize(1);
        verify(syncStatusService).recordSuccessByKey("teams:2025", "Teams 2025");
    }

    @Test
    void cancellingAnExecutionScopePreventsCapturedStaleTasksFromRunning() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        Runnable firstAction = mock(Runnable.class);
        Runnable secondAction = mock(Runnable.class);
        Runnable unrelatedAction = mock(Runnable.class);

        scheduler.schedule("teams:39:2025", "teams:league=39; season=2025",
                "scheduled teams", new RuntimeException(), firstAction);
        scheduler.schedule("startup:teams:39:2025", "teams:league=39; season=2025",
                "startup teams", new RuntimeException(), secondAction);
        scheduler.schedule("teams:40:2025", "teams:league=40; season=2025",
                "other teams", new RuntimeException(), unrelatedAction);
        verify(executor, times(3)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        assertThat(scheduler.cancelPendingByExecutionKey("teams:league=39; season=2025")).isEqualTo(1);
        taskCaptor.getAllValues().get(0).run();
        taskCaptor.getAllValues().get(1).run();

        assertThat(scheduler.pendingRetryCount()).isEqualTo(1);
        verify(firstAction, never()).run();
        verify(secondAction, never()).run();
        verify(unrelatedAction, never()).run();
        verify(executor, times(3)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));

        ILoggingEvent cancelledEvent = eventWithCode("API_FOOTBALL_SYNC_RETRY_BATCH_CANCELLED");
        assertThat(keyValue(cancelledEvent, "event.outcome")).isEqualTo("cancelled");
        assertThat(keyValue(cancelledEvent, "api_football.execution_key"))
                .isEqualTo("teams:league=39; season=2025");
        assertThat(keyValue(cancelledEvent, "api_football.retry_total_units")).isEqualTo(1);
        assertThat(keyValue(cancelledEvent, "api_football.retry_cancelled_units")).isEqualTo(1);
        assertThat(keyValue(cancelledEvent, "api_football.retry_cancel_reason"))
                .isEqualTo("synchronization_success");
        verify(syncStatusService, never()).recordSuccessByKey(any(), any());
        verify(syncStatusService, never()).recordFailureByKey(any(), any(), any());
    }

    @Test
    void partialRetrySuccessKeepsSiblingsAndMarksSuccessAfterTheLastOne() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        String executionKey = "players:league=39; season=2025";

        scheduler.scheduleBatch(ApiFootballRetryBatchRequest.partialUnits(
                executionKey,
                "player team retry",
                new RuntimeException(),
                List.of(
                        new ApiFootballRetryUnit("registered-players:39:2025:team:1", "team 1", () -> { }),
                        new ApiFootballRetryUnit("registered-players:39:2025:team:2", "team 2", () -> { })
                )
        ));
        verify(executor, times(2)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        taskCaptor.getAllValues().get(0).run();
        assertThat(scheduler.pendingRetryCount()).isEqualTo(1);
        verify(syncStatusService, never()).recordSuccessByKey(any(), any());

        taskCaptor.getAllValues().get(1).run();
        assertThat(scheduler.pendingRetryCount()).isZero();
        verify(syncStatusService).recordSuccessByKey("players:2025", "Players 2025");
    }

    @Test
    void successfulSiblingDoesNotHideATerminalPartialRetryFailure() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        String executionKey = "players:league=39; season=2025";

        scheduler.scheduleBatch(ApiFootballRetryBatchRequest.partialUnits(
                executionKey,
                "player team retry",
                new RuntimeException(),
                List.of(
                        new ApiFootballRetryUnit("registered-players:39:2025:team:1", "team 1", () -> {
                            throw nonRetryableFailure();
                        }),
                        new ApiFootballRetryUnit("registered-players:39:2025:team:2", "team 2", () -> { })
                )
        ));
        verify(executor, times(2)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        taskCaptor.getAllValues().forEach(Runnable::run);

        assertThat(scheduler.pendingRetryCount()).isZero();
        verify(syncStatusService, never()).recordSuccessByKey(any(), any());
        verify(syncStatusService).recordFailureByKey(eq("players:2025"), eq("Players 2025"), any());
    }

    @Test
    void aNewAutomaticRetryBatchSupersedesTerminalFailuresFromThePreviousRun() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        String executionKey = "players:league=39; season=2025";

        scheduler.schedule("registered-players:39:2025:team:1", executionKey,
                "previous team failure", new RuntimeException(), () -> {
                    throw nonRetryableFailure();
                });
        verify(executor).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        scheduler.schedule("registered-players:39:2025:team:2", executionKey,
                "current team failure", new RuntimeException(), () -> { });
        verify(executor, times(2)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        verify(syncStatusService).recordSuccessByKey("players:2025", "Players 2025");
    }

    @Test
    void activeSynchronizationDefersWithoutRunningTheRetryAction() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        String executionKey = "fixtures:league=39; season=2025";
        Runnable action = mock(Runnable.class);
        ApiFootballSyncExecutionGuard.Lease lease = executionGuard.acquire(executionKey);

        scheduler.schedule("fixtures:daily:39:2025", executionKey,
                "fixture sync", new RuntimeException(), action);
        verify(executor).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        assertThat(scheduler.pendingRetryCount()).isEqualTo(1);
        verify(action, never()).run();
        verify(executor, times(2)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(syncStatusService, times(1)).recordRetryPendingByKey(any(), any(), any());
        executionGuard.release(lease);
    }

    @Test
    void logsStructuredEventWhenRetryStopsForANonRetryableFailure() {
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        scheduler.schedule("registered-players:39:2025:team:1", "players:league=39; season=2025",
                "player team retry", new RuntimeException(), () -> {
                    throw nonRetryableFailure();
                });
        verify(executor).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        ILoggingEvent event = eventWithCode("API_FOOTBALL_SYNC_RETRY_NON_RETRYABLE");
        assertThat(keyValue(event, "event.action")).isEqualTo("api-football-sync-retry");
        assertThat(keyValue(event, "event.outcome")).isEqualTo("failure");
        assertThat(keyValue(event, "external_api.provider")).isEqualTo("API_FOOTBALL");
        assertThat(keyValue(event, "external_api.error_category")).isEqualTo("BAD_REQUEST");
        assertThat(keyValue(event, "external_api.retryable")).isEqualTo(false);
        assertThat(keyValue(event, "api_football.retry_key"))
                .isEqualTo("registered-players:39:2025:team:1");
        assertThat(keyValue(event, "api_football.retry_attempt")).isEqualTo(1);
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    void logsStructuredEventWhenRetryAttemptsAreExhausted() {
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 1);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        scheduler.schedule("fixtures:daily:39:2025", "fixtures:league=39; season=2025",
                "fixture retry", new RuntimeException(), () -> {
                    throw retryableFailure(null);
                });
        verify(executor).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));
        taskCaptor.getValue().run();

        ILoggingEvent event = eventWithCode("API_FOOTBALL_SYNC_RETRY_EXHAUSTED");
        assertThat(keyValue(event, "event.action")).isEqualTo("api-football-sync-retry");
        assertThat(keyValue(event, "event.outcome")).isEqualTo("failure");
        assertThat(keyValue(event, "external_api.provider")).isEqualTo("API_FOOTBALL");
        assertThat(keyValue(event, "external_api.error_category")).isEqualTo("RATE_LIMITED");
        assertThat(keyValue(event, "external_api.retryable")).isEqualTo(true);
        assertThat(keyValue(event, "api_football.retry_attempt")).isEqualTo(1);
        assertThat(keyValue(event, "api_football.retry_max_attempts")).isEqualTo(1);
        assertThat(event.getThrowableProxy()).isNotNull();
    }

    @Test
    void emitsOnePartialFailureEventAfterEveryUnitInTheBatchFinishes() {
        ReflectionTestUtils.setField(scheduler, "maxAttempts", 1);
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);

        scheduler.scheduleBatch(ApiFootballRetryBatchRequest.partialUnits(
                "players:league=39; season=2025",
                "registered player retry batch",
                new RuntimeException("initial partial failure"),
                List.of(
                        new ApiFootballRetryUnit(
                                "registered-players:39:2025:team:1",
                                "team 1",
                                () -> { }
                        ),
                        new ApiFootballRetryUnit(
                                "registered-players:39:2025:team:2",
                                "team 2",
                                () -> {
                                    throw retryableFailure(null);
                                }
                        )
                )
        ));
        verify(executor, times(2)).schedule(taskCaptor.capture(), anyLong(), eq(TimeUnit.MILLISECONDS));

        taskCaptor.getAllValues().get(0).run();
        assertThat(eventsWithCode("API_FOOTBALL_SYNC_RETRY_BATCH_COMPLETED")).isEmpty();

        taskCaptor.getAllValues().get(1).run();
        List<ILoggingEvent> completedEvents = eventsWithCode("API_FOOTBALL_SYNC_RETRY_BATCH_COMPLETED");

        assertThat(completedEvents).hasSize(1);
        ILoggingEvent event = completedEvents.get(0);
        assertThat(keyValue(event, "event.outcome")).isEqualTo("partial_failure");
        assertThat(keyValue(event, "api_football.retry_scope")).isEqualTo("PARTIAL_UNITS");
        assertThat(keyValue(event, "api_football.retry_total_units")).isEqualTo(2);
        assertThat(keyValue(event, "api_football.retry_succeeded_units")).isEqualTo(1);
        assertThat(keyValue(event, "api_football.retry_failed_units")).isEqualTo(1);
        assertThat(keyValue(event, "api_football.failed_retry_keys"))
                .isEqualTo("registered-players:39:2025:team:2");
        assertThat(keyValue(event, "api_football.retry_batch_id")).isNotNull();
        verify(syncStatusService).recordFailureByKey(eq("players:2025"), eq("Players 2025"), any());
    }

    @Test
    void completesANonRetryableInitialFailureWithoutSchedulingAUnit() {
        scheduler.schedule(
                "injuries:39:2025",
                "injuries:league=39; season=2025",
                "injury retry",
                nonRetryableFailure(),
                () -> { }
        );

        verify(executor, never()).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        ILoggingEvent unitEvent = eventWithCode("API_FOOTBALL_SYNC_RETRY_NON_RETRYABLE");
        ILoggingEvent batchEvent = eventWithCode("API_FOOTBALL_SYNC_RETRY_BATCH_COMPLETED");
        assertThat(keyValue(unitEvent, "api_football.retry_attempt")).isEqualTo(0);
        assertThat(keyValue(batchEvent, "event.outcome")).isEqualTo("failure");
        assertThat(keyValue(batchEvent, "api_football.retry_total_units")).isEqualTo(1);
        assertThat(keyValue(batchEvent, "api_football.retry_failed_units")).isEqualTo(1);
    }

    private ILoggingEvent eventWithCode(String eventCode) {
        return eventsWithCode(eventCode).stream()
                .findFirst()
                .orElseThrow();
    }

    private List<ILoggingEvent> eventsWithCode(String eventCode) {
        return logAppender.list.stream()
                .filter(event -> eventCode.equals(keyValue(event, "event.code")))
                .toList();
    }

    private Object keyValue(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() == null) {
            return null;
        }
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .map(pair -> pair.value)
                .findFirst()
                .orElse(null);
    }

    private ExternalApiException retryableFailure(Duration retryAfter) {
        return new ExternalApiException(
                ExternalApiProvider.API_FOOTBALL,
                "fixtures",
                ExternalApiErrorCategory.RATE_LIMITED,
                429,
                true,
                retryAfter,
                "rate limited",
                null
        );
    }

    private ExternalApiException nonRetryableFailure() {
        return new ExternalApiException(
                ExternalApiProvider.API_FOOTBALL,
                "players",
                ExternalApiErrorCategory.BAD_REQUEST,
                400,
                false,
                null,
                "bad request",
                null
        );
    }
}

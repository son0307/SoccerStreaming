package com.son.soccerStreaming.news.scheduler;

import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.news.service.NewsCollectionService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@ConditionalOnProperty(name = "news.sync.enabled", havingValue = "true")
public class NewsCollectionFailureRetryScheduler {

    private final NewsCollectionService collectionService;
    private final ScheduledExecutorService retryExecutor;
    private final Map<Long, RetryState> retryStates = new ConcurrentHashMap<>();

    @Value("${news.sync.failure-retry.enabled:true}")
    private boolean enabled;

    @Value("${news.sync.failure-retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${news.sync.failure-retry.initial-delay:5m}")
    private Duration initialDelay;

    @Value("${news.sync.failure-retry.delay-multiplier:3}")
    private long delayMultiplier;

    @Value("${news.sync.failure-retry.max-delay:30m}")
    private Duration maxDelay;

    @Autowired
    public NewsCollectionFailureRetryScheduler(NewsCollectionService collectionService) {
        this(collectionService, newRetryExecutor());
    }

    NewsCollectionFailureRetryScheduler(
            NewsCollectionService collectionService,
            ScheduledExecutorService retryExecutor
    ) {
        this.collectionService = collectionService;
        this.retryExecutor = retryExecutor;
    }

    public void replacePendingRetries(List<NewsCollectionService.FailedTeam> failures) {
        List<NewsCollectionService.FailedTeam> safeFailures = failures == null ? List.of() : failures;
        Set<Long> retryableTeamIds = new HashSet<>();
        for (NewsCollectionService.FailedTeam failure : safeFailures) {
            if (failure.retryable()) {
                retryableTeamIds.add(failure.teamId());
            }
        }

        retryStates.entrySet().removeIf(entry -> {
            if (retryableTeamIds.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().cancel();
            return true;
        });

        for (NewsCollectionService.FailedTeam failure : safeFailures) {
            if (failure.retryable()) {
                scheduleIfAbsent(failure);
            } else {
                log.atError()
                        .addKeyValue("event.action", "news-collection-retry")
                        .addKeyValue("event.outcome", "not_scheduled")
                        .addKeyValue("event.code", "NEWS_COLLECTION_NON_RETRYABLE_FAILURE")
                        .addKeyValue("external_api.provider", "SERP_API")
                        .addKeyValue("external_api.error_category", failure.category().name())
                        .addKeyValue("team.id", failure.teamId())
                        .log("Team news collection retry was not scheduled for a non-retryable failure.");
            }
        }
    }

    private void scheduleIfAbsent(NewsCollectionService.FailedTeam failure) {
        if (!enabled) {
            log.warn("Team news collection retry is disabled. teamId={}, category={}",
                    failure.teamId(), failure.category());
            return;
        }
        int configuredMaxAttempts = Math.max(0, maxAttempts);
        if (configuredMaxAttempts == 0) {
            log.error("Team news collection retry exhausted before scheduling. teamId={}, maxAttempts=0",
                    failure.teamId());
            return;
        }

        RetryState state = new RetryState(failure.teamId(), failure.teamName(), configuredMaxAttempts);
        if (retryStates.putIfAbsent(failure.teamId(), state) == null) {
            scheduleNext(state, preferredDelay(initialDelay, failure.retryAfter()));
        }
    }

    private void scheduleNext(RetryState state, Duration delay) {
        if (!isCurrent(state)) {
            return;
        }
        long delayMillis = safeDelayMillis(delay);
        ScheduledFuture<?> future = retryExecutor.schedule(
                () -> retry(state),
                delayMillis,
                TimeUnit.MILLISECONDS
        );
        state.replaceFuture(future);
        log.atWarn()
                .addKeyValue("event.action", "news-collection-retry")
                .addKeyValue("event.outcome", "scheduled")
                .addKeyValue("team.id", state.teamId())
                .addKeyValue("news.retry_attempt", state.nextAttempt())
                .addKeyValue("news.retry_max_attempts", state.maxAttempts())
                .addKeyValue("news.retry_delay_ms", delayMillis)
                .log("Team news collection retry scheduled.");
    }

    private void retry(RetryState state) {
        if (!isCurrent(state)) {
            return;
        }
        int attempt = state.incrementAttempt();
        try {
            int savedArticles = collectionService.collectTeam(state.teamId());
            if (retryStates.remove(state.teamId(), state)) {
                state.cancel();
            }
            log.atInfo()
                    .addKeyValue("event.action", "news-collection-retry")
                    .addKeyValue("event.outcome", "success")
                    .addKeyValue("team.id", state.teamId())
                    .addKeyValue("news.retry_attempt", attempt)
                    .addKeyValue("news.saved_articles", savedArticles)
                    .log("Team news collection retry succeeded.");
        } catch (Exception exception) {
            if (!isCurrent(state)) {
                return;
            }
            ExternalApiException externalFailure = externalApiException(exception);
            boolean retryable = externalFailure != null && externalFailure.isRetryable();
            if (!retryable || attempt >= state.maxAttempts()) {
                if (retryStates.remove(state.teamId(), state)) {
                    state.cancel();
                }
                log.atError()
                        .addKeyValue("event.action", "news-collection-retry")
                        .addKeyValue("event.outcome", "failure")
                        .addKeyValue("event.code", retryable
                                ? "NEWS_COLLECTION_RETRY_EXHAUSTED"
                                : "NEWS_COLLECTION_RETRY_NON_RETRYABLE")
                        .addKeyValue("team.id", state.teamId())
                        .addKeyValue("news.retry_attempt", attempt)
                        .addKeyValue("news.retry_max_attempts", state.maxAttempts())
                        .addKeyValue("external_api.error_category",
                                externalFailure != null ? externalFailure.getCategory().name() : "UNKNOWN")
                        .log("Team news collection retry stopped.");
                return;
            }
            scheduleNext(state, preferredDelay(retryDelay(attempt),
                    externalFailure != null ? externalFailure.getRetryAfter() : null));
        }
    }

    private Duration preferredDelay(Duration configuredDelay, Duration retryAfter) {
        Duration safeConfigured = configuredDelay == null || configuredDelay.isNegative()
                ? Duration.ZERO
                : configuredDelay;
        if (retryAfter == null || retryAfter.isNegative()) {
            return safeConfigured;
        }
        return retryAfter.compareTo(safeConfigured) > 0 ? retryAfter : safeConfigured;
    }

    private Duration retryDelay(int completedAttempts) {
        long multiplier = Math.max(1, delayMultiplier);
        long delayMillis = Math.max(0, initialDelay.toMillis());
        long maximumMillis = Math.max(delayMillis, maxDelay.toMillis());
        for (int index = 0; index < completedAttempts && delayMillis < maximumMillis; index++) {
            if (delayMillis > maximumMillis / multiplier) {
                delayMillis = maximumMillis;
            } else {
                delayMillis = Math.min(maximumMillis, delayMillis * multiplier);
            }
        }
        return Duration.ofMillis(delayMillis);
    }

    private long safeDelayMillis(Duration delay) {
        if (delay == null || delay.isNegative()) {
            return 0;
        }
        try {
            return delay.toMillis();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private ExternalApiException externalApiException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ExternalApiException externalApiException) {
                return externalApiException;
            }
            current = current.getCause();
        }
        return null;
    }

    private boolean isCurrent(RetryState state) {
        return !state.cancelled() && retryStates.get(state.teamId()) == state;
    }

    int pendingRetryCount() {
        return retryStates.size();
    }

    @PreDestroy
    public void shutdown() {
        retryStates.values().forEach(RetryState::cancel);
        retryStates.clear();
        retryExecutor.shutdownNow();
    }

    private static ScheduledExecutorService newRetryExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "news-collection-failure-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static final class RetryState {
        private final Long teamId;
        private final String teamName;
        private final int maxAttempts;
        private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private int attempt;

        private RetryState(Long teamId, String teamName, int maxAttempts) {
            this.teamId = teamId;
            this.teamName = teamName;
            this.maxAttempts = maxAttempts;
        }

        private synchronized int incrementAttempt() {
            return ++attempt;
        }

        private synchronized int nextAttempt() {
            return attempt + 1;
        }

        private Long teamId() {
            return teamId;
        }

        @SuppressWarnings("unused")
        private String teamName() {
            return teamName;
        }

        private int maxAttempts() {
            return maxAttempts;
        }

        private void replaceFuture(ScheduledFuture<?> next) {
            ScheduledFuture<?> previous = future.getAndSet(next);
            if (previous != null && !previous.isDone()) {
                previous.cancel(false);
            }
            if (cancelled.get()) {
                next.cancel(false);
            }
        }

        private boolean cancelled() {
            return cancelled.get();
        }

        private void cancel() {
            cancelled.set(true);
            ScheduledFuture<?> scheduled = future.getAndSet(null);
            if (scheduled != null && !scheduled.isDone()) {
                scheduled.cancel(false);
            }
        }
    }
}

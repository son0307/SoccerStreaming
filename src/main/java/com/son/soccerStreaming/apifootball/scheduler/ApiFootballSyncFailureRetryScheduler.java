package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballInjuryReferenceSyncException;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;

/**
 * API-Football 동기화 실패를 UUID 기반 Batch로 묶고, 개별 Unit 재시도와 최종 집계 결과를 관리한다.
 *
 * <p>{@code executionKey}는 같은 종류의 동기화에 대한 동시 실행을 막는 논리 키이고,
 * {@code batchId}는 실제로 발생한 한 번의 실패 및 재시도 묶음을 식별한다.</p>
 */
@Slf4j
@Component
public class ApiFootballSyncFailureRetryScheduler {

    private static final String RETRY_EVENT_ACTION = "api-football-sync-retry";
    private static final String RETRY_EXHAUSTED_EVENT_CODE = "API_FOOTBALL_SYNC_RETRY_EXHAUSTED";
    private static final String RETRY_NON_RETRYABLE_EVENT_CODE = "API_FOOTBALL_SYNC_RETRY_NON_RETRYABLE";
    private static final String RETRY_BATCH_EVENT_ACTION = "api-football-sync-retry-batch";
    private static final String RETRY_BATCH_COMPLETED_EVENT_CODE = "API_FOOTBALL_SYNC_RETRY_BATCH_COMPLETED";
    private static final String RETRY_BATCH_CANCELLED_EVENT_CODE = "API_FOOTBALL_SYNC_RETRY_BATCH_CANCELLED";
    private static final String RETRY_BATCH_SUPERSEDED_EVENT_CODE = "API_FOOTBALL_SYNC_RETRY_BATCH_SUPERSEDED";
    private static final int MAX_FAILED_KEYS_IN_LOG = 20;

    private final ApiFootballSyncStatusService syncStatusService;
    private final ApiFootballSyncExecutionGuard executionGuard;
    private final ScheduledExecutorService retryExecutor;
    private final Map<String, RetryState> retryStates = new ConcurrentHashMap<>();
    private final Map<UUID, RetryBatchState> retryBatches = new ConcurrentHashMap<>();
    private final Map<String, RetryBatchState> activeBatchesByExecutionKey = new ConcurrentHashMap<>();

    @Value("${api-football.sync.failure-retry.enabled:true}")
    private boolean enabled;

    @Value("${api-football.sync.failure-retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${api-football.sync.failure-retry.initial-delay-minutes:1}")
    private long initialDelayMinutes;

    @Value("${api-football.sync.failure-retry.delay-multiplier:5}")
    private long delayMultiplier;

    @Value("${api-football.sync.failure-retry.max-delay-minutes:30}")
    private long maxDelayMinutes;

    /**
     * 운영 환경에서 사용할 단일 스레드 재시도 실행기를 생성한다.
     */
    @Autowired
    public ApiFootballSyncFailureRetryScheduler(ApiFootballSyncStatusService syncStatusService,
                                                ApiFootballSyncExecutionGuard executionGuard) {
        this(syncStatusService, executionGuard, newRetryExecutor());
    }

    /**
     * 테스트에서 제어 가능한 실행기를 주입할 수 있도록 제공하는 생성자다.
     */
    ApiFootballSyncFailureRetryScheduler(ApiFootballSyncStatusService syncStatusService,
                                         ApiFootballSyncExecutionGuard executionGuard,
                                         ScheduledExecutorService retryExecutor) {
        this.syncStatusService = syncStatusService;
        this.executionGuard = executionGuard;
        this.retryExecutor = retryExecutor;
    }

    /**
     * API-Football 재시도 전용 데몬 스레드를 만든다.
     */
    private static ScheduledExecutorService newRetryExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "api-football-sync-failure-retry");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 기존 전체 작업 호출부를 단일 Unit UUID Batch로 변환한다.
     */
    public void schedule(String retryKey, String executionKey, String description,
                         Exception failure, Runnable retryAction) {
        scheduleBatch(ApiFootballRetryBatchRequest.wholeTask(
                executionKey,
                description,
                failure,
                new ApiFootballRetryUnit(retryKey, description, retryAction)
        ));
    }

    /**
     * 한 번의 동기화에서 실패한 전체 작업 또는 팀·Chunk 목록을 하나의 UUID Batch로 등록한다.
     */
    public synchronized void scheduleBatch(ApiFootballRetryBatchRequest request) {
        UUID batchId = UUID.randomUUID();
        int configuredMaxAttempts = Math.max(0, maxAttempts);
        RetryBatchState batch = new RetryBatchState(batchId, request, configuredMaxAttempts);
        retryBatches.put(batchId, batch);

        RetryBatchState previousBatch = activeBatchesByExecutionKey.put(request.executionKey(), batch);
        if (previousBatch != null && previousBatch != batch) {
            supersedeBatch(previousBatch, batch);
        }

        if (!enabled) {
            batch.failAll(request.initialFailure());
            completeBatch(batch);
            return;
        }
        if (!shouldRetry(request.initialFailure())) {
            batch.failAll(request.initialFailure());
            logInitialTerminalFailure(RETRY_NON_RETRYABLE_EVENT_CODE, batch, request.initialFailure(),
                    "API-Football sync retry batch was not scheduled for a non-retryable error.");
            completeBatch(batch);
            return;
        }
        if (configuredMaxAttempts == 0) {
            batch.failAll(request.initialFailure());
            logInitialTerminalFailure(RETRY_EXHAUSTED_EVENT_CODE, batch, request.initialFailure(),
                    "API-Football sync retry batch has no configured attempts.");
            completeBatch(batch);
            return;
        }

        List<Map.Entry<String, RetryState>> scheduledRetryStates = new ArrayList<>();
        for (RetryUnitState unit : batch.units()) {
            RetryState state = new RetryState(batchId, request.executionKey(), unit,
                    configuredMaxAttempts);
            retryStates.put(unit.retryKey(), state);
            scheduledRetryStates.add(Map.entry(unit.retryKey(), state));
        }

        int scheduledUnits = scheduledRetryStates.size();
        // 상태를 RETRY_PENDING으로 먼저 저장해야 지연 시간이 0이어도 성공/실패 결과를 대기 상태가 덮어쓰지 않는다.
        syncStatusOfRetryKey(batch.firstRetryKey()).ifPresent(status ->
                syncStatusService.recordRetryPendingByKey(status.syncKey(), status.displayName(),
                        "Retry batch %s scheduled. units=%d, description=%s"
                                .formatted(batchId, scheduledUnits, request.description())));
        scheduledRetryStates.forEach(entry ->
                scheduleNext(entry.getKey(), entry.getValue(), request.initialFailure(), false));
        completeBatchIfFinished(batch);
    }

    /**
     * 예외 체인에서 외부 API 예외를 찾아 지연 재시도가 가능한지 판단한다.
     */
    public boolean shouldRetry(Exception exception) {
        return externalApiException(exception)
                .map(ExternalApiException::isRetryable)
                .orElse(true);
    }

    /**
     * 새로운 정규 동기화가 성공하면 같은 실행 범위의 대기 Batch와 과거 실패 표시를 제거한다.
     */
    public synchronized int cancelPendingByExecutionKey(String executionKey) {
        int cancelledCount = 0;
        for (RetryBatchState batch : new ArrayList<>(retryBatches.values())) {
            if (!batch.executionKey().equals(executionKey)
                    || !retryBatches.remove(batch.batchId(), batch)) {
                continue;
            }
            if (!batch.markCompleted()) {
                continue;
            }
            batch.cancel();
            for (RetryUnitState unit : batch.units()) {
                RetryState state = retryStates.get(unit.retryKey());
                if (state != null && state.batchId().equals(batch.batchId())
                        && retryStates.remove(unit.retryKey(), state)) {
                    state.cancel();
                    cancelledCount++;
                }
            }
            logBatchCancelledAfterSyncSuccess(batch);
        }
        activeBatchesByExecutionKey.remove(executionKey);
        return cancelledCount;
    }

    /**
     * 애플리케이션 종료 시 예약된 Unit을 취소하고 메모리 상태를 정리한다.
     */
    @PreDestroy
    public void shutdown() {
        retryStates.values().forEach(RetryState::cancel);
        retryStates.clear();
        retryBatches.values().forEach(RetryBatchState::cancel);
        retryBatches.clear();
        activeBatchesByExecutionKey.clear();
        retryExecutor.shutdownNow();
    }

    /**
     * Unit의 다음 실행 시각을 계산하고 예약한다.
     */
    private void scheduleNext(String retryKey, RetryState state, Exception failure, boolean activeJobDeferred) {
        if (!isCurrent(retryKey, state)) {
            return;
        }

        Duration delay = activeJobDeferred
                ? Duration.ofMinutes(Math.max(0, initialDelayMinutes))
                : retryDelay(state.attempt(), failure);
        long delayMillis = safeMillis(delay);
        int nextAttempt = state.attempt() + 1;
        Instant nextAttemptAt = Instant.now().plusMillis(delayMillis);
        boolean retryAfterApplied = !activeJobDeferred && retryAfter(failure).isPresent();

        log.atWarn()
                .addKeyValue("event.action", RETRY_EVENT_ACTION)
                .addKeyValue("event.outcome", "scheduled")
                .addKeyValue("api_football.retry_batch_id", state.batchId())
                .addKeyValue("api_football.retry_key", retryKey)
                .addKeyValue("api_football.execution_key", state.executionKey())
                .addKeyValue("api_football.retry_attempt", nextAttempt)
                .addKeyValue("api_football.retry_max_attempts", state.maxAttempts())
                .addKeyValue("api_football.retry_delay_ms", delayMillis)
                .addKeyValue("api_football.retry_at", nextAttemptAt)
                .addKeyValue("api_football.retry_after_applied", retryAfterApplied)
                .log("API-Football sync failure retry scheduled.");

        ScheduledFuture<?> future = retryExecutor.schedule(
                () -> runRetry(retryKey, state), delayMillis, TimeUnit.MILLISECONDS);
        state.replaceScheduledFuture(future);
        if (!isCurrent(retryKey, state)) {
            state.cancel();
        }
    }

    /**
     * 같은 동기화가 실행 중이면 Unit을 연기하고, 비어 있으면 실제 재시도를 시작한다.
     */
    private void runRetry(String retryKey, RetryState state) {
        if (!isCurrent(retryKey, state)) {
            return;
        }

        if (!executionGuard.executeIfAvailable(state.executionKey(), () -> runRetryNow(retryKey, state))) {
            if (!isCurrent(retryKey, state)) {
                return;
            }
            log.info("API-Football retry deferred because the same synchronization is active. retryKey={}, batchId={}, executionKey={}",
                    retryKey, state.batchId(), state.executionKey());
            scheduleNext(retryKey, state, null, true);
        }
    }

    /**
     * Unit 작업을 실행하고 성공, 비재시도 실패, 재시도 소진 또는 다음 예약으로 상태를 전환한다.
     */
    private void runRetryNow(String retryKey, RetryState state) {
        if (!isCurrent(retryKey, state)) {
            return;
        }

        int attempt = state.incrementAttempt();
        try {
            log.info("API-Football sync failure retry started. retryKey={}, batchId={}, executionKey={}, description={}, attempt={}/{}",
                    retryKey, state.batchId(), state.executionKey(), state.description(), attempt, state.maxAttempts());
            state.retryAction().run();
            finishUnit(retryKey, state, true, null);
            log.info("API-Football sync failure retry succeeded. retryKey={}, batchId={}, executionKey={}, attempt={}/{}",
                    retryKey, state.batchId(), state.executionKey(), attempt, state.maxAttempts());
        } catch (Exception exception) {
            if (!isCurrent(retryKey, state)) {
                return;
            }
            if (!shouldRetry(exception)) {
                finishUnit(retryKey, state, false, exception);
                logTerminalFailure(RETRY_NON_RETRYABLE_EVENT_CODE, retryKey, state, attempt, exception,
                        "API-Football sync failure retry stopped for a non-retryable error.");
                return;
            }
            if (attempt >= state.maxAttempts()) {
                finishUnit(retryKey, state, false, exception);
                logTerminalFailure(RETRY_EXHAUSTED_EVENT_CODE, retryKey, state, attempt, exception,
                        "API-Football sync failure retry exhausted.");
                return;
            }

            log.error("API-Football sync failure retry failed. retryKey={}, batchId={}, executionKey={}, attempt={}/{}",
                    retryKey, state.batchId(), state.executionKey(), attempt, state.maxAttempts(), exception);
            scheduleNext(retryKey, state, exception, false);
        }
    }

    /**
     * Unit을 터미널 상태로 전환하고 Batch의 모든 Unit이 끝났는지 확인한다.
     */
    private void finishUnit(String retryKey, RetryState state, boolean success, Exception failure) {
        if (retryStates.remove(retryKey, state)) {
            state.cancel();
        }
        RetryBatchState batch = retryBatches.get(state.batchId());
        if (batch == null) {
            return;
        }
        if (success) {
            batch.succeedUnit(retryKey);
        } else {
            batch.failUnit(retryKey, failure);
        }
        completeBatchIfFinished(batch);
    }

    /**
     * Batch의 모든 Unit이 터미널 상태가 된 경우에만 최종 집계를 수행한다.
     */
    private void completeBatchIfFinished(RetryBatchState batch) {
        if (batch.isFinished()) {
            completeBatch(batch);
        }
    }

    /**
     * Batch 결과를 상태 저장소와 구조화 로그에 정확히 한 번 반영한다.
     */
    private synchronized void completeBatch(RetryBatchState batch) {
        if (!batch.markCompleted()) {
            return;
        }
        retryBatches.remove(batch.batchId(), batch);

        if (!activeBatchesByExecutionKey.remove(batch.executionKey(), batch)) {
            log.debug("Ignore stale API-Football retry batch completion. batchId={}, executionKey={}",
                    batch.batchId(), batch.executionKey());
            return;
        }

        Optional<SyncStatusTarget> target = syncStatusOfRetryKey(batch.firstRetryKey());
        Exception failure = batch.firstFailure().orElse(batch.initialFailure());
        if (batch.failedUnits() > 0) {
            target.ifPresent(status ->
                    syncStatusService.recordFailureByKey(status.syncKey(), status.displayName(), failure));
        } else {
            target.ifPresent(status ->
                    syncStatusService.recordSuccessByKey(status.syncKey(), status.displayName()));
        }

        logBatchCompleted(batch);
    }

    /**
     * 같은 실행 키로 등록된 이전 Batch를 취소하고 최신 Batch만 유효하게 유지한다.
     */
    private void supersedeBatch(RetryBatchState previousBatch, RetryBatchState replacementBatch) {
        if (!previousBatch.markCompleted()) {
            return;
        }
        retryBatches.remove(previousBatch.batchId(), previousBatch);
        previousBatch.cancel();
        for (RetryUnitState unit : previousBatch.units()) {
            RetryState state = retryStates.get(unit.retryKey());
            if (state != null && state.batchId().equals(previousBatch.batchId())
                    && retryStates.remove(unit.retryKey(), state)) {
                state.cancel();
            }
        }
        log.atInfo()
                .addKeyValue("event.action", RETRY_BATCH_EVENT_ACTION)
                .addKeyValue("event.outcome", "cancelled")
                .addKeyValue("event.code", RETRY_BATCH_SUPERSEDED_EVENT_CODE)
                .addKeyValue("external_api.provider", "API_FOOTBALL")
                .addKeyValue("api_football.retry_batch_id", previousBatch.batchId())
                .addKeyValue("api_football.replacement_retry_batch_id", replacementBatch.batchId())
                .addKeyValue("api_football.execution_key", previousBatch.executionKey())
                .addKeyValue("api_football.retry_total_units", previousBatch.totalUnits())
                .log("API-Football retry batch was superseded by the latest batch.");
    }

    /**
     * 정상 동기화 성공으로 재시도할 필요가 없어진 Batch의 최종 취소 집계를 구조화 로그로 남긴다.
     */
    private void logBatchCancelledAfterSyncSuccess(RetryBatchState batch) {
        var batchLog = log.atInfo()
                .addKeyValue("event.action", RETRY_BATCH_EVENT_ACTION)
                .addKeyValue("event.outcome", "cancelled")
                .addKeyValue("event.code", RETRY_BATCH_CANCELLED_EVENT_CODE)
                .addKeyValue("external_api.provider", "API_FOOTBALL")
                .addKeyValue("api_football.retry_batch_id", batch.batchId())
                .addKeyValue("api_football.execution_key", batch.executionKey())
                .addKeyValue("api_football.retry_scope", batch.scope().name())
                .addKeyValue("api_football.retry_total_units", batch.totalUnits())
                .addKeyValue("api_football.retry_succeeded_units", batch.succeededUnits())
                .addKeyValue("api_football.retry_failed_units", batch.failedUnits())
                .addKeyValue("api_football.retry_cancelled_units", batch.cancelledUnits())
                .addKeyValue("api_football.retry_duration_ms", batch.durationMillis())
                .addKeyValue("api_football.retry_cancel_reason", "synchronization_success");
        syncStatusOfRetryKey(batch.firstRetryKey()).ifPresent(status -> batchLog
                .addKeyValue("api_football.sync_key", status.syncKey())
                .addKeyValue("api_football.sync_task", status.syncKey().split(":")[0]));
        batchLog.log("API-Football retry batch cancelled after synchronization success. description={}",
                batch.description());
    }

    /**
     * Unit 최종 실패를 Kibana 진단용 구조화 이벤트로 기록한다.
     */
    private void logTerminalFailure(String eventCode, String retryKey, RetryState state, int attempt,
                                    Exception exception, String message) {
        var failureLog = log.atError()
                .addKeyValue("event.action", RETRY_EVENT_ACTION)
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.code", eventCode)
                .addKeyValue("external_api.provider", "API_FOOTBALL")
                .addKeyValue("api_football.retry_batch_id", state.batchId())
                .addKeyValue("api_football.retry_key", retryKey)
                .addKeyValue("api_football.execution_key", state.executionKey())
                .addKeyValue("api_football.retry_attempt", attempt)
                .addKeyValue("api_football.retry_max_attempts", state.maxAttempts());
        addExternalFailureFields(failureLog, exception);
        failureLog
                .setCause(exception)
                .log("{} description={}", message, state.description());
    }

    /**
     * 최초 오류가 비재시도이거나 시도 횟수가 0일 때 대표 Unit 종료 이벤트를 기록한다.
     */
    private void logInitialTerminalFailure(String eventCode, RetryBatchState batch,
                                           Exception exception, String message) {
        var failureLog = log.atError()
                .addKeyValue("event.action", RETRY_EVENT_ACTION)
                .addKeyValue("event.outcome", "failure")
                .addKeyValue("event.code", eventCode)
                .addKeyValue("external_api.provider", "API_FOOTBALL")
                .addKeyValue("api_football.retry_batch_id", batch.batchId())
                .addKeyValue("api_football.retry_key", batch.firstRetryKey())
                .addKeyValue("api_football.execution_key", batch.executionKey())
                .addKeyValue("api_football.retry_attempt", 0)
                .addKeyValue("api_football.retry_max_attempts", batch.maxAttempts());
        addExternalFailureFields(failureLog, exception);
        failureLog
                .setCause(exception)
                .log("{} description={}", message, batch.description());
    }

    /**
     * 외부 API 예외가 있으면 Provider 오류 분류를 구조화 로그에 덧붙인다.
     */
    private void addExternalFailureFields(org.slf4j.spi.LoggingEventBuilder failureLog, Exception exception) {
        externalApiException(exception).ifPresent(externalFailure -> {
            failureLog
                    .addKeyValue("external_api.operation", externalFailure.getOperation())
                    .addKeyValue("external_api.error_category", externalFailure.getCategory().name())
                    .addKeyValue("external_api.retryable", externalFailure.isRetryable())
                    .addKeyValue("http.response.status_code", externalFailure.getHttpStatus());
            if (externalFailure instanceof ApiFootballInjuryReferenceSyncException injuryFailure) {
                failureLog
                        .addKeyValue("api_football.invalid_payload_count", injuryFailure.getInvalidPayloadCount())
                        .addKeyValue("api_football.missing_fixture_count", injuryFailure.getMissingFixtureCount())
                        .addKeyValue("api_football.missing_fixture_ids", injuryFailure.getMissingFixtureIds())
                        .addKeyValue("api_football.missing_team_count", injuryFailure.getMissingTeamCount())
                        .addKeyValue("api_football.missing_team_ids", injuryFailure.getMissingTeamIds())
                        .addKeyValue("api_football.missing_player_count", injuryFailure.getMissingPlayerCount())
                        .addKeyValue("api_football.missing_player_ids", injuryFailure.getMissingPlayerIds());
            }
        });
    }

    /**
     * Batch의 성공/부분 실패/전체 실패와 집계 수치를 알림용 단일 이벤트로 기록한다.
     */
    private void logBatchCompleted(RetryBatchState batch) {
        String outcome = batch.failedUnits() == 0
                ? "success"
                : batch.scope() == ApiFootballRetryScope.PARTIAL_UNITS ? "partial_failure" : "failure";
        List<String> failedKeys = batch.failedRetryKeys();
        String visibleFailedKeys = String.join(",", failedKeys.stream()
                .limit(MAX_FAILED_KEYS_IN_LOG)
                .toList());

        var batchLog = (batch.failedUnits() == 0 ? log.atInfo() : log.atError())
                .addKeyValue("event.action", RETRY_BATCH_EVENT_ACTION)
                .addKeyValue("event.outcome", outcome)
                .addKeyValue("event.code", RETRY_BATCH_COMPLETED_EVENT_CODE)
                .addKeyValue("external_api.provider", "API_FOOTBALL")
                .addKeyValue("api_football.retry_batch_id", batch.batchId())
                .addKeyValue("api_football.execution_key", batch.executionKey())
                .addKeyValue("api_football.retry_scope", batch.scope().name())
                .addKeyValue("api_football.retry_total_units", batch.totalUnits())
                .addKeyValue("api_football.retry_succeeded_units", batch.succeededUnits())
                .addKeyValue("api_football.retry_failed_units", batch.failedUnits())
                .addKeyValue("api_football.retry_cancelled_units", batch.cancelledUnits())
                .addKeyValue("api_football.retry_duration_ms", batch.durationMillis())
                .addKeyValue("api_football.failed_retry_keys", visibleFailedKeys)
                .addKeyValue("api_football.failed_retry_keys_omitted",
                        Math.max(0, failedKeys.size() - MAX_FAILED_KEYS_IN_LOG));
        syncStatusOfRetryKey(batch.firstRetryKey()).ifPresent(status -> batchLog
                .addKeyValue("api_football.sync_key", status.syncKey())
                .addKeyValue("api_football.sync_task", status.syncKey().split(":")[0]));
        batch.firstFailure().ifPresent(failure -> addExternalFailureFields(batchLog, failure));
        batchLog.log("API-Football retry batch completed. description={}", batch.description());
    }

    /**
     * 완료된 시도 횟수와 Retry-After를 기준으로 다음 지연 시간을 계산한다.
     */
    Duration retryDelay(int completedAttempts, Exception failure) {
        Optional<Duration> retryAfter = retryAfter(failure);
        if (retryAfter.isPresent()) {
            return retryAfter.get();
        }

        long initial = Math.max(0, initialDelayMinutes);
        long maximum = Math.max(initial, maxDelayMinutes);
        long multiplier = Math.max(1, delayMultiplier);
        long delay = initial;
        for (int index = 0; index < completedAttempts && delay < maximum; index++) {
            if (delay > maximum / multiplier) {
                delay = maximum;
            } else {
                delay = Math.min(maximum, delay * multiplier);
            }
        }
        return Duration.ofMinutes(delay);
    }

    /**
     * 예외 체인에 포함된 Provider의 Retry-After 값을 찾는다.
     */
    private Optional<Duration> retryAfter(Exception exception) {
        return externalApiException(exception)
                .map(ExternalApiException::getRetryAfter)
                .filter(delay -> !delay.isNegative());
    }

    /**
     * 래핑된 예외 체인을 순회하여 외부 API 예외를 반환한다.
     */
    private Optional<ExternalApiException> externalApiException(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof ExternalApiException externalApiException) {
                return Optional.of(externalApiException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    /**
     * 예약 작업이 취소되지 않았고 현재 Map에 등록된 최신 Unit인지 확인한다.
     */
    private boolean isCurrent(String retryKey, RetryState state) {
        return !state.isCancelled() && retryStates.get(retryKey) == state;
    }

    /**
     * 매우 큰 Duration도 스케줄러가 받을 수 있는 안전한 밀리초 값으로 변환한다.
     */
    private long safeMillis(Duration duration) {
        try {
            return Math.max(0, duration.toMillis());
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * 테스트와 상태 확인을 위해 현재 대기 중인 Unit 수를 반환한다.
     */
    int pendingRetryCount() {
        return retryStates.size();
    }

    /**
     * retryKey 규칙을 동기화 상태 저장소의 키와 화면 표시 이름으로 변환한다.
     */
    private Optional<SyncStatusTarget> syncStatusOfRetryKey(String key) {
        String[] parts = key.split(":");
        if (parts.length == 0) {
            return Optional.empty();
        }
        return switch (parts[0]) {
            case "teams" -> seasonTarget("teams", "Teams", parts, 2);
            case "standings" -> seasonTarget("standings", "Standings", parts, parts.length - 1);
            case "fixtures" -> seasonTarget("fixtures", "Fixtures", parts, parts.length - 1);
            case "fixture-details" -> parts.length > 2 && "daily".equals(parts[1])
                    ? seasonTarget("fixture-details", "Season Details", parts, 2)
                    : Optional.empty();
            case "registered-players" -> seasonTarget("players", "Players", parts, 2);
            case "injuries" -> seasonTarget("injuries", "Injuries", parts, 2);
            case "league-seasons" -> parts.length > 1
                    ? Optional.of(new SyncStatusTarget("league-seasons:" + parts[1], "League Seasons"))
                    : Optional.empty();
            case "startup" -> startupTarget(parts);
            default -> Optional.empty();
        };
    }

    /**
     * 애플리케이션 시작 시점 retryKey를 동기화 상태 대상으로 변환한다.
     */
    private Optional<SyncStatusTarget> startupTarget(String[] parts) {
        if (parts.length < 3) {
            return Optional.empty();
        }
        return switch (parts[1]) {
            case "league-seasons" -> Optional.of(new SyncStatusTarget("league-seasons:" + parts[2], "League Seasons"));
            case "teams" -> seasonTarget("teams", "Teams", parts, 3);
            case "standings" -> seasonTarget("standings", "Standings", parts, 3);
            case "fixtures" -> seasonTarget("fixtures", "Fixtures", parts, 3);
            case "fixture-details" -> seasonTarget("fixture-details", "Season Details", parts, 2);
            case "registered-players" -> seasonTarget("players", "Players", parts, 3);
            case "injuries" -> seasonTarget("injuries", "Injuries", parts, 3);
            default -> Optional.empty();
        };
    }

    /**
     * retryKey에 포함된 시즌 숫자를 이용해 동기화 상태 대상을 만든다.
     */
    private Optional<SyncStatusTarget> seasonTarget(String task, String displayName, String[] parts, int seasonIndex) {
        if (seasonIndex < 0 || seasonIndex >= parts.length) {
            return Optional.empty();
        }
        try {
            int season = Integer.parseInt(parts[seasonIndex]);
            return Optional.of(new SyncStatusTarget("%s:%d".formatted(task, season), "%s %d".formatted(displayName, season)));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /**
     * 재시도 결과를 반영할 동기화 상태 키와 표시 이름을 보관한다.
     */
    private record SyncStatusTarget(String syncKey, String displayName) {
    }

    /**
     * 예약 실행기에서 사용하는 개별 Unit의 실행 횟수와 Future를 관리한다.
     */
    private static class RetryState {

        private static final AtomicIntegerFieldUpdater<RetryState> ATTEMPT_UPDATER =
                AtomicIntegerFieldUpdater.newUpdater(RetryState.class, "attempt");

        private final UUID batchId;
        private final String executionKey;
        private final RetryUnitState unit;
        private final int maxAttempts;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicReference<ScheduledFuture<?>> scheduledFuture = new AtomicReference<>();

        @SuppressWarnings("unused")
        private volatile int attempt;

        /**
         * Batch와 Unit을 실제 예약 실행 상태에 연결한다.
         */
        private RetryState(UUID batchId, String executionKey, RetryUnitState unit, int maxAttempts) {
            this.batchId = batchId;
            this.executionKey = executionKey;
            this.unit = unit;
            this.maxAttempts = maxAttempts;
        }

        /**
         * 이 Unit이 속한 고유 Batch ID를 반환한다.
         */
        UUID batchId() {
            return batchId;
        }

        /**
         * 동시 실행 제어에 사용하는 논리 실행 키를 반환한다.
         */
        String executionKey() {
            return executionKey;
        }

        /**
         * 운영 로그에 사용할 Unit 설명을 반환한다.
         */
        String description() {
            return unit.description();
        }

        /**
         * 실제 재시도 작업을 반환한다.
         */
        Runnable retryAction() {
            return unit.retryAction();
        }

        /**
         * 이 Unit에 허용된 최대 지연 재시도 횟수를 반환한다.
         */
        int maxAttempts() {
            return maxAttempts;
        }

        /**
         * 지금까지 실행한 재시도 횟수를 반환한다.
         */
        int attempt() {
            return attempt;
        }

        /**
         * 실제 실행 직전에 재시도 횟수를 원자적으로 증가시킨다.
         */
        int incrementAttempt() {
            return ATTEMPT_UPDATER.incrementAndGet(this);
        }

        /**
         * 정규 동기화 성공 등으로 이 Unit이 취소되었는지 확인한다.
         */
        boolean isCancelled() {
            return cancelled.get();
        }

        /**
         * 새 예약 Future로 교체하고 이전 예약이 남아 있으면 취소한다.
         */
        void replaceScheduledFuture(ScheduledFuture<?> future) {
            ScheduledFuture<?> previous = scheduledFuture.getAndSet(future);
            if (previous != null && !previous.isDone()) {
                previous.cancel(false);
            }
            if (cancelled.get()) {
                future.cancel(false);
            }
        }

        /**
         * 더 이상 실행되지 않도록 현재 예약 Future를 취소한다.
         */
        void cancel() {
            cancelled.set(true);
            ScheduledFuture<?> future = scheduledFuture.getAndSet(null);
            if (future != null) {
                future.cancel(false);
            }
        }
    }

    /**
     * Batch 집계에서 사용하는 개별 Unit의 터미널 상태와 실패 원인을 관리한다.
     */
    private static class RetryUnitState {

        private final ApiFootballRetryUnit unit;
        private volatile RetryUnitStatus status = RetryUnitStatus.PENDING;
        private volatile Exception failure;

        /**
         * 외부 요청 모델을 내부 변경 가능한 상태로 감싼다.
         */
        private RetryUnitState(ApiFootballRetryUnit unit) {
            this.unit = unit;
        }

        /**
         * Unit 식별 키를 반환한다.
         */
        String retryKey() {
            return unit.retryKey();
        }

        /**
         * Unit 설명을 반환한다.
         */
        String description() {
            return unit.description();
        }

        /**
         * Unit 재실행 작업을 반환한다.
         */
        Runnable retryAction() {
            return unit.retryAction();
        }

        /**
         * 성공 상태로 전환한다.
         */
        void succeed() {
            status = RetryUnitStatus.SUCCEEDED;
            failure = null;
        }

        /**
         * 최종 실패 상태와 마지막 예외를 저장한다.
         */
        void fail(Exception exception) {
            status = RetryUnitStatus.FAILED;
            failure = exception;
        }

        /**
         * 다른 Batch가 같은 retryKey를 이미 처리 중인 경우 집계 대상에서 취소한다.
         */
        void cancel() {
            status = RetryUnitStatus.CANCELLED;
        }

        /**
         * 더 이상 재시도가 남지 않은 상태인지 확인한다.
         */
        boolean isTerminal() {
            return status == RetryUnitStatus.SUCCEEDED
                    || status == RetryUnitStatus.FAILED
                    || status == RetryUnitStatus.CANCELLED;
        }

        /**
         * 현재 상태를 반환한다.
         */
        RetryUnitStatus status() {
            return status;
        }

        /**
         * 최종 실패 원인을 반환한다.
         */
        Optional<Exception> failure() {
            return Optional.ofNullable(failure);
        }
    }

    /**
     * 한 번의 동기화 실패에서 생성된 모든 재시도 Unit과 최종 집계 상태를 보관한다.
     */
    private static class RetryBatchState {

        private final UUID batchId;
        private final ApiFootballRetryBatchRequest request;
        private final Map<String, RetryUnitState> units;
        private final int maxAttempts;
        private final Instant startedAt = Instant.now();
        private final AtomicBoolean completed = new AtomicBoolean();

        /**
         * 요청의 모든 Unit을 먼저 등록하여 실행 도중에도 Batch 전체 크기를 확정한다.
         */
        private RetryBatchState(UUID batchId, ApiFootballRetryBatchRequest request, int maxAttempts) {
            this.batchId = batchId;
            this.request = request;
            this.maxAttempts = maxAttempts;
            this.units = new LinkedHashMap<>();
            for (ApiFootballRetryUnit unit : request.units()) {
                RetryUnitState previous = units.putIfAbsent(unit.retryKey(), new RetryUnitState(unit));
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate retryKey in batch: " + unit.retryKey());
                }
            }
        }

        /**
         * Batch UUID를 반환한다.
         */
        UUID batchId() {
            return batchId;
        }

        /**
         * 동시 실행 제어에 사용하는 논리 실행 키를 반환한다.
         */
        String executionKey() {
            return request.executionKey();
        }

        /**
         * 운영 로그에 사용할 Batch 설명을 반환한다.
         */
        String description() {
            return request.description();
        }

        /**
         * 전체 작업인지 일부 단위 보완인지 반환한다.
         */
        ApiFootballRetryScope scope() {
            return request.scope();
        }

        /**
         * Batch 생성 원인이 된 최초 예외를 반환한다.
         */
        Exception initialFailure() {
            return request.initialFailure();
        }

        /**
         * Batch에 등록된 Unit 목록을 반환한다.
         */
        synchronized List<RetryUnitState> units() {
            return List.copyOf(units.values());
        }

        /**
         * 대표 retryKey를 상태 저장소 매핑에 사용한다.
         */
        synchronized String firstRetryKey() {
            return units.keySet().iterator().next();
        }

        /**
         * 지정한 Unit을 성공 상태로 전환한다.
         */
        synchronized void succeedUnit(String retryKey) {
            RetryUnitState unit = units.get(retryKey);
            if (unit != null && !unit.isTerminal()) {
                unit.succeed();
            }
        }

        /**
         * 지정한 Unit을 최종 실패 상태로 전환한다.
         */
        synchronized void failUnit(String retryKey, Exception failure) {
            RetryUnitState unit = units.get(retryKey);
            if (unit != null && !unit.isTerminal()) {
                unit.fail(failure);
            }
        }

        /**
         * 재시도하지 않는 Batch의 모든 Unit을 동일한 최종 실패로 종료한다.
         */
        synchronized void failAll(Exception failure) {
            units.values().stream()
                    .filter(unit -> !unit.isTerminal())
                    .forEach(unit -> unit.fail(failure));
        }

        /**
         * 모든 Unit이 성공, 실패 또는 취소 상태인지 확인한다.
         */
        synchronized boolean isFinished() {
            return units.values().stream().allMatch(RetryUnitState::isTerminal);
        }

        /**
         * Batch 최종 처리를 한 번만 수행하도록 원자적으로 완료 표시한다.
         */
        boolean markCompleted() {
            return completed.compareAndSet(false, true);
        }

        /**
         * Batch 최종 처리가 끝났는지 확인한다.
         */
        boolean isCompleted() {
            return completed.get();
        }

        /**
         * Batch와 아직 미완료인 Unit을 취소한다.
         */
        synchronized void cancel() {
            units.values().stream()
                    .filter(unit -> !unit.isTerminal())
                    .forEach(RetryUnitState::cancel);
        }

        /**
         * Batch 전체 Unit 수를 반환한다.
         */
        synchronized int totalUnits() {
            return units.size();
        }

        /**
         * 성공한 Unit 수를 반환한다.
         */
        synchronized int succeededUnits() {
            return (int) units.values().stream()
                    .filter(unit -> unit.status() == RetryUnitStatus.SUCCEEDED)
                    .count();
        }

        /**
         * 최종 실패한 Unit 수를 반환한다.
         */
        synchronized int failedUnits() {
            return (int) units.values().stream()
                    .filter(unit -> unit.status() == RetryUnitStatus.FAILED)
                    .count();
        }

        /**
         * 중복 등록 또는 정규 동기화 성공으로 취소된 Unit 수를 반환한다.
         */
        synchronized int cancelledUnits() {
            return (int) units.values().stream()
                    .filter(unit -> unit.status() == RetryUnitStatus.CANCELLED)
                    .count();
        }

        /**
         * 최종 실패한 retryKey 목록을 등록 순서대로 반환한다.
         */
        synchronized List<String> failedRetryKeys() {
            return units.entrySet().stream()
                    .filter(entry -> entry.getValue().status() == RetryUnitStatus.FAILED)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        /**
         * 최종 실패 Unit 중 첫 번째 예외를 대표 원인으로 반환한다.
         */
        synchronized Optional<Exception> firstFailure() {
            return units.values().stream()
                    .map(RetryUnitState::failure)
                    .flatMap(Optional::stream)
                    .findFirst();
        }

        /**
         * Batch 생성부터 최종 집계까지 걸린 시간을 밀리초로 반환한다.
         */
        long durationMillis() {
            return Math.max(0, Duration.between(startedAt, Instant.now()).toMillis());
        }

        /**
         * Batch에 적용된 최대 재시도 횟수를 반환한다.
         */
        int maxAttempts() {
            return maxAttempts;
        }
    }

    /**
     * Batch 내부 Unit의 수명 주기를 나타낸다.
     */
    private enum RetryUnitStatus {
        PENDING,
        SUCCEEDED,
        FAILED,
        CANCELLED
    }
}

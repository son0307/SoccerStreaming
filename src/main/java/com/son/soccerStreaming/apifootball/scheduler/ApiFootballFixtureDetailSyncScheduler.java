package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncException;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.live.service.LiveFixtureBroadcastService;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-details.enabled", havingValue = "true")
public class ApiFootballFixtureDetailSyncScheduler {

    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final LiveFixtureBroadcastService liveFixtureBroadcastService;
    private final FixtureRepository fixtureRepository;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;
    private final ApiFootballSyncExecutionGuard executionGuard;

    @Value("${api-football.sync.fixtures.season:2025}")
    private Integer season;

    @Scheduled(cron = "${api-football.sync.fixture-details.live-cron:15 * * * * *}")
    public void syncLiveFixtureDetails() {
        String syncKey = ApiFootballSyncExecutionGuard.key("fixture-details-live", "live");
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncLiveFixtureDetailsSafely(syncKey))) {
            log.info("API-Football live fixture detail sync skipped because the same job is active. syncKey={}", syncKey);
        }
    }

    private void syncLiveFixtureDetailsSafely(String syncKey) {
        try {
            if (syncLiveFixtureDetailsNow()) {
                failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
            }
        } catch (Exception e) {
            log.error("API-Football live fixture detail sync failed.", e);
            scheduleFixtureDetailRetry(syncKey, "live", true, e);
        }
    }

    @Scheduled(cron = "${api-football.sync.fixture-details.daily-cron:0 55 4 * * *}")
    public void syncFixtureDetailsDaily() {
        String syncKey = ApiFootballSyncExecutionGuard.key("fixture-details", "season=" + season);
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncFixtureDetailsDailyNow(syncKey))) {
            log.info("API-Football daily fixture detail sync skipped because the same job is active. syncKey={}", syncKey);
        }
    }

    private void syncFixtureDetailsDailyNow(String syncKey) {
        try {
            apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football daily fixture detail sync failed.", e);
            scheduleFixtureDetailRetry(syncKey, "daily:%s".formatted(season), false, e);
        }
    }

    private boolean syncLiveFixtureDetailsNow() {
        List<Fixture> liveFixtures = fixtureRepository.findAllByFixtureStatus("LIVE");
        if (liveFixtures.isEmpty()) {
            return false;
        }

        log.info("API-Football live fixture detail sync started. fixtureCount={}", liveFixtures.size());
        List<ApiFootballFixtureDetailSyncService.FixtureDetailSyncResult> results =
                apiFootballFixtureDetailSyncService.syncFixtureDetailsWithResults(
                liveFixtures,
                true
        );
        results.forEach(result -> liveFixtureBroadcastService.broadcastFixture(result.fixtureId(), result.latestEvent()));
        log.info("API-Football live fixture detail sync completed. fixtureCount={}, broadcastCount={}",
                liveFixtures.size(), results.size());
        return true;
    }

    private void scheduleFixtureDetailRetry(String syncKey, String reason,
                                            boolean applyLiveStandingImpact, Exception exception) {
        if (exception instanceof ApiFootballFixtureDetailSyncException fixtureDetailException) {
            java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger(1);
            List<ApiFootballRetryUnit> units = fixtureDetailException.getFailedChunks().stream()
                    .map(chunk -> {
                        String fixtureIds = chunk.stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining("-"));
                        return new ApiFootballRetryUnit(
                                "fixture-details:%s:chunk:%s".formatted(reason, fixtureIds),
                                "fixture detail sync reason=%s chunk=%s".formatted(reason, index.getAndIncrement()),
                                () -> apiFootballFixtureDetailSyncService
                                        .syncFixtureDetailsByIds(chunk, applyLiveStandingImpact)
                        );
                    })
                    .toList();
            failureRetryScheduler.scheduleBatch(ApiFootballRetryBatchRequest.partialUnits(
                    syncKey,
                    "fixture detail sync reason=%s".formatted(reason),
                    exception,
                    units
            ));
            return;
        }

        failureRetryScheduler.schedule(
                "fixture-details:%s".formatted(reason),
                syncKey,
                "fixture detail sync reason=%s".formatted(reason),
                exception,
                applyLiveStandingImpact ? this::syncLiveFixtureDetailsNow
                        : () -> apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false)
        );
    }
}

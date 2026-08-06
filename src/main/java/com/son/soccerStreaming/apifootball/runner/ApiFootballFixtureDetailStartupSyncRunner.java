package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.scheduler.ApiFootballRetryBatchRequest;
import com.son.soccerStreaming.apifootball.scheduler.ApiFootballRetryUnit;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncException;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@Profile("prod")
@Order(4)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixture-details.run-on-startup", havingValue = "true")
public class ApiFootballFixtureDetailStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.fixtures.season:2025}")
    private Integer season;

    @Override
    public void run(String... args) {
        String syncKey = ApiFootballSyncExecutionGuard.key("fixture-details", "season=" + season);
        log.info("API-Football startup fixture detail sync started. season={}", season);
        try {
            int syncedCount = apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
            log.info("API-Football startup fixture detail sync completed. season={}, count={}", season, syncedCount);
        } catch (Exception e) {
            log.error("API-Football startup fixture detail sync failed. season={}", season, e);
            scheduleRetry(syncKey, e);
        }
    }

    private void scheduleRetry(String syncKey, Exception exception) {
        if (exception instanceof ApiFootballFixtureDetailSyncException fixtureDetailException) {
            java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger(1);
            List<ApiFootballRetryUnit> units = fixtureDetailException.getFailedChunks().stream()
                    .map(chunk -> {
                        String fixtureIds = chunk.stream()
                                .map(String::valueOf)
                                .collect(java.util.stream.Collectors.joining("-"));
                        return new ApiFootballRetryUnit(
                                "startup:fixture-details:%s:chunk:%s".formatted(season, fixtureIds),
                                "startup fixture detail sync season=%s chunk=%s"
                                        .formatted(season, index.getAndIncrement()),
                                () -> apiFootballFixtureDetailSyncService.syncFixtureDetailsByIds(chunk, false)
                        );
                    })
                    .toList();
            failureRetryScheduler.scheduleBatch(ApiFootballRetryBatchRequest.partialUnits(
                    syncKey,
                    "startup fixture detail sync season=%s".formatted(season),
                    exception,
                    units
            ));
            return;
        }

        failureRetryScheduler.schedule(
                "startup:fixture-details:%s".formatted(season),
                syncKey,
                "startup fixture detail sync season=%s".formatted(season),
                exception,
                () -> apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false)
        );
    }
}

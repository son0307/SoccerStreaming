package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("prod")
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixtures.run-on-startup", havingValue = "true")
public class ApiFootballFixtureStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.fixtures.league:39}")
    private Integer league;

    @Value("${api-football.sync.fixtures.season:2025}")
    private Integer season;

    @Override
    public void run(String... args) {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "fixtures", "league=%s; season=%s".formatted(league, season));
        try {
            log.info("API-Football startup fixture sync started.");
            apiFootballFixtureSyncService.syncSeasonFixtures(league, season);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football startup fixture sync failed. league={}, season={}", league, season, e);
            failureRetryScheduler.schedule(
                    "startup:fixtures:%s:%s".formatted(league, season),
                    syncKey,
                    "startup fixture sync league=%s season=%s".formatted(league, season),
                    e,
                    () -> apiFootballFixtureSyncService.syncSeasonFixtures(league, season)
            );
        }
    }
}

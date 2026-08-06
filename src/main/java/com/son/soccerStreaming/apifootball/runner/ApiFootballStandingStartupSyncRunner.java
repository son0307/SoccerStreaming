package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingSyncService;
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
@Order(3)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.standings.run-on-startup", havingValue = "true")
public class ApiFootballStandingStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballStandingSyncService apiFootballStandingSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.standings.league:39}")
    private Integer league;

    @Value("${api-football.sync.standings.season:2025}")
    private Integer season;

    @Override
    public void run(String... args) {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "standings", "league=%s; season=%s".formatted(league, season));
        log.info("API-Football startup standing sync started.");
        try {
            apiFootballStandingSyncService.syncStandings(league, season);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football startup standing sync failed. league={}, season={}", league, season, e);
            failureRetryScheduler.schedule(
                    "startup:standings:%s:%s".formatted(league, season),
                    syncKey,
                    "startup standing sync league=%s season=%s".formatted(league, season),
                    e,
                    () -> apiFootballStandingSyncService.syncStandings(league, season)
            );
        }
    }
}

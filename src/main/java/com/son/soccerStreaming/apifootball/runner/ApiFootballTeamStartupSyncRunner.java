package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballTeamSyncService;
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
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.teams.run-on-startup", havingValue = "true")
public class ApiFootballTeamStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballTeamSyncService apiFootballTeamSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.teams.league:39}")
    private Integer league;

    @Value("${api-football.sync.teams.season:2025}")
    private Integer season;

    @Override
    public void run(String... args) {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "teams", "league=%s; season=%s".formatted(league, season));
        log.info("API-Football startup team sync started.");
        try {
            apiFootballTeamSyncService.syncTeams(league, season);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football startup team sync failed. league={}, season={}", league, season, e);
            failureRetryScheduler.schedule(
                    "startup:teams:%s:%s".formatted(league, season),
                    syncKey,
                    "startup team sync league=%s season=%s".formatted(league, season),
                    e,
                    () -> apiFootballTeamSyncService.syncTeams(league, season)
            );
        }
    }
}

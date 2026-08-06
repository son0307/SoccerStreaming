package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.LeagueSeasonCoverageSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.league-seasons.run-on-startup", havingValue = "true")
public class LeagueSeasonCoverageStartupSyncRunner implements CommandLineRunner {

    private final LeagueSeasonCoverageSyncService leagueSeasonCoverageSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.league-seasons.league:39}")
    private Integer league;

    @Override
    public void run(String... args) {
        String syncKey = ApiFootballSyncExecutionGuard.key("seasons", "league=" + league);
        log.info("API-Football startup league season coverage sync started. league={}", league);
        try {
            leagueSeasonCoverageSyncService.syncLeagueSeasons(league);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football startup league season coverage sync failed. league={}", league, e);
            failureRetryScheduler.schedule(
                    "startup:league-seasons:%s".formatted(league),
                    syncKey,
                    "startup league season coverage sync league=%s".formatted(league),
                    e,
                    () -> leagueSeasonCoverageSyncService.syncLeagueSeasons(league)
            );
        }
    }
}

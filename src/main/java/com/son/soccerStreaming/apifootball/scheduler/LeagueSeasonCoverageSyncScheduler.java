package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.LeagueSeasonCoverageSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.league-seasons.enabled", havingValue = "true")
public class LeagueSeasonCoverageSyncScheduler {

    private final LeagueSeasonCoverageSyncService leagueSeasonCoverageSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;
    private final ApiFootballSyncExecutionGuard executionGuard;

    @Value("${api-football.sync.league-seasons.league:39}")
    private Integer league;

    @Scheduled(cron = "${api-football.sync.league-seasons.daily-cron:0 0 3 * * *}")
    public void syncLeagueSeasons() {
        String syncKey = ApiFootballSyncExecutionGuard.key("seasons", "league=" + league);
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncLeagueSeasonsNow(syncKey))) {
            log.info("API-Football league season sync skipped because the same job is active. syncKey={}", syncKey);
        }
    }

    private void syncLeagueSeasonsNow(String syncKey) {
        try {
            leagueSeasonCoverageSyncService.syncLeagueSeasons(league);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football league season coverage sync failed. league={}", league, e);
            failureRetryScheduler.schedule(
                    "league-seasons:%s".formatted(league),
                    syncKey,
                    "league season coverage sync league=%s".formatted(league),
                    e,
                    () -> leagueSeasonCoverageSyncService.syncLeagueSeasons(league)
            );
        }
    }
}

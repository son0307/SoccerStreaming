package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballTeamSyncService;
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
@ConditionalOnProperty(name = "api-football.sync.teams.enabled", havingValue = "true")
public class ApiFootballTeamSyncScheduler {

    private final ApiFootballTeamSyncService apiFootballTeamSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;
    private final ApiFootballSyncExecutionGuard executionGuard;

    @Value("${api-football.sync.teams.league:39}")
    private Integer league;

    @Value("${api-football.sync.teams.season:2025}")
    private Integer season;

    @Scheduled(cron = "${api-football.sync.teams.cron:0 0 4 * * *}")
    public void syncTeams() {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "teams", "league=%s; season=%s".formatted(league, season));
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncTeamsNow(syncKey))) {
            log.info("API-Football team sync skipped because the same job is active. syncKey={}", syncKey);
        }
    }

    private void syncTeamsNow(String syncKey) {
        try {
            apiFootballTeamSyncService.syncTeams(league, season);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football team sync failed. league={}, season={}", league, season, e);
            failureRetryScheduler.schedule(
                    "teams:%s:%s".formatted(league, season),
                    syncKey,
                    "team sync league=%s season=%s".formatted(league, season),
                    e,
                    () -> apiFootballTeamSyncService.syncTeams(league, season)
            );
        }
    }
}

package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballInjurySyncService;
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
@ConditionalOnProperty(name = "api-football.sync.injuries.enabled", havingValue = "true")
public class ApiFootballInjurySyncScheduler {

    private final ApiFootballInjurySyncService apiFootballInjurySyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;
    private final ApiFootballSyncExecutionGuard executionGuard;

    @Value("${api-football.sync.injuries.league:39}")
    private Integer league;

    @Value("${api-football.sync.injuries.season:2025}")
    private Integer season;

    @Scheduled(cron = "${api-football.sync.injuries.daily-cron:0 45 5 * * *}")
    public void syncInjuriesDaily() {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "injuries", "league=%s; season=%s".formatted(league, season));
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncInjuriesNow(syncKey))) {
            log.info("API-Football injury sync skipped because the same job is active. syncKey={}", syncKey);
        }
    }

    private void syncInjuriesNow(String syncKey) {
        try {
            apiFootballInjurySyncService.syncInjuries(league, season);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football injury sync failed. league={}, season={}", league, season, e);
            failureRetryScheduler.schedule(
                    "injuries:%s:%s".formatted(league, season),
                    syncKey,
                    "injury sync league=%s season=%s".formatted(league, season),
                    e,
                    () -> apiFootballInjurySyncService.syncInjuries(league, season)
            );
        }
    }
}

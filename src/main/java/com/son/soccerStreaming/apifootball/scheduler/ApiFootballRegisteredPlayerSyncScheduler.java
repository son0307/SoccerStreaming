package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballRegisteredPlayerSyncException;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
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
@ConditionalOnProperty(name = "api-football.sync.players.registered.enabled", havingValue = "true")
public class ApiFootballRegisteredPlayerSyncScheduler {

    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;
    private final ApiFootballSyncExecutionGuard executionGuard;

    @Value("${api-football.sync.players.registered.league:39}")
    private Integer league;

    @Value("${api-football.sync.players.registered.season:2025}")
    private Integer season;

    @Value("${api-football.sync.players.registered.delay-ms:7000}")
    private Long delayMs;

    @Scheduled(cron = "${api-football.sync.players.registered.daily-cron:0 10 5 * * *}")
    public void syncRegisteredPlayersDaily() {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "players", "league=%s; season=%s".formatted(league, season));
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncRegisteredPlayersNow(syncKey))) {
            log.info("API-Football registered player sync skipped because the same job is active. syncKey={}", syncKey);
        }
    }

    private void syncRegisteredPlayersNow(String syncKey) {
        try {
            apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football registered player sync failed. league={}, season={}", league, season, e);
            scheduleRetry(syncKey, e);
        }
    }

    private void scheduleRetry(String syncKey, Exception exception) {
        if (exception instanceof ApiFootballRegisteredPlayerSyncException playerSyncException) {
            List<ApiFootballRetryUnit> units = playerSyncException.getFailedTeamIds().stream()
                    .map(teamId -> new ApiFootballRetryUnit(
                            "registered-players:%s:%s:team:%s".formatted(league, season, teamId),
                            "registered player sync league=%s season=%s teamId=%s"
                                    .formatted(league, season, teamId),
                            () -> apiFootballPlayerSyncService
                                    .syncRegisteredPlayersByTeamId(teamId, league, season, delayMs)
                    ))
                    .toList();
            failureRetryScheduler.scheduleBatch(ApiFootballRetryBatchRequest.partialUnits(
                    syncKey,
                    "registered player sync league=%s season=%s".formatted(league, season),
                    exception,
                    units
            ));
            return;
        }

        failureRetryScheduler.schedule(
                "registered-players:%s:%s".formatted(league, season),
                syncKey,
                "registered player sync league=%s season=%s".formatted(league, season),
                exception,
                () -> apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs)
        );
    }
}

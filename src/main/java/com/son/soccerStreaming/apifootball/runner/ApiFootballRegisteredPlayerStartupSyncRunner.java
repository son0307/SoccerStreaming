package com.son.soccerStreaming.apifootball.runner;

import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.scheduler.ApiFootballRetryBatchRequest;
import com.son.soccerStreaming.apifootball.scheduler.ApiFootballRetryUnit;
import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballRegisteredPlayerSyncException;
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
@Order(6)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.players.registered.run-on-startup", havingValue = "true")
public class ApiFootballRegisteredPlayerStartupSyncRunner implements CommandLineRunner {

    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;

    @Value("${api-football.sync.players.registered.league:39}")
    private Integer league;

    @Value("${api-football.sync.players.registered.season:2025}")
    private Integer season;

    @Value("${api-football.sync.players.registered.startup-delay-ms:7000}")
    private Long delayMs;

    @Override
    public void run(String... args) {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "players", "league=%s; season=%s".formatted(league, season));
        log.info("API-Football startup registered player sync started. league={}, season={}", league, season);
        try {
            apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football startup registered player sync failed. league={}, season={}", league, season, e);
            scheduleRetry(syncKey, e);
        }
    }

    private void scheduleRetry(String syncKey, Exception exception) {
        if (exception instanceof ApiFootballRegisteredPlayerSyncException playerSyncException) {
            List<ApiFootballRetryUnit> units = playerSyncException.getFailedTeamIds().stream()
                    .map(teamId -> new ApiFootballRetryUnit(
                            "startup:registered-players:%s:%s:team:%s".formatted(league, season, teamId),
                            "startup registered player sync league=%s season=%s teamId=%s"
                                    .formatted(league, season, teamId),
                            () -> apiFootballPlayerSyncService
                                    .syncRegisteredPlayersByTeamId(teamId, league, season, delayMs)
                    ))
                    .toList();
            failureRetryScheduler.scheduleBatch(ApiFootballRetryBatchRequest.partialUnits(
                    syncKey,
                    "startup registered player sync league=%s season=%s".formatted(league, season),
                    exception,
                    units
            ));
            return;
        }

        failureRetryScheduler.schedule(
                "startup:registered-players:%s:%s".formatted(league, season),
                syncKey,
                "startup registered player sync league=%s season=%s".formatted(league, season),
                exception,
                () -> apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs)
        );
    }
}

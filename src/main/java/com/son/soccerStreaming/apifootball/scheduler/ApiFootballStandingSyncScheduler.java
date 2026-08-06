package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.standings.enabled", havingValue = "true")
public class ApiFootballStandingSyncScheduler {

    private final ApiFootballStandingSyncService apiFootballStandingSyncService;
    private final FixtureRepository fixtureRepository;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;
    private final ApiFootballSyncExecutionGuard executionGuard;

    @Value("${api-football.sync.standings.league:39}")
    private Integer league;

    @Value("${api-football.sync.standings.season:2025}")
    private Integer season;

    @Scheduled(cron = "${api-football.sync.standings.daily-cron:0 10 4 * * *}")
    public void syncStandingsDaily() {
        syncStandings("daily");
    }

    @Scheduled(cron = "${api-football.sync.standings.live-cron:0 0 * * * *}")
    public void syncStandingsHourlyWhenLive() {
        if (!fixtureRepository.existsByFixtureStatus("LIVE")) {
            return;
        }
        syncStandings("hourly-live");
    }

    private void syncStandings(String reason) {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "standings", "league=%s; season=%s".formatted(league, season));
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncStandingsNow(reason, syncKey))) {
            log.info("API-Football standing sync skipped because the same job is active. syncKey={}, reason={}",
                    syncKey, reason);
        }
    }

    private void syncStandingsNow(String reason, String syncKey) {
        try {
            apiFootballStandingSyncService.syncStandings(league, season);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football standing sync failed. reason={}, league={}, season={}", reason, league, season, e);
            failureRetryScheduler.schedule(
                    "standings:%s:%s:%s".formatted(reason, league, season),
                    syncKey,
                    "standing sync reason=%s league=%s season=%s".formatted(reason, league, season),
                    e,
                    () -> apiFootballStandingSyncService.syncStandings(league, season)
            );
        }
    }
}

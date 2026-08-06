package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "api-football.sync.fixtures.enabled", havingValue = "true")
public class ApiFootballFixtureSyncScheduler {

    private static final List<String> LIVE_CANDIDATE_STATUSES = List.of("SCHEDULED", "LIVE");

    private final ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    private final FixtureRepository fixtureRepository;
    private final ApiFootballSyncFailureRetryScheduler failureRetryScheduler;
    private final ApiFootballSyncExecutionGuard executionGuard;

    @Value("${api-football.sync.fixtures.league:39}")
    private Integer league;

    @Value("${api-football.sync.fixtures.season:2025}")
    private Integer season;

    @Value("${api-football.sync.fixtures.live-window-before-minutes:10}")
    private Long liveWindowBeforeMinutes;

    @Value("${api-football.sync.fixtures.live-window-after-hours:4}")
    private Long liveWindowAfterHours;

    @Scheduled(cron = "${api-football.sync.fixtures.daily-cron:0 20 4 * * *}")
    public void syncSeasonFixturesDaily() {
        syncSeasonFixtures("daily");
    }

    @Scheduled(cron = "${api-football.sync.fixtures.live-cron:0 * * * * *}")
    public void syncLiveFixturesWhenMatchWindowOpen() {
        if (!hasFixtureInLiveWindow()) {
            return;
        }

        String syncKey = ApiFootballSyncExecutionGuard.key(
                "fixtures-live", "league=%s; season=%s".formatted(league, season));
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncLiveFixturesNow(syncKey))) {
            log.info("API-Football live fixture sync skipped because the same job is active. syncKey={}", syncKey);
        }
    }

    private void syncLiveFixturesNow(String syncKey) {
        try {
            apiFootballFixtureSyncService.syncLiveFixtures(league, season);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football live fixture sync failed.", e);
            failureRetryScheduler.schedule(
                    "fixtures:live:%s:%s".formatted(league, season),
                    syncKey,
                    "live fixture sync league=%s season=%s".formatted(league, season),
                    e,
                    () -> apiFootballFixtureSyncService.syncLiveFixtures(league, season)
            );
        }
    }

    private void syncSeasonFixtures(String reason) {
        String syncKey = ApiFootballSyncExecutionGuard.key(
                "fixtures", "league=%s; season=%s".formatted(league, season));
        if (!executionGuard.executeIfAvailable(syncKey, () -> syncSeasonFixturesNow(reason, syncKey))) {
            log.info("API-Football season fixture sync skipped because the same job is active. syncKey={}, reason={}",
                    syncKey, reason);
        }
    }

    private void syncSeasonFixturesNow(String reason, String syncKey) {
        try {
            apiFootballFixtureSyncService.syncSeasonFixtures(league, season);
            failureRetryScheduler.cancelPendingByExecutionKey(syncKey);
        } catch (Exception e) {
            log.error("API-Football season fixture sync failed. reason={}, league={}, season={}", reason, league, season, e);
            failureRetryScheduler.schedule(
                    "fixtures:%s:%s:%s".formatted(reason, league, season),
                    syncKey,
                    "season fixture sync reason=%s league=%s season=%s".formatted(reason, league, season),
                    e,
                    () -> apiFootballFixtureSyncService.syncSeasonFixtures(league, season)
            );
        }
    }

    private boolean hasFixtureInLiveWindow() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return fixtureRepository.existsByFixtureDateBetweenAndFixtureStatusIn(
                now.minusHours(liveWindowAfterHours),
                now.plusMinutes(liveWindowBeforeMinutes),
                LIVE_CANDIDATE_STATUSES
        );
    }
}

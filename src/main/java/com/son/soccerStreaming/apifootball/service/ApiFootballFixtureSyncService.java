package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixtureSyncService {

    private final ApiFootballClient apiFootballClient;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;
    private final AdminOverrideService adminOverrideService;
    private final OptimisticLockRetryExecutor optimisticLockRetryExecutor;
    private static final List<String> OVERRIDE_FIELDS = List.of(
            "fixtureDate", "referee", "venueId", "venueName", "venueCity"
    );
    private static final Set<String> LIVE_STATUS_SHORTS = Set.of("1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE");
    private static final Set<String> FINISHED_STATUS_SHORTS = Set.of("FT", "AET", "PEN");

    @CacheEvict(
            cacheNames = {
                    RedisCacheConfig.FAVORITE_TEAM_CARD_CACHE,
                    RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE
            },
            allEntries = true
    )
    public int syncSeasonFixtures(Integer league, Integer season) {
        apiFootballSyncStatusService.recordAttempt("fixtures", "Fixtures", season);
        List<ApiFootballLiveDto.FixtureResponse> responses = apiFootballClient.getFixtures(league, season);
        int syncedCount = optimisticLockRetryExecutor.execute(
                "fixtures:league=%s;season=%s".formatted(league, season),
                () -> upsertFixtures(responses, false)
        );
        apiFootballSyncStatusService.recordSuccess("fixtures", "Fixtures", season);
        return syncedCount;
    }

    @CacheEvict(
            cacheNames = {
                    RedisCacheConfig.FAVORITE_TEAM_CARD_CACHE,
                    RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE
            },
            allEntries = true
    )
    public int syncLiveFixtures(Integer league, Integer season) {
        apiFootballSyncStatusService.recordAttempt("fixtures", "Fixtures", season);
        List<ApiFootballLiveDto.FixtureResponse> liveFixtures = apiFootballClient.getLiveFixtures(league).stream()
                .filter(response -> matchesSeason(response, season))
                .toList();

        int syncedCount = optimisticLockRetryExecutor.execute(
                "live-fixtures:league=%s;season=%s".formatted(league, season),
                () -> upsertFixtures(liveFixtures, true)
        );
        apiFootballSyncStatusService.recordSuccess("fixtures", "Fixtures", season);
        return syncedCount;
    }

    @CacheEvict(
            cacheNames = {
                    RedisCacheConfig.FAVORITE_TEAM_CARD_CACHE,
                    RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE
            },
            allEntries = true
    )
    @Transactional
    public Optional<Fixture> syncFixtureResponse(ApiFootballLiveDto.FixtureResponse response, boolean applyLiveStandingImpact) {
        Optional<Fixture> fixture = upsertFixture(response);
        if (fixture.isPresent() && applyLiveStandingImpact) {
            apiFootballStandingLocalUpdateService.applyFixtureState(fixture.get());
        }
        return fixture;
    }

    private int upsertFixtures(List<ApiFootballLiveDto.FixtureResponse> responses, boolean applyLiveStandingImpact) {
        int syncedCount = 0;

        for (ApiFootballLiveDto.FixtureResponse response : responses) {
            Optional<Fixture> fixture = upsertFixture(response);
            if (fixture.isEmpty()) {
                continue;
            }

            if (applyLiveStandingImpact) {
                apiFootballStandingLocalUpdateService.applyFixtureState(fixture.get());
            }
            syncedCount++;
        }

        log.info("API-Football fixture sync completed. live={}, count={}", applyLiveStandingImpact, syncedCount);
        return syncedCount;
    }

    private Optional<Fixture> upsertFixture(ApiFootballLiveDto.FixtureResponse response) {
        ApiFootballLiveDto.FixtureInfo fixtureInfo = response.getFixture();
        ApiFootballLiveDto.Teams teams = response.getTeams();

        if (fixtureInfo == null || fixtureInfo.getId() == null || teams == null
                || teams.getHome() == null || teams.getHome().getId() == null
                || teams.getAway() == null || teams.getAway().getId() == null) {
            return Optional.empty();
        }

        Optional<Team> homeTeam = teamRepository.findByTeamId(teams.getHome().getId());
        Optional<Team> awayTeam = teamRepository.findByTeamId(teams.getAway().getId());
        if (homeTeam.isEmpty() || awayTeam.isEmpty()) {
            log.warn("Skip fixture sync because team does not exist. fixtureId={}, homeTeamId={}, awayTeamId={}",
                    fixtureInfo.getId(), teams.getHome().getId(), teams.getAway().getId());
            return Optional.empty();
        }

        Fixture fixture = fixtureRepository.findByFixtureId(fixtureInfo.getId())
                .orElseGet(() -> Fixture.builder()
                        .fixtureId(fixtureInfo.getId())
                        .homeTeam(homeTeam.get())
                        .awayTeam(awayTeam.get())
                        .fixtureDate(parseFixtureDate(fixtureInfo.getDate(), LocalDateTime.now(ZoneOffset.UTC)))
                        .build());

        Set<String> overrides = adminOverrideService.overriddenFields(
                AdminOverrideTargetType.FIXTURE,
                fixtureInfo.getId(),
                OVERRIDE_FIELDS
        );
        updateFixture(fixture, response, overrides);
        return Optional.of(fixtureRepository.save(fixture));
    }

    private boolean matchesSeason(ApiFootballLiveDto.FixtureResponse response, Integer season) {
        ApiFootballLiveDto.LeagueInfo leagueInfo = response.getLeague();
        if (leagueInfo == null) {
            return false;
        }

        return leagueInfo.getSeason() != null && leagueInfo.getSeason().equals(season);
    }

    private void updateFixture(
            Fixture fixture,
            ApiFootballLiveDto.FixtureResponse response,
            Set<String> overrides
    ) {
        ApiFootballLiveDto.FixtureInfo fixtureInfo = response.getFixture();
        ApiFootballLiveDto.LeagueInfo league = response.getLeague();
        ApiFootballLiveDto.Status status = fixtureInfo != null ? fixtureInfo.getStatus() : null;
        ApiFootballLiveDto.Goals goals = response.getGoals();
        ApiFootballLiveDto.Teams teams = response.getTeams();
        ApiFootballLiveDto.Score score = response.getScore();

        String statusShort = status != null ? status.getShortStatus() : fixture.getStatusShort();
        String statusLong = status != null ? status.getLongStatus() : fixture.getStatusLong();

        ApiFootballLiveDto.Periods periods = fixtureInfo != null ? fixtureInfo.getPeriods() : null;
        ApiFootballLiveDto.Venue venue = fixtureInfo != null ? fixtureInfo.getVenue() : null;
        fixture.updateFixtureMetadata(
                adminOverrideService.apiValueUnlessOverridden(
                        overrides, "fixtureDate", fixture.getFixtureDate(),
                        parseFixtureDate(fixtureInfo != null ? fixtureInfo.getDate() : null, fixture.getFixtureDate())),
                adminOverrideService.apiValueUnlessOverridden(
                        overrides, "referee", fixture.getReferee(),
                        fixtureInfo != null ? fixtureInfo.getReferee() : fixture.getReferee()),
                fixtureInfo != null ? fixtureInfo.getTimezone() : fixture.getTimezone(),
                fixtureInfo != null ? fixtureInfo.getTimestamp() : fixture.getTimestamp(),
                periods != null ? periods.getFirst() : fixture.getFirstPeriod(),
                periods != null ? periods.getSecond() : fixture.getSecondPeriod(),
                adminOverrideService.apiValueUnlessOverridden(
                        overrides, "venueId", fixture.getVenueId(),
                        venue != null ? venue.getId() : fixture.getVenueId()),
                adminOverrideService.apiValueUnlessOverridden(
                        overrides, "venueName", fixture.getVenueName(),
                        venue != null ? venue.getName() : fixture.getVenueName()),
                adminOverrideService.apiValueUnlessOverridden(
                        overrides, "venueCity", fixture.getVenueCity(),
                        venue != null ? venue.getCity() : fixture.getVenueCity())
        );

        fixture.updateFixtureState(
                statusShort,
                statusLong,
                fixtureStatusOf(statusShort),
                status != null ? status.getElapsed() : fixture.getElapsed(),
                status != null ? status.getExtra() : fixture.getExtra(),
                goals != null ? goals.getHome() : fixture.getHomeScore(),
                goals != null ? goals.getAway() : fixture.getAwayScore()
        );

        fixture.updateTeamResult(
                teams != null && teams.getHome() != null ? teams.getHome().getWinner() : fixture.getHomeWinner(),
                teams != null && teams.getAway() != null ? teams.getAway().getWinner() : fixture.getAwayWinner()
        );

        if (score != null) {
            fixture.updateScoreBreakdown(
                    homeScoreOf(score.getHalftime()),
                    awayScoreOf(score.getHalftime()),
                    homeScoreOf(score.getFulltime()),
                    awayScoreOf(score.getFulltime()),
                    homeScoreOf(score.getExtratime()),
                    awayScoreOf(score.getExtratime()),
                    homeScoreOf(score.getPenalty()),
                    awayScoreOf(score.getPenalty())
            );
        }

        if (league != null) {
            fixture.updateRound(league.getRound());
        }

        if (league != null && (league.getId() != null || league.getSeason() != null)) {
            fixture.updateLeagueAndSeason(
                    league.getId() != null ? Math.toIntExact(league.getId()) : fixture.getLeagueId(),
                    league.getSeason() != null ? league.getSeason() : fixture.getSeason()
            );
        }
    }

    private String fixtureStatusOf(String statusShort) {
        if (statusShort == null || "NS".equals(statusShort) || "TBD".equals(statusShort)) {
            return "SCHEDULED";
        }
        if (FINISHED_STATUS_SHORTS.contains(statusShort)) {
            return "FINISHED";
        }
        return LIVE_STATUS_SHORTS.contains(statusShort) ? "LIVE" : "SCHEDULED";
    }

    private LocalDateTime parseFixtureDate(String date, LocalDateTime fallback) {
        if (date == null || date.isBlank()) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(date).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse API-Football fixture date. date={}", date);
            return fallback;
        }
    }

    private Integer homeScoreOf(ApiFootballLiveDto.ScoreDetail score) {
        return score != null ? score.getHome() : null;
    }

    private Integer awayScoreOf(ApiFootballLiveDto.ScoreDetail score) {
        return score != null ? score.getAway() : null;
    }
}

package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballStandingLocalUpdateService {

    private static final String LIVE_IMPACT_KEY = "standing:live-impact:%d:%d";
    private static final String LIVE_IMPACT_PATTERN = "standing:live-impact:%d:*";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TeamStandingRepository teamStandingRepository;

    @Value("${api-football.sync.standings.local-finished-update-enabled:false}")
    private boolean localFinishedUpdateEnabled;

    @Value("${api-football.sync.standings.local-live-update-enabled:false}")
    private boolean localLiveUpdateEnabled;

    @Value("${api-football.sync.standings.season:2025}")
    private Integer season;

    @Value("${api-football.sync.standings.league:39}")
    private Integer league;

    @Value("${api-football.sync.standings.local-live-impact-ttl-hours:6}")
    private Long liveImpactTtlHours;

    @Value("${api-football.sync.standings.local-finished-impact-ttl-hours:48}")
    private Long finishedImpactTtlHours;

    public void applyFixtureState(Fixture fixture) {
        Integer impactSeason = fixture.getSeason() != null ? fixture.getSeason() : season;
        Integer impactLeague = fixture.getLeagueId() != null ? fixture.getLeagueId() : league;
        String statusShort = fixture.getStatusShort();

        if (!isEnabledForStatus(statusShort)) {
            deleteImpact(fixture.getFixtureId(), impactSeason);
            return;
        }

        if (fixture.getHomeScore() == null || fixture.getAwayScore() == null) {
            return;
        }

        Optional<LiveStandingImpact> existingImpact = findImpact(fixture.getFixtureId(), impactSeason);
        BaselinePair baseline = resolveBaselines(fixture, impactLeague, impactSeason, existingImpact);

        saveImpact(new LiveStandingImpact(
                fixture.getFixtureId(),
                impactSeason,
                fixture.getHomeTeam().getTeamId(),
                fixture.getAwayTeam().getTeamId(),
                fixture.getHomeScore(),
                fixture.getAwayScore(),
                statusShort,
                fixture.getElapsed(),
                fixture.getExtra(),
                baseline.home() != null ? baseline.home().getPlayed() : null,
                baseline.away() != null ? baseline.away().getPlayed() : null,
                LocalDateTime.now(),
                baseline.home(),
                baseline.away()
        ));

        log.debug("Standing live impact cached. fixtureId={}, season={}, status={}, score={}-{}",
                fixture.getFixtureId(), impactSeason, statusShort, fixture.getHomeScore(), fixture.getAwayScore());
    }

    public List<LiveStandingImpact> findImpacts(Integer season) {
        Set<String> keys = redisTemplate.keys(LIVE_IMPACT_PATTERN.formatted(season));
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<LiveStandingImpact> impacts = new ArrayList<>();
        for (String key : keys) {
            String impactJson = redisTemplate.opsForValue().get(key);
            readImpact(key, impactJson).ifPresent(impacts::add);
        }
        return impacts;
    }

    public boolean hasFinishedImpacts(Integer season) {
        return findImpacts(season).stream().anyMatch(impact -> isFinished(impact.getStatusShort()));
    }

    public void reconcileFinishedImpacts(Integer league, Integer season) {
        for (LiveStandingImpact impact : findImpacts(season)) {
            if (!isFinished(impact.getStatusShort())) {
                continue;
            }

            if (!hasDetailedBaseline(impact)) {
                log.warn("Keeping legacy finished standing impact until TTL because detailed baseline is missing. " +
                                "fixtureId={}, season={}", impact.getFixtureId(), season);
                continue;
            }

            Optional<TeamStanding> homeStanding = findStanding(impact.getHomeTeamId(), league, season);
            Optional<TeamStanding> awayStanding = findStanding(impact.getAwayTeamId(), league, season);
            if (homeStanding.isEmpty() || awayStanding.isEmpty()) {
                continue;
            }

            StandingBaseline currentHome = toBaseline(homeStanding.get(), true);
            StandingBaseline currentAway = toBaseline(awayStanding.get(), false);
            if (isReflected(impact, currentHome, currentAway)) {
                deleteImpact(impact.getFixtureId(), season);
                log.info("Finished standing impact removed after authoritative standings reflected fixture. " +
                                "fixtureId={}, season={}", impact.getFixtureId(), season);
            }
        }
    }

    public boolean isReflected(LiveStandingImpact impact,
                               StandingBaseline currentHome,
                               StandingBaseline currentAway) {
        return hasDetailedBaseline(impact)
                && includesFixtureResult(currentHome, impact.getHomeBaseline(),
                impact.getHomeScore(), impact.getAwayScore())
                && includesFixtureResult(currentAway, impact.getAwayBaseline(),
                impact.getAwayScore(), impact.getHomeScore());
    }

    public boolean isLiveImpact(LiveStandingImpact impact) {
        return isLive(impact.getStatusShort());
    }

    private boolean isEnabledForStatus(String statusShort) {
        return (isLive(statusShort) && localLiveUpdateEnabled)
                || (isFinished(statusShort) && localFinishedUpdateEnabled);
    }

    private BaselinePair resolveBaselines(Fixture fixture, Integer league, Integer season,
                                          Optional<LiveStandingImpact> existingImpact) {
        if (existingImpact.isPresent() && hasDetailedBaseline(existingImpact.get())) {
            LiveStandingImpact impact = existingImpact.get();
            return new BaselinePair(impact.getHomeBaseline(), impact.getAwayBaseline());
        }

        StandingBaseline home = findStanding(fixture.getHomeTeam().getTeamId(), league, season)
                .map(standing -> toBaseline(standing, true))
                .orElse(null);
        StandingBaseline away = findStanding(fixture.getAwayTeam().getTeamId(), league, season)
                .map(standing -> toBaseline(standing, false))
                .orElse(null);
        return new BaselinePair(home, away);
    }

    private Optional<TeamStanding> findStanding(Long teamId, Integer league, Integer season) {
        return teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(teamId, league, season);
    }

    private StandingBaseline toBaseline(TeamStanding standing, boolean homeSide) {
        return StandingBaseline.builder()
                .played(valueOf(standing.getPlayed()))
                .points(valueOf(standing.getPoints()))
                .win(valueOf(standing.getWin()))
                .draw(valueOf(standing.getDraw()))
                .lose(valueOf(standing.getLose()))
                .goalsFor(valueOf(standing.getGoalsFor()))
                .goalsAgainst(valueOf(standing.getGoalsAgainst()))
                .venuePlayed(valueOf(homeSide ? standing.getHomePlayed() : standing.getAwayPlayed()))
                .venueWin(valueOf(homeSide ? standing.getHomeWin() : standing.getAwayWin()))
                .venueDraw(valueOf(homeSide ? standing.getHomeDraw() : standing.getAwayDraw()))
                .venueLose(valueOf(homeSide ? standing.getHomeLose() : standing.getAwayLose()))
                .venueGoalsFor(valueOf(homeSide ? standing.getHomeGoalsFor() : standing.getAwayGoalsFor()))
                .venueGoalsAgainst(valueOf(homeSide ? standing.getHomeGoalsAgainst() : standing.getAwayGoalsAgainst()))
                .apiUpdatedAt(standing.getApiUpdatedAt())
                .build();
    }

    private boolean includesFixtureResult(StandingBaseline current, StandingBaseline baseline,
                                          int goalsFor, int goalsAgainst) {
        if (current == null || baseline == null || !isUpdatedAfterBaseline(current, baseline)) {
            return false;
        }

        boolean win = goalsFor > goalsAgainst;
        boolean draw = goalsFor == goalsAgainst;
        boolean lose = goalsFor < goalsAgainst;
        return hasIncreasedBy(current.getPlayed(), baseline.getPlayed(), 1)
                && hasIncreasedBy(current.getPoints(), baseline.getPoints(), pointsFor(goalsFor, goalsAgainst))
                && hasIncreasedBy(current.getWin(), baseline.getWin(), win ? 1 : 0)
                && hasIncreasedBy(current.getDraw(), baseline.getDraw(), draw ? 1 : 0)
                && hasIncreasedBy(current.getLose(), baseline.getLose(), lose ? 1 : 0)
                && hasIncreasedBy(current.getGoalsFor(), baseline.getGoalsFor(), goalsFor)
                && hasIncreasedBy(current.getGoalsAgainst(), baseline.getGoalsAgainst(), goalsAgainst)
                && hasIncreasedBy(current.getVenuePlayed(), baseline.getVenuePlayed(), 1)
                && hasIncreasedBy(current.getVenueWin(), baseline.getVenueWin(), win ? 1 : 0)
                && hasIncreasedBy(current.getVenueDraw(), baseline.getVenueDraw(), draw ? 1 : 0)
                && hasIncreasedBy(current.getVenueLose(), baseline.getVenueLose(), lose ? 1 : 0)
                && hasIncreasedBy(current.getVenueGoalsFor(), baseline.getVenueGoalsFor(), goalsFor)
                && hasIncreasedBy(current.getVenueGoalsAgainst(), baseline.getVenueGoalsAgainst(), goalsAgainst);
    }

    private boolean isUpdatedAfterBaseline(StandingBaseline current, StandingBaseline baseline) {
        return baseline.getApiUpdatedAt() == null
                || (current.getApiUpdatedAt() != null
                && current.getApiUpdatedAt().isAfter(baseline.getApiUpdatedAt()));
    }

    private boolean hasDetailedBaseline(LiveStandingImpact impact) {
        return impact.getHomeBaseline() != null && impact.getAwayBaseline() != null;
    }

    private boolean hasIncreasedBy(Integer current, Integer baseline, int delta) {
        return current != null && baseline != null && current >= baseline + delta;
    }

    private int pointsFor(int goalsFor, int goalsAgainst) {
        if (goalsFor > goalsAgainst) {
            return 3;
        }
        return goalsFor == goalsAgainst ? 1 : 0;
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean isLive(String statusShort) {
        return "1H".equals(statusShort)
                || "HT".equals(statusShort)
                || "2H".equals(statusShort)
                || "ET".equals(statusShort)
                || "BT".equals(statusShort)
                || "P".equals(statusShort)
                || "SUSP".equals(statusShort)
                || "INT".equals(statusShort)
                || "LIVE".equals(statusShort);
    }

    private boolean isFinished(String statusShort) {
        return "FT".equals(statusShort) || "AET".equals(statusShort) || "PEN".equals(statusShort);
    }

    private Optional<LiveStandingImpact> findImpact(Long fixtureId, Integer season) {
        String impactJson = redisTemplate.opsForValue().get(liveImpactKey(fixtureId, season));
        return readImpact(liveImpactKey(fixtureId, season), impactJson);
    }

    private Optional<LiveStandingImpact> readImpact(String key, String impactJson) {
        if (impactJson == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(impactJson, LiveStandingImpact.class));
        } catch (JacksonException e) {
            log.error("Failed to deserialize Redis standing live impact. key={}", key, e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    private void saveImpact(LiveStandingImpact impact) {
        try {
            String impactJson = objectMapper.writeValueAsString(impact);
            long ttlHours = isFinished(impact.getStatusShort())
                    ? finishedImpactTtlHours
                    : liveImpactTtlHours;
            redisTemplate.opsForValue().set(
                    liveImpactKey(impact.getFixtureId(), impact.getSeason()),
                    impactJson,
                    Duration.ofHours(ttlHours)
            );
        } catch (JacksonException e) {
            log.error("Failed to serialize Redis standing live impact. fixtureId={}, season={}",
                    impact.getFixtureId(), impact.getSeason(), e);
        }
    }

    private void deleteImpact(Long fixtureId, Integer season) {
        redisTemplate.delete(liveImpactKey(fixtureId, season));
    }

    private String liveImpactKey(Long fixtureId, Integer season) {
        return LIVE_IMPACT_KEY.formatted(season, fixtureId);
    }

    private record BaselinePair(StandingBaseline home, StandingBaseline away) {
    }

    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor
    public static class StandingBaseline {
        private Integer played;
        private Integer points;
        private Integer win;
        private Integer draw;
        private Integer lose;
        private Integer goalsFor;
        private Integer goalsAgainst;
        private Integer venuePlayed;
        private Integer venueWin;
        private Integer venueDraw;
        private Integer venueLose;
        private Integer venueGoalsFor;
        private Integer venueGoalsAgainst;
        private LocalDateTime apiUpdatedAt;
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    @AllArgsConstructor
    public static class LiveStandingImpact {
        private Long fixtureId;
        private Integer season;
        private Long homeTeamId;
        private Long awayTeamId;
        private Integer homeScore;
        private Integer awayScore;
        private String statusShort;
        private Integer elapsed;
        private Integer extra;
        private Integer homePlayedBefore;
        private Integer awayPlayedBefore;
        private LocalDateTime appliedAt;
        private StandingBaseline homeBaseline;
        private StandingBaseline awayBaseline;

        public LiveStandingImpact(Long fixtureId, Integer season, Long homeTeamId, Long awayTeamId,
                                  Integer homeScore, Integer awayScore, String statusShort,
                                  Integer elapsed, Integer extra, Integer homePlayedBefore,
                                  Integer awayPlayedBefore, LocalDateTime appliedAt) {
            this(fixtureId, season, homeTeamId, awayTeamId, homeScore, awayScore, statusShort,
                    elapsed, extra, homePlayedBefore, awayPlayedBefore, appliedAt, null, null);
        }
    }
}

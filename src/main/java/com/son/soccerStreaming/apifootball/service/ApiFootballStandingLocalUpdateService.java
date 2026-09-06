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
import org.springframework.dao.DataAccessException;
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
        Set<String> keys;
        try {
            keys = redisTemplate.keys(LIVE_IMPACT_PATTERN.formatted(season));
        } catch (DataAccessException e) {
            log.warn("Redis standing impact 목록 조회 실패; 공식 DB 순위만 사용합니다. season={}", season, e);
            return List.of();
        }
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<LiveStandingImpact> impacts = new ArrayList<>();
        for (String key : keys) {
            String impactJson;
            try {
                impactJson = redisTemplate.opsForValue().get(key);
            } catch (DataAccessException e) {
                log.warn("Redis standing impact 조회 실패; 해당 impact를 건너뜁니다. key={}", key, e);
                continue;
            }
            if (impactJson == null) {
                log.warn("Redis standing impact key was found but its value could not be read. key={}", key);
                continue;
            }
            readImpact(key, impactJson).ifPresent(impacts::add);
        }
        return impacts;
    }

    public boolean hasFinishedImpacts(Integer season) {
        return findImpacts(season).stream().anyMatch(impact -> isFinished(impact.getStatusShort()));
    }

    public void reconcileFinishedImpacts(Integer league, Integer season) {
        List<LiveStandingImpact> impacts = findImpacts(season);
        log.info("Finished standing impact reconciliation started. league={}, season={}, impactCount={}",
                league, season, impacts.size());

        for (LiveStandingImpact impact : impacts) {
            log.info("Redis standing impact loaded. fixtureId={}, season={}, status={}, score={}-{}, " +
                            "homeTeamId={}, awayTeamId={}, appliedAt={}, homeBaseline=[{}], awayBaseline=[{}]",
                    impact.getFixtureId(), impact.getSeason(), impact.getStatusShort(),
                    impact.getHomeScore(), impact.getAwayScore(), impact.getHomeTeamId(), impact.getAwayTeamId(),
                    impact.getAppliedAt(), describeBaseline(impact.getHomeBaseline()),
                    describeBaseline(impact.getAwayBaseline()));

            if (!isFinished(impact.getStatusShort())) {
                log.info("Standing impact skipped because it is not finished. fixtureId={}, status={}",
                        impact.getFixtureId(), impact.getStatusShort());
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
                log.warn("Authoritative standing not found during impact reconciliation. fixtureId={}, " +
                                "homeTeamId={}, homeFound={}, awayTeamId={}, awayFound={}",
                        impact.getFixtureId(), impact.getHomeTeamId(), homeStanding.isPresent(),
                        impact.getAwayTeamId(), awayStanding.isPresent());
                continue;
            }

            StandingBaseline currentHome = toBaseline(homeStanding.get(), true);
            StandingBaseline currentAway = toBaseline(awayStanding.get(), false);
            log.info("Authoritative standings loaded for impact reconciliation. fixtureId={}, " +
                            "currentHome=[{}], currentAway=[{}]",
                    impact.getFixtureId(), describeBaseline(currentHome), describeBaseline(currentAway));

            ReflectionCheck reflectionCheck = checkReflection(impact, currentHome, currentAway);
            if (!reflectionCheck.reflected()) {
                log.warn("Finished standing impact not reflected. fixtureId={}, mismatches={}",
                        impact.getFixtureId(), String.join("; ", reflectionCheck.mismatches()));
                continue;
            }

            if (deleteImpact(impact.getFixtureId(), season)) {
                log.info("Finished standing impact removed after authoritative standings reflected fixture. " +
                        "fixtureId={}, season={}", impact.getFixtureId(), season);
            } else {
                log.warn("Finished standing impact matched but Redis key was not deleted. fixtureId={}, season={}, key={}",
                        impact.getFixtureId(), season, liveImpactKey(impact.getFixtureId(), season));
            }
        }
    }

    public boolean isReflected(LiveStandingImpact impact,
                               StandingBaseline currentHome,
                               StandingBaseline currentAway) {
        return checkReflection(impact, currentHome, currentAway).reflected();
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

    private ReflectionCheck checkReflection(LiveStandingImpact impact,
                                            StandingBaseline currentHome,
                                            StandingBaseline currentAway) {
        List<String> mismatches = new ArrayList<>();
        if (!hasDetailedBaseline(impact)) {
            mismatches.add("detailed baseline missing");
            return new ReflectionCheck(false, mismatches);
        }

        collectFixtureMismatches("home", currentHome, impact.getHomeBaseline(),
                impact.getHomeScore(), impact.getAwayScore(), mismatches);
        collectFixtureMismatches("away", currentAway, impact.getAwayBaseline(),
                impact.getAwayScore(), impact.getHomeScore(), mismatches);
        return new ReflectionCheck(mismatches.isEmpty(), mismatches);
    }

    private void collectFixtureMismatches(String side,
                                          StandingBaseline current,
                                          StandingBaseline baseline,
                                          int goalsFor,
                                          int goalsAgainst,
                                          List<String> mismatches) {
        if (current == null) {
            mismatches.add(side + ".current missing");
            return;
        }
        if (baseline == null) {
            mismatches.add(side + ".baseline missing");
            return;
        }

        if (!isUpdatedAfterBaseline(current, baseline)) {
            mismatches.add("%s.apiUpdatedAt expected>%s actual=%s".formatted(
                    side, baseline.getApiUpdatedAt(), current.getApiUpdatedAt()));
        }

        boolean win = goalsFor > goalsAgainst;
        boolean draw = goalsFor == goalsAgainst;
        boolean lose = goalsFor < goalsAgainst;
        addMismatchIfNeeded(mismatches, side + ".played", current.getPlayed(), baseline.getPlayed(), 1);
        addMismatchIfNeeded(mismatches, side + ".points", current.getPoints(), baseline.getPoints(),
                pointsFor(goalsFor, goalsAgainst));
        addMismatchIfNeeded(mismatches, side + ".win", current.getWin(), baseline.getWin(), win ? 1 : 0);
        addMismatchIfNeeded(mismatches, side + ".draw", current.getDraw(), baseline.getDraw(), draw ? 1 : 0);
        addMismatchIfNeeded(mismatches, side + ".lose", current.getLose(), baseline.getLose(), lose ? 1 : 0);
        addMismatchIfNeeded(mismatches, side + ".goalsFor", current.getGoalsFor(), baseline.getGoalsFor(), goalsFor);
        addMismatchIfNeeded(mismatches, side + ".goalsAgainst",
                current.getGoalsAgainst(), baseline.getGoalsAgainst(), goalsAgainst);
        addMismatchIfNeeded(mismatches, side + ".venuePlayed",
                current.getVenuePlayed(), baseline.getVenuePlayed(), 1);
        addMismatchIfNeeded(mismatches, side + ".venueWin",
                current.getVenueWin(), baseline.getVenueWin(), win ? 1 : 0);
        addMismatchIfNeeded(mismatches, side + ".venueDraw",
                current.getVenueDraw(), baseline.getVenueDraw(), draw ? 1 : 0);
        addMismatchIfNeeded(mismatches, side + ".venueLose",
                current.getVenueLose(), baseline.getVenueLose(), lose ? 1 : 0);
        addMismatchIfNeeded(mismatches, side + ".venueGoalsFor",
                current.getVenueGoalsFor(), baseline.getVenueGoalsFor(), goalsFor);
        addMismatchIfNeeded(mismatches, side + ".venueGoalsAgainst",
                current.getVenueGoalsAgainst(), baseline.getVenueGoalsAgainst(), goalsAgainst);
    }

    private void addMismatchIfNeeded(List<String> mismatches, String field,
                                     Integer current, Integer baseline, int delta) {
        if (!hasIncreasedBy(current, baseline, delta)) {
            Integer expected = baseline != null ? baseline + delta : null;
            mismatches.add("%s expected>=%s actual=%s".formatted(field, expected, current));
        }
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
        String key = liveImpactKey(fixtureId, season);
        try {
            return readImpact(key, redisTemplate.opsForValue().get(key));
        } catch (DataAccessException e) {
            log.warn("Redis standing impact 조회 실패; 기존 impact 없이 처리를 계속합니다. " +
                    "fixtureId={}, season={}", fixtureId, season, e);
            return Optional.empty();
        }
    }

    private Optional<LiveStandingImpact> readImpact(String key, String impactJson) {
        if (impactJson == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(impactJson, LiveStandingImpact.class));
        } catch (JacksonException e) {
            log.error("Failed to deserialize Redis standing live impact. key={}", key, e);
            deleteKeySafely(key);
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
        } catch (DataAccessException e) {
            log.warn("Redis standing impact 저장 실패; 경기 동기화를 계속합니다. fixtureId={}, season={}",
                    impact.getFixtureId(), impact.getSeason(), e);
        }
    }

    private boolean deleteImpact(Long fixtureId, Integer season) {
        String key = liveImpactKey(fixtureId, season);
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (DataAccessException e) {
            log.warn("Redis standing impact 삭제 실패; 원본 데이터 처리를 계속합니다. " +
                    "fixtureId={}, season={}", fixtureId, season, e);
            return false;
        }
    }

    private void deleteKeySafely(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("Redis standing impact 정리 실패. key={}", key, e);
        }
    }

    private String liveImpactKey(Long fixtureId, Integer season) {
        return LIVE_IMPACT_KEY.formatted(season, fixtureId);
    }

    private record BaselinePair(StandingBaseline home, StandingBaseline away) {
    }

    private record ReflectionCheck(boolean reflected, List<String> mismatches) {
    }

    private String describeBaseline(StandingBaseline baseline) {
        if (baseline == null) {
            return "null";
        }
        return ("played=%s, points=%s, win=%s, draw=%s, lose=%s, goalsFor=%s, goalsAgainst=%s, " +
                "venuePlayed=%s, venueWin=%s, venueDraw=%s, venueLose=%s, venueGoalsFor=%s, " +
                "venueGoalsAgainst=%s, apiUpdatedAt=%s").formatted(
                baseline.getPlayed(), baseline.getPoints(), baseline.getWin(), baseline.getDraw(), baseline.getLose(),
                baseline.getGoalsFor(), baseline.getGoalsAgainst(), baseline.getVenuePlayed(), baseline.getVenueWin(),
                baseline.getVenueDraw(), baseline.getVenueLose(), baseline.getVenueGoalsFor(),
                baseline.getVenueGoalsAgainst(), baseline.getApiUpdatedAt());
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

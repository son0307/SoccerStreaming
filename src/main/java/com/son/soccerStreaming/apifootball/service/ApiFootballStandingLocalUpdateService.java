package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
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
        PlayedBaseline baseline = resolvePlayedBaseline(fixture, impactLeague, impactSeason, existingImpact);

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
                baseline.homePlayed(),
                baseline.awayPlayed(),
                LocalDateTime.now()
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

            if (!hasPlayedBaseline(impact)) {
                log.warn("Removing legacy finished standing impact after authoritative sync. fixtureId={}, season={}",
                        impact.getFixtureId(), season);
                deleteImpact(impact.getFixtureId(), season);
                continue;
            }

            Optional<TeamStanding> homeStanding = findStanding(impact.getHomeTeamId(), league, season);
            Optional<TeamStanding> awayStanding = findStanding(impact.getAwayTeamId(), league, season);
            if (homeStanding.isEmpty() || awayStanding.isEmpty()) {
                continue;
            }

            if (hasPlayedMatch(homeStanding.get(), impact.getHomePlayedBefore())
                    && hasPlayedMatch(awayStanding.get(), impact.getAwayPlayedBefore())) {
                deleteImpact(impact.getFixtureId(), season);
                log.info("Finished standing impact removed after authoritative standings reflected fixture. " +
                                "fixtureId={}, season={}, homePlayed={}->{}, awayPlayed={}->{}",
                        impact.getFixtureId(), season,
                        impact.getHomePlayedBefore(), homeStanding.get().getPlayed(),
                        impact.getAwayPlayedBefore(), awayStanding.get().getPlayed());
            }
        }
    }

    public boolean isReflected(LiveStandingImpact impact, Integer homePlayed, Integer awayPlayed) {
        return hasPlayedBaseline(impact)
                && valueOf(homePlayed) >= impact.getHomePlayedBefore() + 1
                && valueOf(awayPlayed) >= impact.getAwayPlayedBefore() + 1;
    }

    public boolean isLiveImpact(LiveStandingImpact impact) {
        return isLive(impact.getStatusShort());
    }

    private boolean isEnabledForStatus(String statusShort) {
        return (isLive(statusShort) && localLiveUpdateEnabled)
                || (isFinished(statusShort) && localFinishedUpdateEnabled);
    }

    private PlayedBaseline resolvePlayedBaseline(Fixture fixture, Integer league, Integer season,
                                                 Optional<LiveStandingImpact> existingImpact) {
        if (existingImpact.isPresent() && hasPlayedBaseline(existingImpact.get())) {
            LiveStandingImpact impact = existingImpact.get();
            return new PlayedBaseline(impact.getHomePlayedBefore(), impact.getAwayPlayedBefore());
        }

        Integer homePlayed = findStanding(fixture.getHomeTeam().getTeamId(), league, season)
                .map(standing -> valueOf(standing.getPlayed()))
                .orElse(null);
        Integer awayPlayed = findStanding(fixture.getAwayTeam().getTeamId(), league, season)
                .map(standing -> valueOf(standing.getPlayed()))
                .orElse(null);
        return new PlayedBaseline(homePlayed, awayPlayed);
    }

    private Optional<TeamStanding> findStanding(Long teamId, Integer league, Integer season) {
        return teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(teamId, league, season);
    }

    private boolean hasPlayedMatch(TeamStanding standing, Integer playedBefore) {
        return valueOf(standing.getPlayed()) >= playedBefore + 1;
    }

    private boolean hasPlayedBaseline(LiveStandingImpact impact) {
        return impact.getHomePlayedBefore() != null && impact.getAwayPlayedBefore() != null;
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

    private record PlayedBaseline(Integer homePlayed, Integer awayPlayed) {
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
    }
}

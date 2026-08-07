package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballFixtureStatisticsDto;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureStat;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.FixtureStatRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixtureStatSyncService {

    private final ApiFootballClient apiFootballClient;
    private final FixtureRepository fixtureRepository;
    private final FixtureStatRepository fixtureStatRepository;
    private final TeamRepository teamRepository;
    private final AdminOverrideService adminOverrideService;
    private static final List<String> OVERRIDE_FIELDS = List.of(
            "shotsOnGoal", "shotsOffGoal", "totalShots", "blockedShots", "shotsInsideBox",
            "shotsOutsideBox", "fouls", "cornerKicks", "offsides", "ballPossession",
            "yellowCards", "redCards", "goalkeeperSaves", "totalPasses", "passesAccurate",
            "passAccuracy", "expectedGoals"
    );

    @Transactional
    @CacheEvict(
            cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
            cacheNames = RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE,
            allEntries = true
    )
    public int syncFixtureStats(Long fixtureId) {
        Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        List<ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse> teamStats =
                apiFootballClient.getFixtureStatistics(fixtureId);
        return syncFixtureStats(fixture, teamStats);
    }

    @Transactional
    @CacheEvict(
            cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
            cacheNames = RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE,
            allEntries = true
    )
    public int syncFixtureStats(Long fixtureId, List<ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse> teamStats) {
        Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        return syncFixtureStats(fixture, teamStats);
    }

    @Transactional
    @CacheEvict(
            cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
            cacheNames = RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE,
            allEntries = true
    )
    public int syncFixtureStats(Fixture fixture, List<ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse> teamStats) {
        if (teamStats == null || teamStats.isEmpty()) {
            return 0;
        }

        int syncedCount = 0;

        for (ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse teamStat : teamStats) {
            Optional<Team> team = findTeam(teamStat);
            if (team.isEmpty()) {
                log.warn("Skip fixture stat because team does not exist. fixtureId={}, teamId={}",
                        fixture.getFixtureId(), teamStat.getTeam() != null ? teamStat.getTeam().getId() : null);
                continue;
            }

            upsertFixtureStat(fixture, team.get(), teamStat.getStatistics());
            syncedCount++;
        }

        return syncedCount;
    }

    @Transactional
    @CacheEvict(
            cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
            cacheNames = RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE,
            allEntries = true
    )
    public int syncFixtureStats(List<Fixture> fixtures) {
        int syncedCount = 0;
        for (Fixture fixture : fixtures) {
            try {
                syncedCount += syncFixtureStats(fixture.getFixtureId());
            } catch (Exception e) {
                log.error("API-Football fixture stat sync failed. fixtureId={}", fixture.getFixtureId(), e);
            }
        }
        return syncedCount;
    }

    private Optional<Team> findTeam(ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse teamStat) {
        if (teamStat == null || teamStat.getTeam() == null || teamStat.getTeam().getId() == null) {
            return Optional.empty();
        }
        return teamRepository.findByTeamId(teamStat.getTeam().getId());
    }

    private void upsertFixtureStat(Fixture fixture, Team team,
                                   List<ApiFootballFixtureStatisticsDto.Statistic> statistics) {
        Map<String, Object> values = valuesByType(statistics);

        FixtureStat entity = fixtureStatRepository
                .findByFixtureFixtureIdAndTeamTeamId(fixture.getFixtureId(), team.getTeamId())
                .orElseGet(() -> fixtureStatRepository.save(FixtureStat.builder()
                        .fixture(fixture)
                        .team(team)
                        .build()));

        Set<String> overrides = adminOverrideService.overriddenFields(
                AdminOverrideTargetType.FIXTURE_TEAM_STAT,
                entity.getId(),
                OVERRIDE_FIELDS
        );

        entity.updateStats(
                adminOverrideService.apiValueUnlessOverridden(overrides, "shotsOnGoal", entity.getShotsOnGoal(), parseInteger(values.get("shots on goal"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "shotsOffGoal", entity.getShotsOffGoal(), parseInteger(values.get("shots off goal"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "totalShots", entity.getTotalShots(), parseInteger(values.get("total shots"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "blockedShots", entity.getBlockedShots(), parseInteger(values.get("blocked shots"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "shotsInsideBox", entity.getShotsInsideBox(), parseInteger(values.get("shots insidebox"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "shotsOutsideBox", entity.getShotsOutsideBox(), parseInteger(values.get("shots outsidebox"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "fouls", entity.getFouls(), parseInteger(values.get("fouls"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "cornerKicks", entity.getCornerKicks(), parseInteger(values.get("corner kicks"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "offsides", entity.getOffsides(), parseInteger(values.get("offsides"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "ballPossession", entity.getBallPossession(), parseInteger(values.get("ball possession"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "yellowCards", entity.getYellowCards(), parseInteger(values.get("yellow cards"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "redCards", entity.getRedCards(), parseInteger(values.get("red cards"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "goalkeeperSaves", entity.getGoalkeeperSaves(), parseInteger(values.get("goalkeeper saves"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "totalPasses", entity.getTotalPasses(), parseInteger(values.get("total passes"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "passesAccurate", entity.getPassesAccurate(), parseInteger(values.get("passes accurate"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "passAccuracy", entity.getPassAccuracy(), parseInteger(values.get("passes %"))),
                adminOverrideService.apiValueUnlessOverridden(overrides, "expectedGoals", entity.getExpectedGoals(), parseDouble(values.get("expected_goals")))
        );
    }

    private Map<String, Object> valuesByType(List<ApiFootballFixtureStatisticsDto.Statistic> statistics) {
        if (statistics == null) {
            return Map.of();
        }
        return statistics.stream()
                .filter(stat -> stat.getType() != null && stat.getValue() != null)
                .collect(Collectors.toMap(
                        stat -> stat.getType().toLowerCase(Locale.ROOT),
                        ApiFootballFixtureStatisticsDto.Statistic::getValue,
                        (left, right) -> right
                ));
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        String digits = String.valueOf(value).replaceAll("[^0-9-]", "");
        return digits.isBlank() ? null : Integer.parseInt(digits);
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        if (text.isBlank()) {
            return null;
        }
        return Double.parseDouble(text);
    }
}

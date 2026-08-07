package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballFixturePlayerStatSyncService {

    private final ApiFootballClient apiFootballClient;
    private final FixtureRepository fixtureRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final TeamRepository teamRepository;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final AdminOverrideService adminOverrideService;
    private static final List<String> OVERRIDE_FIELDS = List.of(
            "minutesPlayed", "rating", "captain", "substitute", "goals", "assists", "conceded",
            "saves", "shotsTotal", "shotsOnTarget", "passesTotal", "passesKey", "passesAccurate",
            "passAccuracy", "tacklesTotal", "blocks", "interceptions", "duelsTotal", "duelsWon",
            "dribblesAttempts", "dribblesSuccess", "dribblesPast", "foulsDrawn", "foulsCommitted",
            "yellowCards", "redCards", "offsides", "penaltyWon", "penaltyCommitted", "penaltyScored",
            "penaltyMissed", "penaltySaved"
    );

    @Caching(evict = {
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE,
                    allEntries = true
            ),
            @CacheEvict(cacheNames = RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE, allEntries = true),
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE,
                    allEntries = true
            )
    })
    @Transactional
    public int syncPlayerStats(Long fixtureId) {
        Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        List<ApiFootballLiveDto.FixturePlayersResponse> teamStats = apiFootballClient.getPlayerStats(fixtureId);
        return syncPlayerStats(fixture, teamStats);
    }

    @Caching(evict = {
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE,
                    allEntries = true
            ),
            @CacheEvict(cacheNames = RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE, allEntries = true),
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE,
                    allEntries = true
            )
    })
    @Transactional
    public int syncPlayerStats(Long fixtureId, List<ApiFootballLiveDto.FixturePlayersResponse> teamStats) {
        Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        return syncPlayerStats(fixture, teamStats);
    }

    @Caching(evict = {
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE,
                    allEntries = true
            ),
            @CacheEvict(cacheNames = RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE, allEntries = true),
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE,
                    allEntries = true
            )
    })
    @Transactional
    public int syncPlayerStats(Fixture fixture, List<ApiFootballLiveDto.FixturePlayersResponse> teamStats) {
        if (teamStats == null || teamStats.isEmpty()) {
            return 0;
        }

        int syncedCount = 0;

        for (ApiFootballLiveDto.FixturePlayersResponse teamStat : teamStats) {
            Optional<Team> team = findTeam(teamStat.getTeam());
            if (team.isEmpty() || teamStat.getPlayers() == null) {
                continue;
            }

            for (ApiFootballLiveDto.PlayerStatResponse playerStat : teamStat.getPlayers()) {
                if (upsertPlayerStat(fixture, team.get(), playerStat)) {
                    syncedCount++;
                }
            }
        }

        return syncedCount;
    }

    @Caching(evict = {
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.TEAM_PLAYER_RANKINGS_CACHE,
                    allEntries = true
            ),
            @CacheEvict(cacheNames = RedisCacheConfig.FAVORITE_PLAYER_CARD_CACHE, allEntries = true),
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.LEAGUE_PLAYER_RANKINGS_CACHE,
                    allEntries = true
            )
    })
    @Transactional
    public int syncPlayerStats(List<Fixture> fixtures) {
        int syncedCount = 0;
        for (Fixture fixture : fixtures) {
            try {
                syncedCount += syncPlayerStats(fixture.getFixtureId());
            } catch (Exception e) {
                log.error("API-Football fixture player stat sync failed. fixtureId={}", fixture.getFixtureId(), e);
            }
        }
        return syncedCount;
    }

    private boolean upsertPlayerStat(Fixture fixture, Team team, ApiFootballLiveDto.PlayerStatResponse playerStat) {
        Optional<Player> player = findPlayer(playerStat.getPlayer(), team);
        if (player.isEmpty()) {
            log.warn("Skip fixture player stat because player does not exist. fixtureId={}, playerId={}",
                    fixture.getFixtureId(), playerStat.getPlayer() != null ? playerStat.getPlayer().getId() : null);
            return false;
        }

        ApiFootballLiveDto.PlayerStatistics stat = firstStat(playerStat);
        if (stat == null) {
            return false;
        }

        PlayerFixtureStat entity = playerFixtureStatRepository
                .findByFixtureFixtureIdAndPlayerPlayerId(fixture.getFixtureId(), player.get().getPlayerId())
                .orElseGet(() -> playerFixtureStatRepository.save(PlayerFixtureStat.builder()
                        .fixture(fixture)
                        .team(team)
                        .player(player.get())
                        .build()));

        updatePlayerStat(entity, stat);
        return true;
    }

    private void updatePlayerStat(PlayerFixtureStat entity, ApiFootballLiveDto.PlayerStatistics stat) {
        ApiFootballLiveDto.Games games = stat.getGames();
        ApiFootballLiveDto.GoalsStat goals = stat.getGoals();
        ApiFootballLiveDto.Shots shots = stat.getShots();
        ApiFootballLiveDto.Passes passes = stat.getPasses();
        ApiFootballLiveDto.Tackles tackles = stat.getTackles();
        ApiFootballLiveDto.Duels duels = stat.getDuels();
        ApiFootballLiveDto.Dribbles dribbles = stat.getDribbles();
        ApiFootballLiveDto.Fouls fouls = stat.getFouls();
        ApiFootballLiveDto.Cards cards = stat.getCards();
        ApiFootballLiveDto.Penalty penalty = stat.getPenalty();
        Set<String> overrides = adminOverrideService.overriddenFields(
                AdminOverrideTargetType.FIXTURE_PLAYER_STAT,
                entity.getId(),
                OVERRIDE_FIELDS
        );
        Integer minutesPlayed = adminOverrideService.apiValueUnlessOverridden(
                overrides, "minutesPlayed", entity.getMinutesPlayed(),
                PlayerFixtureStat.normalizeMinutesPlayed(games != null ? games.getMinutes() : null)
        );
        Integer redCards = adminOverrideService.apiValueUnlessOverridden(
                overrides, "redCards", entity.getRedCards(), cards != null ? cards.getRed() : null
        );
        Integer goalsValue = overrides.contains("goals")
                ? entity.getGoals()
                : PlayerFixtureStat.normalizeScoringStat(minutesPlayed, goals != null ? goals.getTotal() : null);
        Integer assistsValue = overrides.contains("assists")
                ? entity.getAssists()
                : PlayerFixtureStat.normalizeScoringStat(minutesPlayed, goals != null ? goals.getAssists() : null);
        Integer yellowCards = overrides.contains("yellowCards")
                ? entity.getYellowCards()
                : PlayerFixtureStat.normalizeYellowCards(cards != null ? cards.getYellow() : null, redCards);
        Integer passesTotal = adminOverrideService.apiValueUnlessOverridden(
                overrides, "passesTotal", entity.getPassesTotal(), passes != null ? passes.getTotal() : null
        );
        Integer passesAccurate = adminOverrideService.apiValueUnlessOverridden(
                overrides, "passesAccurate", entity.getPassesAccurate(),
                passes != null ? parseInteger(passes.getAccuracy()) : null
        );
        Integer passAccuracy = adminOverrideService.apiValueUnlessOverridden(
                overrides, "passAccuracy", entity.getPassAccuracy(), passAccuracyOf(passesAccurate, passesTotal)
        );

        entity.updateStatValues(
                minutesPlayed,
                adminOverrideService.apiValueUnlessOverridden(overrides, "rating", entity.getRating(), games != null ? parseDouble(games.getRating()) : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "captain", entity.getIsCaptain(), games != null ? games.getCaptain() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "substitute", entity.getIsSubstitute(), games != null ? games.getSubstitute() : null),
                goalsValue,
                assistsValue,
                adminOverrideService.apiValueUnlessOverridden(overrides, "conceded", entity.getConceded(), goals != null ? goals.getConceded() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "saves", entity.getSaves(), goals != null ? goals.getSaves() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "shotsTotal", entity.getShotsTotal(), shots != null ? shots.getTotal() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "shotsOnTarget", entity.getShotsOnTarget(), shots != null ? shots.getOn() : null),
                passesTotal,
                adminOverrideService.apiValueUnlessOverridden(overrides, "passesKey", entity.getPassesKey(), passes != null ? passes.getKey() : null),
                passesAccurate,
                passAccuracy,
                adminOverrideService.apiValueUnlessOverridden(overrides, "tacklesTotal", entity.getTacklesTotal(), tackles != null ? tackles.getTotal() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "blocks", entity.getBlocks(), tackles != null ? tackles.getBlocks() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "interceptions", entity.getInterceptions(), tackles != null ? tackles.getInterceptions() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "duelsTotal", entity.getDuelsTotal(), duels != null ? duels.getTotal() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "duelsWon", entity.getDuelsWon(), duels != null ? duels.getWon() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "dribblesAttempts", entity.getDribblesAttempts(), dribbles != null ? dribbles.getAttempts() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "dribblesSuccess", entity.getDribblesSuccess(), dribbles != null ? dribbles.getSuccess() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "dribblesPast", entity.getDribblesPast(), dribbles != null ? dribbles.getPast() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "foulsDrawn", entity.getFoulsDrawn(), fouls != null ? fouls.getDrawn() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "foulsCommitted", entity.getFoulsCommitted(), fouls != null ? fouls.getCommitted() : null),
                yellowCards,
                redCards,
                adminOverrideService.apiValueUnlessOverridden(overrides, "offsides", entity.getOffsides(), stat.getOffsides()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "penaltyWon", entity.getPenaltyWon(), penalty != null ? penalty.getWon() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "penaltyCommitted", entity.getPenaltyCommitted(), penalty != null ? penalty.getCommited() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "penaltyScored", entity.getPenaltyScored(), penalty != null ? penalty.getScored() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "penaltyMissed", entity.getPenaltyMissed(), penalty != null ? penalty.getMissed() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "penaltySaved", entity.getPenaltySaved(), penalty != null ? penalty.getSaved() : null)
        );
    }

    private Optional<Team> findTeam(ApiFootballLiveDto.TeamInfo team) {
        if (team == null || team.getId() == null) {
            return Optional.empty();
        }
        return teamRepository.findByTeamId(team.getId());
    }

    private Optional<Player> findPlayer(ApiFootballLiveDto.PlayerInfo player, Team team) {
        if (player == null || player.getId() == null) {
            return Optional.empty();
        }
        return apiFootballPlayerSyncService.findOrFetchPlayer(
                player.getId(),
                player.getName(),
                team,
                null,
                null,
                player.getPhoto()
        );
    }

    private ApiFootballLiveDto.PlayerStatistics firstStat(ApiFootballLiveDto.PlayerStatResponse playerStat) {
        if (playerStat.getStatistics() == null || playerStat.getStatistics().isEmpty()) {
            return null;
        }
        return playerStat.getStatistics().get(0);
    }

    private Double parseDouble(String value) {
        if (value == null || value.isBlank() || value.equals("–")) {
            return null;
        }
        return Double.parseDouble(value);
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("[^0-9-]", "");
        return digits.isBlank() ? null : Integer.parseInt(digits);
    }

    private Integer passAccuracyOf(Integer passesAccurate, Integer passesTotal) {
        if (passesAccurate == null || passesTotal == null || passesTotal <= 0) {
            return null;
        }
        return (int) Math.round((passesAccurate * 100.0) / passesTotal);
    }
}

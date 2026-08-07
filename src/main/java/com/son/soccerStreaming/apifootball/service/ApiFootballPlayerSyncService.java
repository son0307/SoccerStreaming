package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballPlayerDto;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.media.service.ImageCacheService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.service.PlayerTeamSeasonStatAggregationService;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballPlayerSyncService {

    private final ApiFootballClient apiFootballClient;
    private final FixtureLineupRepository fixtureLineupRepository;
    private final PlayerRepository playerRepository;
    private final PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    private final TeamRepository teamRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final AdminOverrideService adminOverrideService;
    private final OptimisticLockRetryExecutor optimisticLockRetryExecutor;
    private final EntityManager entityManager;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;
    private final ImageCacheService imageCacheService;
    private final PlayerTeamSeasonStatAggregationService playerTeamSeasonStatAggregationService;
    private static final List<String> PROFILE_OVERRIDE_FIELDS = List.of(
            "name", "firstname", "lastname", "age", "birthDate", "birthPlace", "birthCountry",
            "nationality", "height", "weight", "position", "number", "photoUrl"
    );

    @Value("${api-football.sync.players.profile-fallback-enabled:false}")
    private boolean profileFallbackEnabled;

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
    public int syncRegisteredPlayers(Integer league, Integer season, Long delayMs) {
        return syncRegisteredPlayers(league, season, delayMs, SyncProgressReporter.NO_OP);
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
    public int syncRegisteredPlayers(Integer league, Integer season, Long delayMs,
                                     SyncProgressReporter progressReporter) {
        apiFootballSyncStatusService.recordAttempt("players", "Players", season);
        int syncedCount = 0;
        List<Long> failedTeamIds = new java.util.ArrayList<>();
        Exception firstFailure = null;
        Set<Long> syncedPlayerIds = new LinkedHashSet<>();
        List<Team> teams = seasonTeams(league, season);
        int processedTeams = 0;
        int successfulTeams = 0;
        progressReporter.beginPhase("SYNCING_PLAYERS", teams.size(), "teams", 0);
        for (Team team : teams) {
            progressReporter.checkCancelled();
            try {
                RegisteredPlayerSyncResult result = syncRegisteredPlayersByTeamInternal(
                        team, league, season, delayMs, progressReporter);
                syncedCount += result.syncedCount();
                syncedPlayerIds.addAll(result.playerIds());
                successfulTeams++;
            } catch (SyncCancelledException exception) {
                throw exception;
            } catch (Exception e) {
                if (firstFailure == null) {
                    firstFailure = e;
                }
                failedTeamIds.add(team.getTeamId());
                progressReporter.error("TEAM", String.valueOf(team.getTeamId()), e.getMessage());
                log.error("API-Football registered players team sync failed. teamId={}, season={}",
                        team.getTeamId(), season, e);
            }
            processedTeams++;
            progressReporter.update(processedTeams, successfulTeams, failedTeamIds.size(), syncedCount);
        }

        if (!failedTeamIds.isEmpty()) {
            throw new ApiFootballRegisteredPlayerSyncException(failedTeamIds, firstFailure);
        }

        progressReporter.checkCancelled();
        progressReporter.beginPhase("CACHING_IMAGES", syncedPlayerIds.size(), "images", syncedCount);
        imageCacheService.cachePlayerPhotos(syncedPlayerIds, progressReporter, syncedCount);
        progressReporter.checkCancelled();
        progressReporter.beginPhase("REBUILDING_SEASON_STATS", 0, "season", syncedCount);
        playerTeamSeasonStatAggregationService.rebuildSeason(league, season);
        progressReporter.beginPhase("REBUILDING_SEASON_STATS", 1, "season", syncedCount);
        progressReporter.update(1, 1, 0, syncedCount);
        progressReporter.checkCancelled();
        log.info("API-Football registered players sync completed. league={}, season={}, count={}",
                league, season, syncedCount);
        apiFootballSyncStatusService.recordSuccess("players", "Players", season);
        return syncedCount;
    }

    public int syncRegisteredPlayersByTeamId(Long teamId, Integer league, Integer season, Long delayMs) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found. teamId=" + teamId));
        return syncRegisteredPlayersByTeam(team, league, season, delayMs);
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
    public int syncRegisteredPlayersByTeam(Team team, Integer league, Integer season, Long delayMs) {
        RegisteredPlayerSyncResult result = syncRegisteredPlayersByTeamInternal(
                team, league, season, delayMs, SyncProgressReporter.NO_OP);
        imageCacheService.cachePlayerPhotos(result.playerIds());
        return result.syncedCount();
    }

    private RegisteredPlayerSyncResult syncRegisteredPlayersByTeamInternal(
            Team team, Integer league, Integer season, Long delayMs, SyncProgressReporter progressReporter) {
        int page = 1;
        int totalPages = 1;
        int syncedCount = 0;
        Set<Long> syncedPlayerIds = new LinkedHashSet<>();

        do {
            progressReporter.checkCancelled();
            ApiFootballPlayerDto.ApiResponse<ApiFootballPlayerDto.RegisteredPlayerResponse> response =
                    apiFootballClient.getRegisteredPlayersByTeam(team.getTeamId(), league, season, page == 1 ? null : page);

            List<ApiFootballPlayerDto.RegisteredPlayerResponse> players = response.getResponse() != null
                    ? response.getResponse()
                    : List.of();
            RegisteredPlayerPageSyncResult pageResult = syncRegisteredPlayerPage(players, team.getTeamId(), league, season);
            syncedCount += pageResult.syncedCount();
            syncedPlayerIds.addAll(pageResult.playerIds());

            totalPages = response.getPaging() != null && response.getPaging().getTotal() != null
                    ? response.getPaging().getTotal()
                    : page;

            log.debug("API-Football registered players page synced. teamId={}, season={}, page={}/{}, count={}",
                    team.getTeamId(), season, page, totalPages, players.size());
            page++;
        } while (page <= totalPages);

        log.info("API-Football registered players team sync completed. teamId={}, season={}, count={}",
                team.getTeamId(), season, syncedCount);
        return new RegisteredPlayerSyncResult(syncedCount, syncedPlayerIds);
    }

    private List<Team> seasonTeams(Integer league, Integer season) {
        List<Team> standingTeams = teamStandingRepository.findAllByLeagueIdAndSeason(league, season).stream()
                .map(standing -> standing.getTeam())
                .distinct()
                .sorted(Comparator.comparing(Team::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        if (standingTeams.isEmpty()) {
            throw new IllegalStateException(
                    "Team standings must be synchronized before player sync. league=" + league + "; season=" + season);
        }
        return standingTeams;
    }

    private RegisteredPlayerPageSyncResult syncRegisteredPlayerPage(List<ApiFootballPlayerDto.RegisteredPlayerResponse> players,
                                                                    Long requestedTeamId,
                                                                    Integer league,
                                                                    Integer season) {
        return optimisticLockRetryExecutor.execute(
                "registered-players:team=%s;season=%s".formatted(requestedTeamId, season),
                () -> {
                    Team managedTeam = teamRepository.findByTeamId(requestedTeamId).orElse(null);
                    if (managedTeam == null) {
                        log.warn("Skip registered players page because team does not exist. teamId={}", requestedTeamId);
                        return new RegisteredPlayerPageSyncResult(0, Set.of());
                    }

                    int syncedCount = 0;
                    Set<Long> playerIds = new LinkedHashSet<>();
                    for (ApiFootballPlayerDto.RegisteredPlayerResponse playerResponse : players) {
                        Optional<Long> playerId = upsertRegisteredPlayer(
                                playerResponse, managedTeam, league, season);
                        if (playerId.isPresent()) {
                            syncedCount++;
                            playerIds.add(playerId.get());
                        }
                    }
                    // Bulk admin sync can run for a long time, so release managed entities after each API page.
                    entityManager.flush();
                    entityManager.clear();
                    return new RegisteredPlayerPageSyncResult(syncedCount, playerIds);
                }
        );
    }

    @Transactional
    public Optional<Long> upsertRegisteredPlayer(
            ApiFootballPlayerDto.RegisteredPlayerResponse playerResponse,
            Team requestedTeam,
            Integer requestedLeague,
            Integer requestedSeason
    ) {
        if (playerResponse == null || playerResponse.getPlayer() == null
                || playerResponse.getPlayer().getId() == null) {
            return Optional.empty();
        }

        ApiFootballPlayerDto.PlayerStatistics statistics = statisticsForTeam(
                playerResponse.getStatistics(),
                requestedTeam,
                requestedLeague,
                requestedSeason
        )
                .orElse(null);
        ApiFootballPlayerDto.Games games = statistics != null ? statistics.getGames() : null;

        Player player = upsertProfilePlayer(
                playerResponse.getPlayer(),
                games != null ? games.getNumber() : null,
                games != null ? games.getPosition() : null
        );
        return Optional.of(player.getPlayerId());
    }

    @Transactional
    public Optional<Player> findOrFetchPlayer(Long playerId, String fallbackName, Team team,
                                              Integer number, String position, String photoUrl) {
        if (playerId == null) {
            return Optional.empty();
        }

        Optional<Player> existing = playerRepository.findByPlayerId(playerId);
        if (existing.isPresent()) {
            return existing;
        }

        if (profileFallbackEnabled) {
            Optional<Player> fetched = fetchProfile(playerId, number, position);
            if (fetched.isPresent()) {
                return fetched;
            }
        }

        if (fallbackName == null || fallbackName.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(saveMinimalPlayer(playerId, fallbackName, number, position, photoUrl));
    }

    @Transactional
    public void updateLineupProfileIfLatest(Player player, Fixture fixture, Integer number, String position) {
        if (player == null || fixture == null || fixture.getFixtureDate() == null) {
            return;
        }
        if (number == null && (position == null || position.isBlank())) {
            return;
        }

        boolean latestLineup = fixtureLineupRepository.findLatestFixtureDateByPlayerId(player.getPlayerId())
                .map(latestFixtureDate -> !latestFixtureDate.isAfter(fixture.getFixtureDate()))
                .orElse(true);

        if (latestLineup) {
            player.updateNumber(number);
            playerRepository.save(player);
        }
    }

    @Transactional
    public void updateSeasonBackNumberFromLineup(Player player, Team team, Fixture fixture, Integer number) {
        if (player == null || team == null || fixture == null || fixture.getSeason() == null || number == null) {
            return;
        }

        // A lineup number belongs to the player's team-season row, not only to the latest player profile.
        playerTeamSeasonStatRepository.findAllByPlayerPlayerIdAndTeamTeamIdAndSeason(
                        player.getPlayerId(),
                        team.getTeamId(),
                        fixture.getSeason()
                )
                .forEach(stat -> stat.updateBackNumberFromLineup(number));
    }

    @Transactional
    public Optional<Player> fetchProfile(Long playerId, Integer number, String position) {
        return apiFootballClient.getPlayerProfiles(playerId).stream()
                .map(ApiFootballPlayerDto.ProfileResponse::getPlayer)
                .filter(player -> player != null && player.getId() != null)
                .findFirst()
                .map(player -> upsertProfilePlayer(player, number, position));
    }

    private Player upsertProfilePlayer(ApiFootballPlayerDto.ProfilePlayer playerInfo, Integer number, String position) {
        Player player = playerRepository.findByPlayerId(playerInfo.getId())
                .orElseGet(() -> Player.builder()
                        .playerId(playerInfo.getId())
                        .name(nameOrFallback(playerInfo.getName(), playerInfo.getId()))
                        .build());

        ApiFootballPlayerDto.Birth birth = playerInfo.getBirth();
        Set<String> overrides = adminOverrideService.overriddenFields(
                AdminOverrideTargetType.PLAYER,
                playerInfo.getId(),
                PROFILE_OVERRIDE_FIELDS
        );
        player.updateProfile(
                adminOverrideService.apiValueUnlessOverridden(overrides, "name", player.getName(), nameOrFallback(normalizeName(playerInfo.getName()), player.getName())),
                adminOverrideService.apiValueUnlessOverridden(overrides, "firstname", player.getFirstname(), normalizeName(playerInfo.getFirstname())),
                adminOverrideService.apiValueUnlessOverridden(overrides, "lastname", player.getLastname(), normalizeName(playerInfo.getLastname())),
                adminOverrideService.apiValueUnlessOverridden(overrides, "age", player.getAge(), playerInfo.getAge()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "birthDate", player.getBirthDate(), birth != null ? parseBirthDate(birth.getDate()) : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "birthPlace", player.getBirthPlace(), birth != null ? birth.getPlace() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "birthCountry", player.getBirthCountry(), birth != null ? birth.getCountry() : null),
                adminOverrideService.apiValueUnlessOverridden(overrides, "nationality", player.getNationality(), playerInfo.getNationality()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "height", player.getHeight(), numericPrefix(playerInfo.getHeight())),
                adminOverrideService.apiValueUnlessOverridden(overrides, "weight", player.getWeight(), numericPrefix(playerInfo.getWeight())),
                adminOverrideService.apiValueUnlessOverridden(overrides, "position", player.getPosition(), position),
                adminOverrideService.apiValueUnlessOverridden(overrides, "number", player.getNumber(), number != null ? number : player.getNumber()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "photoUrl", player.getPhotoUrl(), playerInfo.getPhoto())
        );
        return playerRepository.save(player);
    }

    private Optional<ApiFootballPlayerDto.PlayerStatistics> statisticsForTeam(
            List<ApiFootballPlayerDto.PlayerStatistics> statistics,
            Team team,
            Integer league,
            Integer season
    ) {
        if (statistics == null || statistics.isEmpty()) {
            return Optional.empty();
        }

        return statistics.stream()
                .filter(stat -> stat.getTeam() != null && team.getTeamId().equals(stat.getTeam().getId()))
                .filter(stat -> stat.getLeague() != null
                        && league != null
                        && league.longValue() == stat.getLeague().getId())
                .filter(stat -> season != null && season.equals(stat.getLeague().getSeason()))
                .findFirst();
    }


    private Player saveMinimalPlayer(Long playerId, String name, Integer number, String position, String photoUrl) {
        Player player = Player.builder()
                .playerId(playerId)
                .name(normalizeName(name))
                .number(number)
                .position(position)
                .photoUrl(photoUrl)
                .build();
        return playerRepository.save(player);
    }

    private record RegisteredPlayerSyncResult(int syncedCount, Set<Long> playerIds) {
    }

    private record RegisteredPlayerPageSyncResult(int syncedCount, Set<Long> playerIds) {
    }

    private String nameOrFallback(String name, Long playerId) {
        String normalizedName = normalizeName(name);
        return normalizedName != null && !normalizedName.isBlank() ? normalizedName : "Player " + playerId;
    }

    private String nameOrFallback(String name, String fallback) {
        return name != null && !name.isBlank() ? name : fallback;
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        // Spring HtmlUtils follows the HTML 4 entity set, which does not include &apos;.
        return HtmlUtils.htmlUnescape(value.replace("&apos;", "'"));
    }

    private LocalDate parseBirthDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse API-Football player birth date. date={}", date);
            return null;
        }
    }

    private Integer numericPrefix(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String firstToken = value.trim().split("\\s+")[0];
        try {
            return Integer.parseInt(firstToken);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sleepBetweenPages(Long delayMs, int nextPage, int totalPages) {
        if (nextPage > totalPages || delayMs == null || delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

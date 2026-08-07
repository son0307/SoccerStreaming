package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballInjurySyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballTeamSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncAlreadyRunningException;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.service.LeagueSeasonCoverageSyncService;
import com.son.soccerStreaming.admin.dto.AdminDto;
import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.entity.AdminAuditType;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureEvent;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.fixture.entity.FixtureStat;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.fixture.repository.FixtureEventRepository;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.FixtureStatRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.fixture.service.FixtureRedisService;
import com.son.soccerStreaming.league.entity.LeagueSeasonCoverage;
import com.son.soccerStreaming.league.repository.LeagueSeasonCoverageRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.apifootball.repository.ApiFootballSyncStatusRepository;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.cache.annotation.CacheEvict;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private static final int ADMIN_SEARCH_KEYWORD_MAX_LENGTH = 80;
    private static final Duration MANUAL_SYNC_COOLDOWN = Duration.ofSeconds(30);

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> TEAM_OVERRIDE_FIELDS = Set.of(
            "name",
            "koreanName",
            "code",
            "country",
            "founded",
            "logoUrl",
            "venueId",
            "venueName",
            "venueNameKo",
            "venueAddress",
            "venueCity",
            "capacity",
            "surface",
            "venueImageUrl"
    );
    private static final Set<String> PLAYER_OVERRIDE_FIELDS = Set.of(
            "name",
            "koreanName",
            "firstname",
            "lastname",
            "age",
            "birthDate",
            "birthPlace",
            "birthCountry",
            "nationality",
            "height",
            "weight",
            "position",
            "number",
            "photoUrl"
    );
    private static final Set<String> EVENT_TYPES = Set.of("Goal", "Card", "Subst", "Var");
    private static final String FIXTURE_EVENTS_OVERRIDE_FIELD = "events";
    private static final Set<String> FIXTURE_EVENT_OVERRIDE_FIELDS = Set.of(FIXTURE_EVENTS_OVERRIDE_FIELD);
    private static final Set<String> FIXTURE_OVERRIDE_FIELDS = Set.of(
            "fixtureDate", "referee", "venueId", "venueName", "venueNameKo", "venueCity",
            "homeFormation", "awayFormation", "homeCoachName", "awayCoachName",
            "homePlayerColorPrimary", "homePlayerColorNumber", "homePlayerColorBorder",
            "homeGoalkeeperColorPrimary", "homeGoalkeeperColorNumber", "homeGoalkeeperColorBorder",
            "awayPlayerColorPrimary", "awayPlayerColorNumber", "awayPlayerColorBorder",
            "awayGoalkeeperColorPrimary", "awayGoalkeeperColorNumber", "awayGoalkeeperColorBorder"
    );
    private static final Set<String> FIXTURE_LINEUP_OVERRIDE_FIELDS = Set.of(
            "jerseyNumber", "position", "grid", "starter"
    );
    private static final Set<String> FIXTURE_TEAM_STAT_OVERRIDE_FIELDS = Set.of(
            "shotsOnGoal", "shotsOffGoal", "totalShots", "blockedShots", "shotsInsideBox",
            "shotsOutsideBox", "fouls", "cornerKicks", "offsides", "ballPossession",
            "yellowCards", "redCards", "goalkeeperSaves", "totalPasses", "passesAccurate",
            "passAccuracy", "expectedGoals"
    );
    private static final Set<String> FIXTURE_PLAYER_STAT_OVERRIDE_FIELDS = Set.of(
            "minutesPlayed", "rating", "captain", "substitute", "goals", "assists", "conceded",
            "saves", "shotsTotal", "shotsOnTarget", "passesTotal", "passesKey", "passesAccurate",
            "passAccuracy", "tacklesTotal", "blocks", "interceptions", "duelsTotal", "duelsWon",
            "dribblesAttempts", "dribblesSuccess", "dribblesPast", "foulsDrawn", "foulsCommitted",
            "yellowCards", "redCards", "offsides", "penaltyWon", "penaltyCommitted", "penaltyScored",
            "penaltyMissed", "penaltySaved"
    );
    private static final Map<String, Set<String>> EVENT_DETAILS_BY_TYPE = Map.of(
            "Goal", Set.of("Normal Goal", "Own Goal", "Penalty", "Missed Penalty"),
            "Card", Set.of("Yellow Card", "Red card"),
            "Var", Set.of("Goal cancelled", "Penalty confirmed")
    );
    private static final Pattern SUBSTITUTION_DETAIL_PATTERN = Pattern.compile("Substitution\\s+\\d+");
    private static final Pattern AUDIT_MESSAGE_PARAMETER_PATTERN = Pattern.compile("\\b(sequence|player|team)=(\\d+)\\b");
    private static final Set<String> PUBLIC_SYNC_DETAIL_KEYS = Set.of("league", "season", "fixtureId");
    private static final Set<String> PUBLIC_EXTERNAL_API_DETAIL_KEYS = Set.of(
            "operation", "teamId", "articleId", "batchSize", "resultCount", "attempts", "durationMs", "httpStatus", "errorCategory");
    private static final Map<String, String> SYNC_CATEGORY_LABELS = Map.of(
            "seasons", "Seasons",
            "teams", "Teams",
            "standings", "Standings",
            "fixtures", "Fixtures",
            "fixture-details", "Season Details",
            "fixture-detail", "Fixture Detail",
            "players", "Players",
            "injuries", "Injuries"
    );

    private static final List<SyncStatusDefinition> SYNC_STATUS_DEFINITIONS = List.of(
            new SyncStatusDefinition("api-football", "API-Football"),
            new SyncStatusDefinition("serp-api", "SerpAPI"),
            new SyncStatusDefinition("openai", "OpenAI"),
            new SyncStatusDefinition("seasons", "Seasons"),
            new SyncStatusDefinition("teams", "Teams"),
            new SyncStatusDefinition("standings", "Standings"),
            new SyncStatusDefinition("fixtures", "Fixtures"),
            new SyncStatusDefinition("fixture-details", "Season Details"),
            new SyncStatusDefinition("players", "Players"),
            new SyncStatusDefinition("injuries", "Injuries")
    );

    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final PlayerRepository playerRepository;
    private final FixtureRepository fixtureRepository;
    private final FixtureEventRepository fixtureEventRepository;
    private final FixtureLineupRepository fixtureLineupRepository;
    private final FixtureStatRepository fixtureStatRepository;
    private final PlayerFixtureStatRepository playerFixtureStatRepository;
    private final FixtureRedisService fixtureRedisService;
    private final LeagueSeasonCoverageRepository leagueSeasonCoverageRepository;
    private final AdminOverrideService adminOverrideService;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ApiFootballSyncStatusRepository apiFootballSyncStatusRepository;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;
    private final ApiFootballTeamSyncService apiFootballTeamSyncService;
    private final ApiFootballStandingSyncService apiFootballStandingSyncService;
    private final ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final ApiFootballInjurySyncService apiFootballInjurySyncService;
    private final LeagueSeasonCoverageSyncService leagueSeasonCoverageSyncService;
    private final AdminSyncTaskRunner adminSyncTaskRunner;
    private final AdminSyncJobService adminSyncJobService;
    private final ApiFootballSyncExecutionGuard apiFootballSyncExecutionGuard;
    private final ApiFootballSyncFailureRetryScheduler apiFootballSyncFailureRetryScheduler;
    private final MediaUrlService mediaUrlService;
    private final EntityManager entityManager;
    private final ConcurrentMap<String, ManualSyncState> manualSyncStates = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public List<AdminDto.TeamAdminResponse> searchTeams(String keyword) {
        String search = adminSearchKeyword(keyword);
        return teamRepository.findTop20ByNameOrKoreanNameContainingIgnoreCaseOrderByNameAsc(search)
                .stream()
                .map(this::toTeamResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDto.TeamAdminResponse getTeamAdminDetail(Long teamId) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        return toTeamResponse(team);
    }

    @Transactional(readOnly = true)
    public List<AdminDto.PlayerAdminResponse> searchPlayers(String keyword) {
        String search = adminSearchKeyword(keyword);
        return playerRepository.findTop20ByNameOrKoreanNameContainingIgnoreCaseOrderByNameAsc(search)
                .stream()
                .map(this::toPlayerResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminDto.PlayerAdminResponse getPlayerAdminDetail(Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        return toPlayerResponse(player);
    }

    @Transactional(readOnly = true)
    public List<AdminDto.FixtureTeamOptionResponse> getFixtureTeams(Integer season) {
        return teamRepository.findAllWithFixtureInSeasonOrderByNameAsc(season)
                .stream()
                .map(team -> AdminDto.FixtureTeamOptionResponse.builder()
                        .teamId(team.getTeamId())
                        .name(team.getName())
                        .koreanName(team.getKoreanName())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminDto.FixtureAdminSummaryResponse> searchFixtures(String keyword, Integer season) {
        String search = adminSearchKeyword(keyword);
        return fixtureRepository.searchAdminFixtures(search, season, PageRequest.of(0, 20))
                .stream()
                .map(this::toFixtureSummaryResponse)
                .toList();
    }

    private String adminSearchKeyword(String keyword) {
        String search = keyword == null ? "" : keyword.trim();
        if (search.length() > ADMIN_SEARCH_KEYWORD_MAX_LENGTH) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_SEARCH_KEYWORD);
        }
        return search;
    }

    @Transactional(readOnly = true)
    public AdminDto.FixtureAdminDetailResponse getFixtureAdminDetail(Long fixtureId) {
        Fixture fixture = findFixtureWithTeams(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse updateFixture(Long adminUserId, Long fixtureId, AdminDto.FixtureUpdateRequest request) {
        request.normalizeTextFields();
        Fixture fixture = findFixtureWithTeams(fixtureId);
        validateAdminEditVersion(request.getVersion(), fixture.getVersion());
        entityManager.lock(fixture, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        List<FieldChange> changes = changedFixtureFields(fixture, request);
        LocalDateTime fixtureDateUtc = request.getFixtureDate() != null
                ? LocalDateTime.ofInstant(request.getFixtureDate().toInstant(), ZoneOffset.UTC)
                : fixture.getFixtureDate();
        Long timestamp = request.getFixtureDate() != null
                ? request.getFixtureDate().toInstant().getEpochSecond()
                : fixture.getTimestamp();

        fixture.updateFixtureMetadata(
                fixtureDateUtc,
                request.getReferee(),
                fixture.getTimezone(),
                timestamp,
                fixture.getFirstPeriod(),
                fixture.getSecondPeriod(),
                request.getVenueId(),
                request.getVenueName(),
                request.getVenueCity()
        );
        fixture.updateVenueKoreanName(request.getVenueNameKo());
        fixture.updateTactics(
                request.getHomeFormation(),
                request.getAwayFormation(),
                request.getHomeCoachName(),
                request.getAwayCoachName()
        );
        fixture.updateHomeLineupColors(
                request.getHomePlayerColorPrimary(),
                request.getHomePlayerColorNumber(),
                request.getHomePlayerColorBorder(),
                request.getHomeGoalkeeperColorPrimary(),
                request.getHomeGoalkeeperColorNumber(),
                request.getHomeGoalkeeperColorBorder()
        );
        fixture.updateAwayLineupColors(
                request.getAwayPlayerColorPrimary(),
                request.getAwayPlayerColorNumber(),
                request.getAwayPlayerColorBorder(),
                request.getAwayGoalkeeperColorPrimary(),
                request.getAwayGoalkeeperColorNumber(),
                request.getAwayGoalkeeperColorBorder()
        );
        if (!changes.isEmpty()) {
            adminOverrideService.markOverrides(
                    AdminOverrideTargetType.FIXTURE,
                    fixtureId,
                    fieldNames(changes)
            );
        }
        entityManager.flush();
        evictFixtureCaches(fixtureId);
        saveFixtureUpdateLog(adminUserId, fixture.getFixtureId(), "Fixture updated", changes);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixtureOverrides(Long adminUserId, Long fixtureId) {
        Fixture fixture = findFixtureWithTeams(fixtureId);
        long deletedCount = adminOverrideService.clearOverrides(AdminOverrideTargetType.FIXTURE, fixtureId);
        saveOverrideClearLog(adminUserId, "FIXTURE", fixtureId, "ALL", deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixtureOverride(
            Long adminUserId,
            Long fixtureId,
            String fieldName
    ) {
        validateOverrideField(FIXTURE_OVERRIDE_FIELDS, fieldName);
        Fixture fixture = findFixtureWithTeams(fixtureId);
        long deletedCount = adminOverrideService.clearOverride(
                AdminOverrideTargetType.FIXTURE, fixtureId, fieldName);
        saveOverrideClearLog(adminUserId, "FIXTURE", fixtureId, fieldName, deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse updateFixtureEvent(
            Long adminUserId,
            Long fixtureId,
            Integer eventSequence,
            AdminDto.FixtureEventUpdateRequest request
    ) {
        request.normalizeTextFields();
        Fixture fixture = findFixtureForEventUpdate(fixtureId);
        validateFinishedFixture(fixture);
        FixtureEvent event = fixtureEventRepository.findByFixtureFixtureIdAndEventSequence(fixtureId, eventSequence)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        validateFixtureEventRequest(fixture, request);
        String eventType = normalizeEventType(request.getEventType());
        event.updateEvent(
                request.getElapsed(),
                request.getExtra(),
                findTeamOrNull(request.getTeamId()),
                findPlayerOrNull(request.getPlayerId()),
                findPlayerOrNull(request.getAssistPlayerId()),
                eventType,
                request.getEventDetail(),
                request.getComments()
        );
        adminOverrideService.markOverrides(
                AdminOverrideTargetType.FIXTURE_EVENT,
                fixtureId,
                List.of(FIXTURE_EVENTS_OVERRIDE_FIELD)
        );
        evictFixtureCaches(fixtureId);
        saveFixtureUpdateLog(adminUserId, fixtureId, "Fixture event updated: sequence=" + eventSequence, List.of());
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse createFixtureEvent(
            Long adminUserId,
            Long fixtureId,
            AdminDto.FixtureEventUpdateRequest request
    ) {
        request.normalizeTextFields();
        Fixture fixture = findFixtureForEventUpdate(fixtureId);
        validateFinishedFixture(fixture);
        validateFixtureEventRequest(fixture, request);
        String eventType = normalizeEventType(request.getEventType());
        Integer nextSequence = fixtureEventRepository.findAllByFixtureFixtureIdOrderByMatchTimeAsc(fixtureId)
                .stream()
                .map(FixtureEvent::getEventSequence)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .map(sequence -> sequence + 1)
                .orElse(1);
        FixtureEvent event = FixtureEvent.builder()
                .fixture(fixture)
                .eventSequence(nextSequence)
                .elapsed(request.getElapsed())
                .extra(request.getExtra())
                .team(findTeamOrNull(request.getTeamId()))
                .player(findPlayerOrNull(request.getPlayerId()))
                .assistPlayer(findPlayerOrNull(request.getAssistPlayerId()))
                .eventType(eventType)
                .eventDetail(request.getEventDetail())
                .comments(request.getComments())
                .build();
        fixtureEventRepository.save(event);
        adminOverrideService.markOverrides(
                AdminOverrideTargetType.FIXTURE_EVENT,
                fixtureId,
                List.of(FIXTURE_EVENTS_OVERRIDE_FIELD)
        );
        evictFixtureCaches(fixtureId);
        saveFixtureUpdateLog(adminUserId, fixtureId, "Fixture event created: sequence=" + nextSequence, List.of());
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse deleteFixtureEvent(
            Long adminUserId,
            Long fixtureId,
            Integer eventSequence
    ) {
        Fixture fixture = findFixtureForEventUpdate(fixtureId);
        validateFinishedFixture(fixture);
        FixtureEvent event = fixtureEventRepository.findByFixtureFixtureIdAndEventSequence(fixtureId, eventSequence)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        fixtureEventRepository.delete(event);
        adminOverrideService.markOverrides(
                AdminOverrideTargetType.FIXTURE_EVENT,
                fixtureId,
                List.of(FIXTURE_EVENTS_OVERRIDE_FIELD)
        );
        evictFixtureCaches(fixtureId);
        saveFixtureUpdateLog(adminUserId, fixtureId, "Fixture event deleted: sequence=" + eventSequence, List.of());
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixtureEventOverrides(
            Long adminUserId,
            Long fixtureId
    ) {
        Fixture fixture = findFixtureForEventUpdate(fixtureId);
        long deletedCount = adminOverrideService.clearOverrides(
                AdminOverrideTargetType.FIXTURE_EVENT, fixtureId);
        saveOverrideClearLog(adminUserId, "FIXTURE_EVENT", fixtureId, "ALL", deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixtureEventOverride(
            Long adminUserId,
            Long fixtureId,
            String fieldName
    ) {
        validateOverrideField(FIXTURE_EVENT_OVERRIDE_FIELDS, fieldName);
        Fixture fixture = findFixtureForEventUpdate(fixtureId);
        long deletedCount = adminOverrideService.clearOverride(
                AdminOverrideTargetType.FIXTURE_EVENT, fixtureId, fieldName);
        saveOverrideClearLog(adminUserId, "FIXTURE_EVENT", fixtureId, fieldName, deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse updateFixtureLineup(
            Long adminUserId,
            Long fixtureId,
            Long teamId,
            Long playerId,
            AdminDto.FixtureLineupUpdateRequest request
    ) {
        request.normalizeTextFields();
        validateRequiredAdminText(request.getPosition());
        Fixture fixture = findFixtureWithTeams(fixtureId);
        validateFixtureTeam(fixture, teamId);
        validateFixturePlayer(fixtureId, teamId, playerId);
        FixtureLineup lineup = fixtureLineupRepository.findByFixtureFixtureIdAndTeamTeamIdAndPlayerPlayerId(fixtureId, teamId, playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        validateAdminEditVersion(request.getVersion(), lineup.getVersion());
        entityManager.lock(lineup, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        Integer jerseyNumber = request.getJerseyNumber() != null
                ? request.getJerseyNumber()
                : lineup.getJerseyNumber();
        List<FieldChange> changes = changedFixtureLineupFields(lineup, request, jerseyNumber);
        lineup.updateLineup(
                jerseyNumber,
                request.getPosition(),
                request.getGrid(),
                Boolean.TRUE.equals(request.getStarter())
        );
        if (!changes.isEmpty()) {
            adminOverrideService.markOverrides(
                    AdminOverrideTargetType.FIXTURE_LINEUP,
                    lineup.getId(),
                    fieldNames(changes)
            );
        }
        entityManager.flush();
        evictFixtureCaches(fixtureId);
        saveFixtureUpdateLog(adminUserId, fixtureId, "Fixture lineup updated: player=" + playerId, changes);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixtureLineupOverrides(
            Long adminUserId,
            Long fixtureId,
            Long teamId,
            Long playerId
    ) {
        Fixture fixture = findFixtureWithTeams(fixtureId);
        FixtureLineup lineup = findFixtureLineup(fixtureId, teamId, playerId);
        long deletedCount = adminOverrideService.clearOverrides(
                AdminOverrideTargetType.FIXTURE_LINEUP, lineup.getId());
        saveOverrideClearLog(adminUserId, "FIXTURE_LINEUP", lineup.getId(), "ALL", deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixtureLineupOverride(
            Long adminUserId,
            Long fixtureId,
            Long teamId,
            Long playerId,
            String fieldName
    ) {
        validateOverrideField(FIXTURE_LINEUP_OVERRIDE_FIELDS, fieldName);
        Fixture fixture = findFixtureWithTeams(fixtureId);
        FixtureLineup lineup = findFixtureLineup(fixtureId, teamId, playerId);
        long deletedCount = adminOverrideService.clearOverride(
                AdminOverrideTargetType.FIXTURE_LINEUP, lineup.getId(), fieldName);
        saveOverrideClearLog(adminUserId, "FIXTURE_LINEUP", lineup.getId(), fieldName, deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    @CacheEvict(
            cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
            cacheNames = RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE,
            allEntries = true
    )
    public AdminDto.FixtureAdminDetailResponse updateFixtureTeamStat(
            Long adminUserId,
            Long fixtureId,
            Long teamId,
            AdminDto.FixtureTeamStatUpdateRequest request
    ) {
        Fixture fixture = findFixtureWithTeams(fixtureId);
        validateFinishedFixture(fixture);
        validateFixtureTeam(fixture, teamId);
        FixtureStat stat = fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(fixtureId, teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        validateAdminEditVersion(request.getVersion(), stat.getVersion());
        Integer passAccuracy = calculatePassAccuracy(request.getPassesAccurate(), request.getTotalPasses());
        List<FieldChange> changes = changedFixtureTeamStatFields(stat, request, passAccuracy);
        stat.updateStats(
                request.getShotsOnGoal(),
                request.getShotsOffGoal(),
                request.getTotalShots(),
                request.getBlockedShots(),
                request.getShotsInsideBox(),
                request.getShotsOutsideBox(),
                request.getFouls(),
                request.getCornerKicks(),
                request.getOffsides(),
                request.getBallPossession(),
                request.getYellowCards(),
                request.getRedCards(),
                request.getGoalkeeperSaves(),
                request.getTotalPasses(),
                request.getPassesAccurate(),
                passAccuracy,
                request.getExpectedGoals()
        );
        if (!changes.isEmpty()) {
            adminOverrideService.markOverrides(
                    AdminOverrideTargetType.FIXTURE_TEAM_STAT,
                    stat.getId(),
                    fieldNames(changes)
            );
        }
        entityManager.flush();
        evictFixtureCaches(fixtureId);
        saveFixtureUpdateLog(adminUserId, fixtureId, "Fixture team stats updated: team=" + teamId, changes);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixtureTeamStatOverrides(
            Long adminUserId,
            Long fixtureId,
            Long teamId
    ) {
        Fixture fixture = findFixtureWithTeams(fixtureId);
        FixtureStat stat = fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(fixtureId, teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverrides(
                AdminOverrideTargetType.FIXTURE_TEAM_STAT, stat.getId());
        saveOverrideClearLog(adminUserId, "FIXTURE_TEAM_STAT", stat.getId(), "ALL", deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixtureTeamStatOverride(
            Long adminUserId,
            Long fixtureId,
            Long teamId,
            String fieldName
    ) {
        validateOverrideField(FIXTURE_TEAM_STAT_OVERRIDE_FIELDS, fieldName);
        Fixture fixture = findFixtureWithTeams(fixtureId);
        FixtureStat stat = fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(fixtureId, teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverride(
                AdminOverrideTargetType.FIXTURE_TEAM_STAT, stat.getId(), fieldName);
        saveOverrideClearLog(adminUserId, "FIXTURE_TEAM_STAT", stat.getId(), fieldName, deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse updateFixturePlayerStat(
            Long adminUserId,
            Long fixtureId,
            Long playerId,
            AdminDto.FixturePlayerStatUpdateRequest request
    ) {
        Fixture fixture = findFixtureWithTeams(fixtureId);
        validateFinishedFixture(fixture);
        PlayerFixtureStat stat = playerFixtureStatRepository.findByFixtureFixtureIdAndPlayerPlayerId(fixtureId, playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        validateAdminEditVersion(request.getVersion(), stat.getVersion());
        Integer passAccuracy = calculatePassAccuracy(request.getPassesAccurate(), request.getPassesTotal());
        List<FieldChange> changes = changedFixturePlayerStatFields(stat, request, passAccuracy);
        stat.updateLiveStat(
                request.getMinutesPlayed(),
                request.getRating(),
                request.getCaptain(),
                request.getSubstitute(),
                request.getGoals(),
                request.getAssists(),
                request.getConceded(),
                request.getSaves(),
                request.getShotsTotal(),
                request.getShotsOnTarget(),
                request.getPassesTotal(),
                request.getPassesKey(),
                request.getPassesAccurate(),
                passAccuracy,
                request.getTacklesTotal(),
                request.getBlocks(),
                request.getInterceptions(),
                request.getDuelsTotal(),
                request.getDuelsWon(),
                request.getDribblesAttempts(),
                request.getDribblesSuccess(),
                request.getDribblesPast(),
                request.getFoulsDrawn(),
                request.getFoulsCommitted(),
                request.getYellowCards(),
                request.getRedCards(),
                request.getOffsides(),
                request.getPenaltyWon(),
                request.getPenaltyCommitted(),
                request.getPenaltyScored(),
                request.getPenaltyMissed(),
                request.getPenaltySaved()
        );
        if (!changes.isEmpty()) {
            adminOverrideService.markOverrides(
                    AdminOverrideTargetType.FIXTURE_PLAYER_STAT,
                    stat.getId(),
                    fieldNames(changes)
            );
        }
        entityManager.flush();
        evictFixtureCaches(fixtureId);
        saveFixtureUpdateLog(adminUserId, fixtureId, "Fixture player stats updated: player=" + playerId, changes);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixturePlayerStatOverrides(
            Long adminUserId,
            Long fixtureId,
            Long playerId
    ) {
        Fixture fixture = findFixtureWithTeams(fixtureId);
        PlayerFixtureStat stat = playerFixtureStatRepository
                .findByFixtureFixtureIdAndPlayerPlayerId(fixtureId, playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverrides(
                AdminOverrideTargetType.FIXTURE_PLAYER_STAT, stat.getId());
        saveOverrideClearLog(adminUserId, "FIXTURE_PLAYER_STAT", stat.getId(), "ALL", deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.FixtureAdminDetailResponse clearFixturePlayerStatOverride(
            Long adminUserId,
            Long fixtureId,
            Long playerId,
            String fieldName
    ) {
        validateOverrideField(FIXTURE_PLAYER_STAT_OVERRIDE_FIELDS, fieldName);
        Fixture fixture = findFixtureWithTeams(fixtureId);
        PlayerFixtureStat stat = playerFixtureStatRepository
                .findByFixtureFixtureIdAndPlayerPlayerId(fixtureId, playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverride(
                AdminOverrideTargetType.FIXTURE_PLAYER_STAT, stat.getId(), fieldName);
        saveOverrideClearLog(adminUserId, "FIXTURE_PLAYER_STAT", stat.getId(), fieldName, deletedCount);
        evictFixtureCaches(fixtureId);
        return toFixtureDetailResponse(fixture);
    }

    @Transactional
    public AdminDto.TeamAdminResponse updateTeam(Long adminUserId, Long teamId, AdminDto.TeamUpdateRequest request) {
        request.normalizeTextFields();
        validateRequiredAdminText(request.getName());
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        validateAdminEditVersion(request.getVersion(), team.getVersion());
        entityManager.lock(team, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        List<FieldChange> changes = changedTeamFields(team, request);
        List<String> changedFields = fieldNames(changes);

        team.updateTeam(
                request.getName(),
                request.getCode(),
                request.getCountry(),
                request.getFounded(),
                request.getLogoUrl()
        );
        team.updateKoreanName(request.getKoreanName());
        upsertVenue(team, request);
        if (!changedFields.isEmpty()) {
            adminOverrideService.markOverrides(AdminOverrideTargetType.TEAM, team.getTeamId(), changedFields);
        }
        adminAuditLogRepository.save(AdminAuditLog.of(
                findUser(adminUserId),
                AdminAuditType.TEAM_UPDATE,
                "TEAM",
                team.getTeamId(),
                "Team profile updated: " + team.getName(),
                detailsOf(changes),
                true
        ));
        entityManager.flush();
        return toTeamResponse(team);
    }

    @Transactional
    public AdminDto.PlayerAdminResponse updatePlayer(Long adminUserId, Long playerId, AdminDto.PlayerUpdateRequest request) {
        request.normalizeTextFields();
        validateRequiredAdminText(request.getName());
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        validateAdminEditVersion(request.getVersion(), player.getVersion());
        List<FieldChange> changes = changedPlayerFields(player, request);
        List<String> changedFields = fieldNames(changes);

        player.updateProfile(
                request.getName(),
                request.getFirstname(),
                request.getLastname(),
                request.getAge(),
                request.getBirthDate(),
                request.getBirthPlace(),
                request.getBirthCountry(),
                request.getNationality(),
                request.getHeight(),
                request.getWeight(),
                request.getPosition(),
                request.getNumber(),
                request.getPhotoUrl()
        );
        player.updateKoreanName(request.getKoreanName());
        if (!changedFields.isEmpty()) {
            adminOverrideService.markOverrides(AdminOverrideTargetType.PLAYER, player.getPlayerId(), changedFields);
        }
        adminAuditLogRepository.save(AdminAuditLog.of(
                findUser(adminUserId),
                AdminAuditType.PLAYER_UPDATE,
                "PLAYER",
                player.getPlayerId(),
                "Player profile updated: " + player.getName(),
                detailsOf(changes),
                true
        ));
        entityManager.flush();
        return toPlayerResponse(player);
    }

    @Transactional
    public AdminDto.TeamAdminResponse clearTeamOverride(Long adminUserId, Long teamId, String fieldName) {
        validateOverrideField(TEAM_OVERRIDE_FIELDS, fieldName);
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverride(AdminOverrideTargetType.TEAM, team.getTeamId(), fieldName);
        saveOverrideClearLog(adminUserId, "TEAM", team.getTeamId(), fieldName, deletedCount);
        return toTeamResponse(team);
    }

    @Transactional
    public AdminDto.TeamAdminResponse clearTeamOverrides(Long adminUserId, Long teamId) {
        Team team = teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverrides(AdminOverrideTargetType.TEAM, team.getTeamId());
        saveOverrideClearLog(adminUserId, "TEAM", team.getTeamId(), "ALL", deletedCount);
        return toTeamResponse(team);
    }

    @Transactional
    public AdminDto.PlayerAdminResponse clearPlayerOverride(Long adminUserId, Long playerId, String fieldName) {
        validateOverrideField(PLAYER_OVERRIDE_FIELDS, fieldName);
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverride(AdminOverrideTargetType.PLAYER, player.getPlayerId(), fieldName);
        saveOverrideClearLog(adminUserId, "PLAYER", player.getPlayerId(), fieldName, deletedCount);
        return toPlayerResponse(player);
    }

    @Transactional
    public AdminDto.PlayerAdminResponse clearPlayerOverrides(Long adminUserId, Long playerId) {
        Player player = playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
        long deletedCount = adminOverrideService.clearOverrides(AdminOverrideTargetType.PLAYER, player.getPlayerId());
        saveOverrideClearLog(adminUserId, "PLAYER", player.getPlayerId(), "ALL", deletedCount);
        return toPlayerResponse(player);
    }

    public AdminDto.SyncResponse syncLeagueSeasons(Long adminUserId, Integer league) {
        return runSync(
                adminUserId,
                "seasons",
                "LEAGUE",
                league == null ? null : league.longValue(),
                "league=" + league,
                () -> leagueSeasonCoverageSyncService.syncLeagueSeasons(league)
        );
    }

    public AdminDto.SyncResponse syncTeams(Long adminUserId, Integer league, Integer season) {
        validateSeasonExists(league, season);
        return runSync(adminUserId, "teams", "TEAM", null, syncDetails(league, season), () -> apiFootballTeamSyncService.syncTeams(league, season));
    }

    public AdminDto.SyncResponse syncStandings(Long adminUserId, Integer league, Integer season) {
        validateSeasonCoverage(league, season, coverage -> Boolean.TRUE.equals(coverage.getStandings()));
        return runSync(adminUserId, "standings", "STANDING", null, syncDetails(league, season), () -> apiFootballStandingSyncService.syncStandings(league, season));
    }

    public AdminDto.SyncResponse syncFixtures(Long adminUserId, Integer league, Integer season) {
        validateSeasonExists(league, season);
        return runSync(adminUserId, "fixtures", "FIXTURE", null, syncDetails(league, season), () -> apiFootballFixtureSyncService.syncSeasonFixtures(league, season));
    }

    public AdminDto.SyncResponse syncSeasonFixtureDetails(Long adminUserId, Integer season) {
        validateSeasonCoverage(39, season, this::supportsFixtureDetails);
        return queueSync(adminUserId, "fixture-details", "FIXTURE", null, syncDetails(null, season),
                season, progress -> apiFootballFixtureDetailSyncService.syncSeasonFixtureDetails(season, false, progress));
    }

    public AdminDto.SyncResponse syncFixtureDetail(Long adminUserId, Long fixtureId) {
        validateFixtureDetailCoverage(fixtureId);
        return runSync(adminUserId, "fixture-detail", "FIXTURE", fixtureId, "fixtureId=" + fixtureId,
                () -> apiFootballFixtureDetailSyncService.syncFixtureDetail(fixtureId, false).fixtureId() != null ? 1 : 0);
    }

    public AdminDto.SyncResponse syncPlayers(Long adminUserId, Integer league, Integer season, Long delayMs) {
        validateSeasonCoverage(league, season, coverage -> Boolean.TRUE.equals(coverage.getPlayers()));
        if (!teamStandingRepository.existsByLeagueIdAndSeason(league, season)) {
            throw new CustomException(ErrorCode.ADMIN_SYNC_STANDINGS_REQUIRED);
        }
        return queueSync(adminUserId, "players", "PLAYER", null, syncDetails(league, season),
                season, progress -> apiFootballPlayerSyncService.syncRegisteredPlayers(league, season, delayMs, progress));
    }

    public AdminDto.SyncResponse syncInjuries(Long adminUserId, Integer league, Integer season) {
        validateSeasonCoverage(league, season, coverage -> Boolean.TRUE.equals(coverage.getInjuries()));
        return queueSync(adminUserId, "injuries", "INJURY", null, syncDetails(league, season),
                season, progress -> apiFootballInjurySyncService.syncInjuries(league, season, progress));
    }

    @Transactional(readOnly = true)
    public AdminDto.AuditLogListResponse getAuditLogs(Integer page, Integer size) {
        int safePage = page == null || page < 0 ? 0 : page;
        int safeSize = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        var logs = adminAuditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize));
        return AdminDto.AuditLogListResponse.builder()
                .logs(logs.stream()
                        .map(this::toAuditLogResponse)
                        .toList())
                .page(logs.getNumber())
                .size(logs.getSize())
                .totalPages(logs.getTotalPages())
                .totalElements(logs.getTotalElements())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDto.SyncStatusResponse getSyncStatuses(Integer season) {
        return AdminDto.SyncStatusResponse.builder()
                .statuses(SYNC_STATUS_DEFINITIONS.stream()
                        .map(definition -> toSyncStatusItem(definition, season))
                        .toList())
                .build();
    }

    private AdminDto.SyncResponse runSync(Long adminUserId, String task, String targetType, Long targetId, String details, SyncTask syncTask) {
        AppUser admin = findUser(adminUserId);
        String syncKey = manualSyncKey(task, details);
        ApiFootballSyncExecutionGuard.Lease reservation = acquireManualSync(syncKey);
        try {
            int count = syncTask.run();
            apiFootballSyncFailureRetryScheduler.cancelPendingByExecutionKey(syncKey);
            String message = task + " sync completed. " + details + "; count=" + count;
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, details, true));
            return AdminDto.SyncResponse.builder()
                    .task(task)
                    .success(true)
                    .count(count)
                    .message(message)
                    .build();
        } catch (Exception exception) {
            recordSyncFailure(task, seasonFromDetails(details), exception);
            String message = task + " sync failed. " + details + ": " + exception.getMessage();
            adminAuditLogRepository.save(AdminAuditLog.of(admin, AdminAuditType.SYNC, targetType, targetId, message, details, false));
            return AdminDto.SyncResponse.builder()
                    .task(task)
                    .success(false)
                    .count(0)
                    .message(message)
                    .build();
        } finally {
            releaseManualSync(syncKey, reservation);
        }
    }

    public AdminDto.SyncJobListResponse getSyncJobs(Integer limit) {
        return adminSyncJobService.recentJobs(limit);
    }

    public AdminDto.SyncCancelResponse cancelSyncJob(Long adminUserId, Long jobId) {
        AppUser admin = findUser(adminUserId);
        AdminSyncJobService.CancelResult result = adminSyncJobService.requestCancel(jobId);
        if (result.cancelledBeforeStart()) {
            releaseCurrentManualSync(manualSyncKey(result.task(), result.details()));
        }
        String message = result.cancelledBeforeStart()
                ? result.task() + " sync cancelled before start."
                : result.status() == com.son.soccerStreaming.admin.entity.AdminSyncJobStatus.CANCEL_REQUESTED
                ? result.task() + " sync cancellation requested."
                : result.task() + " sync is already finished.";
        adminAuditLogRepository.save(AdminAuditLog.of(
                admin, AdminAuditType.SYNC, "SYNC_JOB", jobId, message, result.details(), true));
        return AdminDto.SyncCancelResponse.builder()
                .jobId(jobId)
                .status(result.status().name())
                .message(message)
                .build();
    }

    private AdminDto.SyncResponse queueSync(Long adminUserId, String task, String targetType, Long targetId,
                                            String details, Integer season, AdminSyncTaskRunner.SyncTask syncTask) {
        // Keep the admin request short; the runner writes start/completion audit logs from a worker thread.
        findUser(adminUserId);
        String syncKey = manualSyncKey(task, details);
        if (adminSyncJobService.hasActiveJob(task, details)) {
            throw new CustomException(ErrorCode.ADMIN_SYNC_ALREADY_RUNNING);
        }
        ApiFootballSyncExecutionGuard.Lease reservation = acquireManualSync(syncKey);
        final com.son.soccerStreaming.admin.entity.AdminSyncJob job;
        try {
            if (adminSyncJobService.hasActiveJob(task, details)) {
                throw new CustomException(ErrorCode.ADMIN_SYNC_ALREADY_RUNNING);
            }
            job = adminSyncJobService.create(adminUserId, task, targetType, targetId, season, details);
        } catch (RuntimeException exception) {
            releaseManualSync(syncKey, reservation);
            throw exception;
        }
        AdminSyncTaskRunner.SyncTask trackedSyncTask = progress -> {
            try {
                int count = syncTask.run(progress);
                apiFootballSyncFailureRetryScheduler.cancelPendingByExecutionKey(syncKey);
                return count;
            } catch (Exception exception) {
                recordSyncFailure(task, season, exception);
                throw exception;
            }
        };
        try {
            adminSyncTaskRunner.run(job.getId(), adminUserId, task, targetType, targetId, details,
                    trackedSyncTask, () -> releaseManualSync(syncKey, reservation));
        } catch (RuntimeException exception) {
            adminSyncJobService.markFailed(job.getId(), task + " sync could not be queued: " + exception.getMessage());
            releaseManualSync(syncKey, reservation);
            throw exception;
        }
        return AdminDto.SyncResponse.builder()
                .jobId(job.getId())
                .task(task)
                .success(true)
                .queued(true)
                .count(0)
                .message(task + " sync has started in the background. Check audit logs for completion.")
                .build();
    }

    private String manualSyncKey(String task, String details) {
        return task + ":" + details;
    }

    private ApiFootballSyncExecutionGuard.Lease acquireManualSync(String syncKey) {
        final ApiFootballSyncExecutionGuard.Lease reservation;
        try {
            reservation = apiFootballSyncExecutionGuard.acquire(syncKey);
        } catch (ApiFootballSyncAlreadyRunningException exception) {
            throw new CustomException(ErrorCode.ADMIN_SYNC_ALREADY_RUNNING);
        }
        Instant now = Instant.now();
        try {
            manualSyncStates.compute(syncKey, (key, current) -> {
                if (current != null && current.running()) {
                    throw new CustomException(ErrorCode.ADMIN_SYNC_ALREADY_RUNNING);
                }
                if (current != null && Duration.between(current.requestedAt(), now).compareTo(MANUAL_SYNC_COOLDOWN) < 0) {
                    throw new CustomException(ErrorCode.ADMIN_SYNC_TOO_FREQUENT);
                }
                return new ManualSyncState(now, true, reservation);
            });
            return reservation;
        } catch (RuntimeException exception) {
            apiFootballSyncExecutionGuard.release(reservation);
            throw exception;
        }
    }

    private void releaseManualSync(String syncKey, ApiFootballSyncExecutionGuard.Lease reservation) {
        manualSyncStates.computeIfPresent(syncKey, (key, current) ->
                Objects.equals(current.reservation(), reservation)
                        ? new ManualSyncState(current.requestedAt(), false, current.reservation())
                        : current);
        apiFootballSyncExecutionGuard.release(reservation);
    }

    private void releaseCurrentManualSync(String syncKey) {
        ManualSyncState current = manualSyncStates.get(syncKey);
        if (current != null) {
            releaseManualSync(syncKey, current.reservation());
        }
    }

    private String syncDetails(Integer league, Integer season) {
        if (league == null) {
            return "season=" + season;
        }
        return "league=" + league + "; season=" + season;
    }

    private void validateSeasonExists(Integer league, Integer season) {
        validateSeasonCoverage(league, season, coverage -> true);
    }

    private void validateSeasonCoverage(Integer league, Integer season, Predicate<LeagueSeasonCoverage> supports) {
        LeagueSeasonCoverage coverage = leagueSeasonCoverageRepository.findByLeagueIdAndSeasonYear(league, season)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_ADMIN_SYNC_COVERAGE));
        if (!supports.test(coverage)) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_SYNC_COVERAGE);
        }
    }

    private void validateFixtureDetailCoverage(Long fixtureId) {
        Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        validateSeasonCoverage(39, fixture.getSeason(), this::supportsFixtureDetails);
    }

    private boolean supportsFixtureDetails(LeagueSeasonCoverage coverage) {
        return Boolean.TRUE.equals(coverage.getEvents())
                || Boolean.TRUE.equals(coverage.getLineups())
                || Boolean.TRUE.equals(coverage.getFixtureStats())
                || Boolean.TRUE.equals(coverage.getPlayerStats());
    }

    private void upsertVenue(Team team, AdminDto.TeamUpdateRequest request) {
        Venue venue = team.getVenue();
        if (venue == null && request.getVenueId() == null) {
            return;
        }
        if (venue == null) {
            venue = Venue.builder()
                    .venueId(request.getVenueId())
                    .build();
        }
        venue.updateVenue(
                request.getVenueName(),
                request.getVenueAddress(),
                request.getVenueCity(),
                request.getCapacity(),
                request.getSurface(),
                request.getVenueImageUrl()
        );
        venue.updateKoreanName(request.getVenueNameKo());
        team.updateVenue(venue);
    }

    private List<FieldChange> changedTeamFields(Team team, AdminDto.TeamUpdateRequest request) {
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "name", team.getName(), request.getName());
        addIfChanged(changes, "koreanName", team.getKoreanName(), request.getKoreanName());
        addIfChanged(changes, "code", team.getCode(), request.getCode());
        addIfChanged(changes, "country", team.getCountry(), request.getCountry());
        addIfChanged(changes, "founded", team.getFounded(), request.getFounded());
        addIfChanged(changes, "logoUrl", team.getLogoUrl(), request.getLogoUrl());

        Venue venue = team.getVenue();
        if (venue != null || request.getVenueId() != null) {
            addIfChanged(changes, "venueId", venue != null ? venue.getVenueId() : null, request.getVenueId());
            addIfChanged(changes, "venueName", venue != null ? venue.getVenueName() : null, request.getVenueName());
            addIfChanged(changes, "venueNameKo", venue != null ? venue.getVenueNameKo() : null, request.getVenueNameKo());
            addIfChanged(changes, "venueAddress", venue != null ? venue.getVenueAddress() : null, request.getVenueAddress());
            addIfChanged(changes, "venueCity", venue != null ? venue.getVenueCity() : null, request.getVenueCity());
            addIfChanged(changes, "capacity", venue != null ? venue.getCapacity() : null, request.getCapacity());
            addIfChanged(changes, "surface", venue != null ? venue.getSurface() : null, request.getSurface());
            addIfChanged(changes, "venueImageUrl", venue != null ? venue.getVenueImageUrl() : null, request.getVenueImageUrl());
        }

        return changes;
    }

    private List<FieldChange> changedPlayerFields(Player player, AdminDto.PlayerUpdateRequest request) {
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "name", player.getName(), request.getName());
        addIfChanged(changes, "koreanName", player.getKoreanName(), request.getKoreanName());
        addIfChanged(changes, "firstname", player.getFirstname(), request.getFirstname());
        addIfChanged(changes, "lastname", player.getLastname(), request.getLastname());
        addIfChanged(changes, "age", player.getAge(), request.getAge());
        addIfChanged(changes, "birthDate", player.getBirthDate(), request.getBirthDate());
        addIfChanged(changes, "birthPlace", player.getBirthPlace(), request.getBirthPlace());
        addIfChanged(changes, "birthCountry", player.getBirthCountry(), request.getBirthCountry());
        addIfChanged(changes, "nationality", player.getNationality(), request.getNationality());
        addIfChanged(changes, "height", player.getHeight(), request.getHeight());
        addIfChanged(changes, "weight", player.getWeight(), request.getWeight());
        addIfChanged(changes, "position", player.getPosition(), request.getPosition());
        addIfChanged(changes, "number", player.getNumber(), request.getNumber());
        addIfChanged(changes, "photoUrl", player.getPhotoUrl(), request.getPhotoUrl());
        return changes;
    }

    private List<FieldChange> changedFixtureTeamStatFields(
            FixtureStat stat,
            AdminDto.FixtureTeamStatUpdateRequest request,
            Integer passAccuracy
    ) {
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "shotsOnGoal", stat.getShotsOnGoal(), request.getShotsOnGoal());
        addIfChanged(changes, "shotsOffGoal", stat.getShotsOffGoal(), request.getShotsOffGoal());
        addIfChanged(changes, "totalShots", stat.getTotalShots(), request.getTotalShots());
        addIfChanged(changes, "blockedShots", stat.getBlockedShots(), request.getBlockedShots());
        addIfChanged(changes, "shotsInsideBox", stat.getShotsInsideBox(), request.getShotsInsideBox());
        addIfChanged(changes, "shotsOutsideBox", stat.getShotsOutsideBox(), request.getShotsOutsideBox());
        addIfChanged(changes, "fouls", stat.getFouls(), request.getFouls());
        addIfChanged(changes, "cornerKicks", stat.getCornerKicks(), request.getCornerKicks());
        addIfChanged(changes, "offsides", stat.getOffsides(), request.getOffsides());
        addIfChanged(changes, "ballPossession", stat.getBallPossession(), request.getBallPossession());
        addIfChanged(changes, "yellowCards", stat.getYellowCards(), request.getYellowCards());
        addIfChanged(changes, "redCards", stat.getRedCards(), request.getRedCards());
        addIfChanged(changes, "goalkeeperSaves", stat.getGoalkeeperSaves(), request.getGoalkeeperSaves());
        addIfChanged(changes, "totalPasses", stat.getTotalPasses(), request.getTotalPasses());
        addIfChanged(changes, "passesAccurate", stat.getPassesAccurate(), request.getPassesAccurate());
        addIfChanged(changes, "passAccuracy", stat.getPassAccuracy(), passAccuracy);
        addIfChanged(changes, "expectedGoals", stat.getExpectedGoals(), request.getExpectedGoals());
        return changes;
    }

    private List<FieldChange> changedFixturePlayerStatFields(
            PlayerFixtureStat stat,
            AdminDto.FixturePlayerStatUpdateRequest request,
            Integer passAccuracy
    ) {
        Integer minutesPlayed = PlayerFixtureStat.normalizeMinutesPlayed(request.getMinutesPlayed());
        Integer goals = PlayerFixtureStat.normalizeScoringStat(request.getMinutesPlayed(), request.getGoals());
        Integer assists = PlayerFixtureStat.normalizeScoringStat(request.getMinutesPlayed(), request.getAssists());
        Integer yellowCards = PlayerFixtureStat.normalizeYellowCards(request.getYellowCards(), request.getRedCards());
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "minutesPlayed", stat.getMinutesPlayed(), minutesPlayed);
        addIfChanged(changes, "rating", stat.getRating(), request.getRating());
        addIfChanged(changes, "captain", stat.getIsCaptain(), request.getCaptain());
        addIfChanged(changes, "substitute", stat.getIsSubstitute(), request.getSubstitute());
        addIfChanged(changes, "goals", stat.getGoals(), goals);
        addIfChanged(changes, "assists", stat.getAssists(), assists);
        addIfChanged(changes, "conceded", stat.getConceded(), request.getConceded());
        addIfChanged(changes, "saves", stat.getSaves(), request.getSaves());
        addIfChanged(changes, "shotsTotal", stat.getShotsTotal(), request.getShotsTotal());
        addIfChanged(changes, "shotsOnTarget", stat.getShotsOnTarget(), request.getShotsOnTarget());
        addIfChanged(changes, "passesTotal", stat.getPassesTotal(), request.getPassesTotal());
        addIfChanged(changes, "passesKey", stat.getPassesKey(), request.getPassesKey());
        addIfChanged(changes, "passesAccurate", stat.getPassesAccurate(), request.getPassesAccurate());
        addIfChanged(changes, "passAccuracy", stat.getPassAccuracy(), passAccuracy);
        addIfChanged(changes, "tacklesTotal", stat.getTacklesTotal(), request.getTacklesTotal());
        addIfChanged(changes, "blocks", stat.getBlocks(), request.getBlocks());
        addIfChanged(changes, "interceptions", stat.getInterceptions(), request.getInterceptions());
        addIfChanged(changes, "duelsTotal", stat.getDuelsTotal(), request.getDuelsTotal());
        addIfChanged(changes, "duelsWon", stat.getDuelsWon(), request.getDuelsWon());
        addIfChanged(changes, "dribblesAttempts", stat.getDribblesAttempts(), request.getDribblesAttempts());
        addIfChanged(changes, "dribblesSuccess", stat.getDribblesSuccess(), request.getDribblesSuccess());
        addIfChanged(changes, "dribblesPast", stat.getDribblesPast(), request.getDribblesPast());
        addIfChanged(changes, "foulsDrawn", stat.getFoulsDrawn(), request.getFoulsDrawn());
        addIfChanged(changes, "foulsCommitted", stat.getFoulsCommitted(), request.getFoulsCommitted());
        addIfChanged(changes, "yellowCards", stat.getYellowCards(), yellowCards);
        addIfChanged(changes, "redCards", stat.getRedCards(), request.getRedCards());
        addIfChanged(changes, "offsides", stat.getOffsides(), request.getOffsides());
        addIfChanged(changes, "penaltyWon", stat.getPenaltyWon(), request.getPenaltyWon());
        addIfChanged(changes, "penaltyCommitted", stat.getPenaltyCommitted(), request.getPenaltyCommitted());
        addIfChanged(changes, "penaltyScored", stat.getPenaltyScored(), request.getPenaltyScored());
        addIfChanged(changes, "penaltyMissed", stat.getPenaltyMissed(), request.getPenaltyMissed());
        addIfChanged(changes, "penaltySaved", stat.getPenaltySaved(), request.getPenaltySaved());
        return changes;
    }

    private void addIfChanged(List<FieldChange> changes, String fieldName, Object currentValue, Object requestValue) {
        if (!Objects.equals(currentValue, requestValue)) {
            changes.add(new FieldChange(fieldName, currentValue, requestValue));
        }
    }

    private List<String> fieldNames(List<FieldChange> changes) {
        return changes.stream()
                .map(FieldChange::fieldName)
                .toList();
    }

    private void validateOverrideField(Set<String> allowedFields, String fieldName) {
        if (!allowedFields.contains(fieldName)) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_OVERRIDE_FIELD);
        }
    }

    private void saveOverrideClearLog(Long adminUserId, String targetType, Long targetId, String fieldName, long deletedCount) {
        String target = "ALL".equals(fieldName) ? "all manual overrides" : "manual override field=" + fieldName;
        adminAuditLogRepository.save(AdminAuditLog.of(
                findUser(adminUserId),
                AdminAuditType.OVERRIDE_CLEAR,
                targetType,
                targetId,
                "Cleared " + target + " for " + targetType + " #" + targetId,
                "field=" + fieldName + "; deletedCount=" + deletedCount,
                true
        ));
    }

    private String detailsOf(List<FieldChange> changes) {
        if (changes.isEmpty()) {
            return "changedFields=[]";
        }
        return changes.stream()
                .map(change -> change.fieldName() + ": " + valueText(change.previousValue()) + " -> " + valueText(change.newValue()))
                .collect(Collectors.joining("; "));
    }

    private String valueText(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }

    private record FieldChange(String fieldName, Object previousValue, Object newValue) {
    }

    private record ManualSyncState(Instant requestedAt, boolean running,
                                   ApiFootballSyncExecutionGuard.Lease reservation) {
    }

    private Fixture findFixtureWithTeams(Long fixtureId) {
        return fixtureRepository.findWithTeamsByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
    }

    private Fixture findFixtureForEventUpdate(Long fixtureId) {
        return fixtureRepository.findByFixtureIdForEventUpdate(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
    }

    private FixtureLineup findFixtureLineup(Long fixtureId, Long teamId, Long playerId) {
        return fixtureLineupRepository
                .findByFixtureFixtureIdAndTeamTeamIdAndPlayerPlayerId(fixtureId, teamId, playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
    }

    private Team findTeamOrNull(Long teamId) {
        if (teamId == null) {
            return null;
        }
        return teamRepository.findByTeamId(teamId)
                .orElseThrow(() -> new CustomException(ErrorCode.TEAM_NOT_FOUND));
    }

    private void validateFixtureEventRequest(Fixture fixture, AdminDto.FixtureEventUpdateRequest request) {
        validateRange(request.getElapsed(), 0, 90);
        validateRange(request.getExtra(), 0, 20);
        validateEventTypeAndDetail(request.getEventType(), request.getEventDetail());
        validateFixtureTeam(fixture, request.getTeamId());
        validateFixturePlayer(fixture.getFixtureId(), request.getTeamId(), request.getPlayerId());
        validateFixturePlayer(fixture.getFixtureId(), request.getTeamId(), request.getAssistPlayerId());
    }

    private void validateRange(Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_EVENT_FIELD);
        }
    }

    private void validateRequiredAdminText(String value) {
        if (value == null) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_EDIT_FIELD);
        }
    }

    private void validateFinishedFixture(Fixture fixture) {
        if (!fixture.isFinished()) {
            throw new CustomException(ErrorCode.ADMIN_FIXTURE_NOT_FINISHED);
        }
    }

    private void validateAdminEditVersion(Long requestedVersion, long currentVersion) {
        if (requestedVersion == null || requestedVersion != currentVersion) {
            throw new CustomException(ErrorCode.ADMIN_EDIT_CONFLICT);
        }
    }

    private void validateEventTypeAndDetail(String eventType, String eventDetail) {
        String normalizedType = normalizeEventType(eventType);
        if (!EVENT_TYPES.contains(normalizedType)) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_EVENT_FIELD);
        }
        if (eventDetail == null || eventDetail.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_EVENT_FIELD);
        }
        if ("Subst".equals(normalizedType)) {
            if (!SUBSTITUTION_DETAIL_PATTERN.matcher(eventDetail).matches()) {
                throw new CustomException(ErrorCode.INVALID_ADMIN_EVENT_FIELD);
            }
            return;
        }
        if (!EVENT_DETAILS_BY_TYPE.getOrDefault(normalizedType, Set.of()).contains(eventDetail)) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_EVENT_FIELD);
        }
    }

    private String normalizeEventType(String eventType) {
        if (eventType == null) {
            return "";
        }
        return switch (eventType.trim().toLowerCase()) {
            case "goal" -> "Goal";
            case "card" -> "Card";
            case "subst", "substitution" -> "Subst";
            case "var" -> "Var";
            default -> eventType;
        };
    }

    private void validateFixtureTeam(Fixture fixture, Long teamId) {
        if (teamId == null) {
            return;
        }
        Long homeTeamId = fixture.getHomeTeam().getTeamId();
        Long awayTeamId = fixture.getAwayTeam().getTeamId();
        if (!teamId.equals(homeTeamId) && !teamId.equals(awayTeamId)) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_EVENT_FIELD);
        }
    }

    private void validateFixturePlayer(Long fixtureId, Long teamId, Long playerId) {
        if (playerId == null) {
            return;
        }
        boolean lineupPlayer = fixtureLineupRepository.findAllByFixtureId(fixtureId).stream()
                .anyMatch(lineup -> teamId != null
                        && teamId.equals(lineup.getTeam().getTeamId())
                        && playerId.equals(lineup.getPlayer().getPlayerId()));
        boolean statsPlayer = playerFixtureStatRepository.findAllByFixtureFixtureId(fixtureId).stream()
                .anyMatch(stat -> teamId != null
                        && teamId.equals(stat.getTeam().getTeamId())
                        && playerId.equals(stat.getPlayer().getPlayerId()));
        if (!lineupPlayer && !statsPlayer) {
            throw new CustomException(ErrorCode.INVALID_ADMIN_EVENT_FIELD);
        }
    }

    private Player findPlayerOrNull(Long playerId) {
        if (playerId == null) {
            return null;
        }
        return playerRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new CustomException(ErrorCode.PLAYER_NOT_FOUND));
    }

    private void saveFixtureUpdateLog(Long adminUserId, Long fixtureId, String message, List<FieldChange> changes) {
        adminAuditLogRepository.save(AdminAuditLog.of(
                findUser(adminUserId),
                AdminAuditType.FIXTURE_UPDATE,
                "FIXTURE",
                fixtureId,
                message,
                detailsOf(changes),
                true
        ));
    }

    private List<FieldChange> changedFixtureFields(Fixture fixture, AdminDto.FixtureUpdateRequest request) {
        List<FieldChange> changes = new ArrayList<>();
        LocalDateTime requestedFixtureDateUtc = request.getFixtureDate() != null
                ? LocalDateTime.ofInstant(request.getFixtureDate().toInstant(), ZoneOffset.UTC)
                : null;
        addIfChanged(changes, "fixtureDate", fixture.getFixtureDate(), requestedFixtureDateUtc);
        addIfChanged(changes, "referee", fixture.getReferee(), request.getReferee());
        addIfChanged(changes, "venueId", fixture.getVenueId(), request.getVenueId());
        addIfChanged(changes, "venueName", fixture.getVenueName(), request.getVenueName());
        addIfChanged(changes, "venueNameKo", fixture.getVenueNameKo(), request.getVenueNameKo());
        addIfChanged(changes, "venueCity", fixture.getVenueCity(), request.getVenueCity());
        addIfChanged(changes, "homeFormation", fixture.getHomeFormation(), request.getHomeFormation());
        addIfChanged(changes, "awayFormation", fixture.getAwayFormation(), request.getAwayFormation());
        addIfChanged(changes, "homeCoachName", fixture.getHomeCoachName(), request.getHomeCoachName());
        addIfChanged(changes, "awayCoachName", fixture.getAwayCoachName(), request.getAwayCoachName());
        addIfChanged(changes, "homePlayerColorPrimary", fixture.getHomePlayerColorPrimary(), request.getHomePlayerColorPrimary());
        addIfChanged(changes, "homePlayerColorNumber", fixture.getHomePlayerColorNumber(), request.getHomePlayerColorNumber());
        addIfChanged(changes, "homePlayerColorBorder", fixture.getHomePlayerColorBorder(), request.getHomePlayerColorBorder());
        addIfChanged(changes, "homeGoalkeeperColorPrimary", fixture.getHomeGoalkeeperColorPrimary(), request.getHomeGoalkeeperColorPrimary());
        addIfChanged(changes, "homeGoalkeeperColorNumber", fixture.getHomeGoalkeeperColorNumber(), request.getHomeGoalkeeperColorNumber());
        addIfChanged(changes, "homeGoalkeeperColorBorder", fixture.getHomeGoalkeeperColorBorder(), request.getHomeGoalkeeperColorBorder());
        addIfChanged(changes, "awayPlayerColorPrimary", fixture.getAwayPlayerColorPrimary(), request.getAwayPlayerColorPrimary());
        addIfChanged(changes, "awayPlayerColorNumber", fixture.getAwayPlayerColorNumber(), request.getAwayPlayerColorNumber());
        addIfChanged(changes, "awayPlayerColorBorder", fixture.getAwayPlayerColorBorder(), request.getAwayPlayerColorBorder());
        addIfChanged(changes, "awayGoalkeeperColorPrimary", fixture.getAwayGoalkeeperColorPrimary(), request.getAwayGoalkeeperColorPrimary());
        addIfChanged(changes, "awayGoalkeeperColorNumber", fixture.getAwayGoalkeeperColorNumber(), request.getAwayGoalkeeperColorNumber());
        addIfChanged(changes, "awayGoalkeeperColorBorder", fixture.getAwayGoalkeeperColorBorder(), request.getAwayGoalkeeperColorBorder());
        return changes;
    }

    private List<FieldChange> changedFixtureLineupFields(
            FixtureLineup lineup,
            AdminDto.FixtureLineupUpdateRequest request,
            Integer jerseyNumber
    ) {
        List<FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, "jerseyNumber", lineup.getJerseyNumber(), jerseyNumber);
        addIfChanged(changes, "position", lineup.getPosition(), request.getPosition());
        addIfChanged(changes, "grid", lineup.getGrid(), request.getGrid());
        addIfChanged(changes, "starter", lineup.isStarter(), Boolean.TRUE.equals(request.getStarter()));
        return changes;
    }

    private Integer calculatePassAccuracy(Integer passesAccurate, Integer totalPasses) {
        if (passesAccurate == null || totalPasses == null || totalPasses <= 0) {
            return null;
        }
        return (int) Math.round((passesAccurate * 100.0) / totalPasses);
    }

    private AppUser findUser(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
    }

    private AdminDto.TeamAdminResponse toTeamResponse(Team team) {
        Venue venue = team.getVenue();
        return AdminDto.TeamAdminResponse.builder()
                .teamId(team.getTeamId())
                .version(team.getVersion())
                .name(team.getName())
                .koreanName(team.getKoreanName())
                .code(team.getCode())
                .country(team.getCountry())
                .founded(team.getFounded())
                .logoUrl(team.getLogoUrl())
                .logoDisplayUrl(mediaUrlService.teamLogoUrl(team))
                .adminLogo(hasText(team.getAdminLogoObjectKey()))
                .venueId(venue != null ? venue.getVenueId() : null)
                .venueName(venue != null ? venue.getVenueName() : null)
                .venueNameKo(venue != null ? venue.getVenueNameKo() : null)
                .venueAddress(venue != null ? venue.getVenueAddress() : null)
                .venueCity(venue != null ? venue.getVenueCity() : null)
                .capacity(venue != null ? venue.getCapacity() : null)
                .surface(venue != null ? venue.getSurface() : null)
                .venueImageUrl(venue != null ? venue.getVenueImageUrl() : null)
                .venueImageDisplayUrl(mediaUrlService.venueImageUrl(venue))
                .adminVenueImage(venue != null && hasText(venue.getAdminVenueImageObjectKey()))
                .manualOverrides(toOverrideResponses(AdminOverrideTargetType.TEAM, team.getTeamId()))
                .build();
    }

    private AdminDto.PlayerAdminResponse toPlayerResponse(Player player) {
        return AdminDto.PlayerAdminResponse.builder()
                .playerId(player.getPlayerId())
                .version(player.getVersion())
                .name(player.getName())
                .koreanName(player.getKoreanName())
                .firstname(player.getFirstname())
                .lastname(player.getLastname())
                .age(player.getAge())
                .birthDate(player.getBirthDate())
                .birthPlace(player.getBirthPlace())
                .birthCountry(player.getBirthCountry())
                .nationality(player.getNationality())
                .height(player.getHeight())
                .weight(player.getWeight())
                .position(player.getPosition())
                .number(player.getNumber())
                .photoUrl(player.getPhotoUrl())
                .photoDisplayUrl(mediaUrlService.playerPhotoUrl(player))
                .adminPhoto(hasText(player.getAdminPhotoObjectKey()))
                .manualOverrides(toOverrideResponses(AdminOverrideTargetType.PLAYER, player.getPlayerId()))
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private AdminDto.FixtureAdminSummaryResponse toFixtureSummaryResponse(Fixture fixture) {
        return AdminDto.FixtureAdminSummaryResponse.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(utcToKoreaOffsetDateTime(fixture.getFixtureDate()))
                .season(fixture.getSeason())
                .round(fixture.getRound())
                .homeTeamId(fixture.getHomeTeam().getTeamId())
                .homeTeamName(fixture.getHomeTeam().getName())
                .homeTeamNameKo(fixture.getHomeTeam().getKoreanName())
                .awayTeamId(fixture.getAwayTeam().getTeamId())
                .awayTeamName(fixture.getAwayTeam().getName())
                .awayTeamNameKo(fixture.getAwayTeam().getKoreanName())
                .homeScore(fixture.getHomeScore())
                .awayScore(fixture.getAwayScore())
                .fixtureStatus(fixture.getFixtureStatus())
                .build();
    }

    private AdminDto.FixtureAdminDetailResponse toFixtureDetailResponse(Fixture fixture) {
        Long fixtureId = fixture.getFixtureId();
        return AdminDto.FixtureAdminDetailResponse.builder()
                .fixture(toFixtureResponse(fixture))
                .eventOverrides(toOverrideResponses(AdminOverrideTargetType.FIXTURE_EVENT, fixtureId))
                .events(fixtureEventRepository.findAllByFixtureFixtureIdOrderByMatchTimeAsc(fixtureId)
                        .stream()
                        .map(this::toFixtureEventResponse)
                        .toList())
                .lineups(fixtureLineupRepository.findAllByFixtureId(fixtureId)
                        .stream()
                        .map(this::toFixtureLineupResponse)
                        .toList())
                .teamStats(fixtureStatRepository.findAllByFixtureFixtureId(fixtureId)
                        .stream()
                        .map(this::toFixtureTeamStatResponse)
                        .toList())
                .playerStats(playerFixtureStatRepository.findAllByFixtureFixtureId(fixtureId)
                        .stream()
                        .map(this::toFixturePlayerStatResponse)
                        .toList())
                .build();
    }

    private AdminDto.FixtureAdminResponse toFixtureResponse(Fixture fixture) {
        return AdminDto.FixtureAdminResponse.builder()
                .fixtureId(fixture.getFixtureId())
                .version(fixture.getVersion())
                .fixtureDate(utcToKoreaOffsetDateTime(fixture.getFixtureDate()))
                .referee(fixture.getReferee())
                .timezone(fixture.getTimezone())
                .timestamp(fixture.getTimestamp())
                .firstPeriod(fixture.getFirstPeriod())
                .secondPeriod(fixture.getSecondPeriod())
                .round(fixture.getRound())
                .season(fixture.getSeason())
                .venueId(fixture.getVenueId())
                .venueName(fixture.getVenueName())
                .venueNameKo(fixture.getVenueNameKo())
                .venueCity(fixture.getVenueCity())
                .statusShort(fixture.getStatusShort())
                .statusLong(fixture.getStatusLong())
                .elapsed(fixture.getElapsed())
                .fixtureStatus(fixture.getFixtureStatus())
                .homeScore(fixture.getHomeScore())
                .awayScore(fixture.getAwayScore())
                .homeWinner(fixture.getHomeWinner())
                .awayWinner(fixture.getAwayWinner())
                .halftimeHomeScore(fixture.getHalftimeHomeScore())
                .halftimeAwayScore(fixture.getHalftimeAwayScore())
                .fulltimeHomeScore(fixture.getFulltimeHomeScore())
                .fulltimeAwayScore(fixture.getFulltimeAwayScore())
                .extratimeHomeScore(fixture.getExtratimeHomeScore())
                .extratimeAwayScore(fixture.getExtratimeAwayScore())
                .penaltyHomeScore(fixture.getPenaltyHomeScore())
                .penaltyAwayScore(fixture.getPenaltyAwayScore())
                .homeTeamId(fixture.getHomeTeam().getTeamId())
                .homeTeamName(fixture.getHomeTeam().getName())
                .homeTeamNameKo(fixture.getHomeTeam().getKoreanName())
                .awayTeamId(fixture.getAwayTeam().getTeamId())
                .awayTeamName(fixture.getAwayTeam().getName())
                .awayTeamNameKo(fixture.getAwayTeam().getKoreanName())
                .homeFormation(fixture.getHomeFormation())
                .awayFormation(fixture.getAwayFormation())
                .homeCoachName(fixture.getHomeCoachName())
                .awayCoachName(fixture.getAwayCoachName())
                .homePlayerColorPrimary(fixture.getHomePlayerColorPrimary())
                .homePlayerColorNumber(fixture.getHomePlayerColorNumber())
                .homePlayerColorBorder(fixture.getHomePlayerColorBorder())
                .homeGoalkeeperColorPrimary(fixture.getHomeGoalkeeperColorPrimary())
                .homeGoalkeeperColorNumber(fixture.getHomeGoalkeeperColorNumber())
                .homeGoalkeeperColorBorder(fixture.getHomeGoalkeeperColorBorder())
                .awayPlayerColorPrimary(fixture.getAwayPlayerColorPrimary())
                .awayPlayerColorNumber(fixture.getAwayPlayerColorNumber())
                .awayPlayerColorBorder(fixture.getAwayPlayerColorBorder())
                .awayGoalkeeperColorPrimary(fixture.getAwayGoalkeeperColorPrimary())
                .awayGoalkeeperColorNumber(fixture.getAwayGoalkeeperColorNumber())
                .awayGoalkeeperColorBorder(fixture.getAwayGoalkeeperColorBorder())
                .manualOverrides(toOverrideResponses(AdminOverrideTargetType.FIXTURE, fixture.getFixtureId()))
                .build();
    }

    private AdminDto.FixtureEventAdminResponse toFixtureEventResponse(FixtureEvent event) {
        return AdminDto.FixtureEventAdminResponse.builder()
                .eventSequence(event.getEventSequence())
                .elapsed(event.getElapsed())
                .extra(event.getExtra())
                .teamId(event.getTeam() != null ? event.getTeam().getTeamId() : null)
                .teamName(event.getTeam() != null ? event.getTeam().getName() : null)
                .teamNameKo(event.getTeam() != null ? event.getTeam().getKoreanName() : null)
                .playerId(event.getPlayer() != null ? event.getPlayer().getPlayerId() : null)
                .playerName(event.getPlayer() != null ? event.getPlayer().getName() : null)
                .playerNameKo(event.getPlayer() != null ? event.getPlayer().getKoreanName() : null)
                .assistPlayerId(event.getAssistPlayer() != null ? event.getAssistPlayer().getPlayerId() : null)
                .assistPlayerName(event.getAssistPlayer() != null ? event.getAssistPlayer().getName() : null)
                .assistPlayerNameKo(event.getAssistPlayer() != null ? event.getAssistPlayer().getKoreanName() : null)
                .eventType(event.getEventType())
                .eventDetail(event.getEventDetail())
                .comments(event.getComments())
                .build();
    }

    private AdminDto.FixtureLineupAdminResponse toFixtureLineupResponse(FixtureLineup lineup) {
        return AdminDto.FixtureLineupAdminResponse.builder()
                .lineupId(lineup.getId())
                .version(lineup.getVersion())
                .teamId(lineup.getTeam().getTeamId())
                .teamName(lineup.getTeam().getName())
                .teamNameKo(lineup.getTeam().getKoreanName())
                .playerId(lineup.getPlayer().getPlayerId())
                .playerName(lineup.getPlayer().getName())
                .playerNameKo(lineup.getPlayer().getKoreanName())
                .jerseyNumber(lineup.getJerseyNumber())
                .position(lineup.getPosition())
                .grid(lineup.getGrid())
                .starter(lineup.isStarter())
                .manualOverrides(toOverrideResponses(AdminOverrideTargetType.FIXTURE_LINEUP, lineup.getId()))
                .build();
    }

    private AdminDto.FixtureTeamStatAdminResponse toFixtureTeamStatResponse(FixtureStat stat) {
        return AdminDto.FixtureTeamStatAdminResponse.builder()
                .teamId(stat.getTeam().getTeamId())
                .version(stat.getVersion())
                .teamName(stat.getTeam().getName())
                .teamNameKo(stat.getTeam().getKoreanName())
                .shotsOnGoal(stat.getShotsOnGoal())
                .shotsOffGoal(stat.getShotsOffGoal())
                .totalShots(stat.getTotalShots())
                .blockedShots(stat.getBlockedShots())
                .shotsInsideBox(stat.getShotsInsideBox())
                .shotsOutsideBox(stat.getShotsOutsideBox())
                .fouls(stat.getFouls())
                .cornerKicks(stat.getCornerKicks())
                .offsides(stat.getOffsides())
                .ballPossession(stat.getBallPossession())
                .yellowCards(stat.getYellowCards())
                .redCards(stat.getRedCards())
                .goalkeeperSaves(stat.getGoalkeeperSaves())
                .totalPasses(stat.getTotalPasses())
                .passesAccurate(stat.getPassesAccurate())
                .passAccuracy(stat.getPassAccuracy())
                .expectedGoals(stat.getExpectedGoals())
                .manualOverrides(toOverrideResponses(AdminOverrideTargetType.FIXTURE_TEAM_STAT, stat.getId()))
                .build();
    }

    private AdminDto.FixturePlayerStatAdminResponse toFixturePlayerStatResponse(PlayerFixtureStat stat) {
        return AdminDto.FixturePlayerStatAdminResponse.builder()
                .playerId(stat.getPlayer().getPlayerId())
                .version(stat.getVersion())
                .playerName(stat.getPlayer().getName())
                .playerNameKo(stat.getPlayer().getKoreanName())
                .teamId(stat.getTeam().getTeamId())
                .teamName(stat.getTeam().getName())
                .teamNameKo(stat.getTeam().getKoreanName())
                .minutesPlayed(stat.getMinutesPlayed())
                .rating(stat.getRating())
                .captain(stat.getIsCaptain())
                .substitute(stat.getIsSubstitute())
                .goals(stat.getGoals())
                .assists(stat.getAssists())
                .conceded(stat.getConceded())
                .saves(stat.getSaves())
                .shotsTotal(stat.getShotsTotal())
                .shotsOnTarget(stat.getShotsOnTarget())
                .passesTotal(stat.getPassesTotal())
                .passesKey(stat.getPassesKey())
                .passesAccurate(stat.getPassesAccurate())
                .passAccuracy(stat.getPassAccuracy())
                .tacklesTotal(stat.getTacklesTotal())
                .blocks(stat.getBlocks())
                .interceptions(stat.getInterceptions())
                .duelsTotal(stat.getDuelsTotal())
                .duelsWon(stat.getDuelsWon())
                .dribblesAttempts(stat.getDribblesAttempts())
                .dribblesSuccess(stat.getDribblesSuccess())
                .dribblesPast(stat.getDribblesPast())
                .foulsDrawn(stat.getFoulsDrawn())
                .foulsCommitted(stat.getFoulsCommitted())
                .yellowCards(stat.getYellowCards())
                .redCards(stat.getRedCards())
                .offsides(stat.getOffsides())
                .penaltyWon(stat.getPenaltyWon())
                .penaltyCommitted(stat.getPenaltyCommitted())
                .penaltyScored(stat.getPenaltyScored())
                .penaltyMissed(stat.getPenaltyMissed())
                .penaltySaved(stat.getPenaltySaved())
                .manualOverrides(toOverrideResponses(AdminOverrideTargetType.FIXTURE_PLAYER_STAT, stat.getId()))
                .build();
    }

    private List<AdminDto.ManualOverrideResponse> toOverrideResponses(AdminOverrideTargetType targetType, Long targetId) {
        List<AdminOverrideService.OverrideInfo> overrides = adminOverrideService.overrideInfos(targetType, targetId);
        if (overrides == null) {
            return List.of();
        }
        return overrides.stream()
                .map(override -> AdminDto.ManualOverrideResponse.builder()
                        .fieldName(override.fieldName())
                        .updatedAt(override.updatedAt())
                        .build())
                .toList();
    }

    private AdminDto.AuditLogResponse toAuditLogResponse(AdminAuditLog log) {
        return AdminDto.AuditLogResponse.builder()
                .id(log.getId())
                .adminEmail(log.getAdminUser() != null ? log.getAdminUser().getEmail() : null)
                .type(log.getType().name())
                .syncCategory(syncCategory(log))
                .targetType(log.getTargetType())
                .targetId(log.getTargetId())
                .message(publicAuditMessage(log))
                .details(publicAuditDetails(log))
                .provider(log.getProvider())
                .success(log.isSuccess())
                .createdAt(log.getCreatedAt())
                .build();
    }

    private String syncCategory(AdminAuditLog log) {
        if (log.getType() != AdminAuditType.SYNC || log.getMessage() == null) {
            return null;
        }
        String message = log.getMessage().toLowerCase();
        return SYNC_CATEGORY_LABELS.entrySet().stream()
                .filter(entry -> message.startsWith(entry.getKey() + " sync"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private String publicAuditDetails(AdminAuditLog log) {
        Map<String, String> parameters = new LinkedHashMap<>();
        addAuditTargetParameter(parameters, log.getTargetType(), log.getTargetId());

        if (log.getType() == AdminAuditType.SYNC) {
            addSyncAuditParameters(parameters, log.getDetails());
        }
        if (log.getType() == AdminAuditType.FIXTURE_UPDATE) {
            addFixtureAuditParameters(parameters, log.getMessage());
        }
        if (log.getType() == AdminAuditType.OVERRIDE_CLEAR) {
            addAllowedDetailParameter(parameters, log.getDetails(), "field", "field");
        }
        if (log.getType() == AdminAuditType.EXTERNAL_API_CALL) {
            addWhitelistedParameters(parameters, log.getDetails(), PUBLIC_EXTERNAL_API_DETAIL_KEYS);
        }

        if (parameters.isEmpty()) {
            return null;
        }
        return parameters.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
    }

    private void addAuditTargetParameter(Map<String, String> parameters, String targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            return;
        }
        String parameterName = switch (targetType) {
            case "LEAGUE" -> "leagueId";
            case "TEAM", "TEAM_LOGO" -> "teamId";
            case "PLAYER", "PLAYER_PHOTO" -> "playerId";
            case "FIXTURE" -> "fixtureId";
            case "VENUE", "VENUE_IMAGE" -> "venueId";
            case "SYNC_JOB" -> "syncJobId";
            default -> "targetId";
        };
        parameters.put(parameterName, String.valueOf(targetId));
    }

    private void addSyncAuditParameters(Map<String, String> parameters, String details) {
        if (details == null || details.isBlank()) {
            return;
        }
        for (String part : details.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length != 2 || !PUBLIC_SYNC_DETAIL_KEYS.contains(pair[0])) {
                continue;
            }
            String parameterName = "league".equals(pair[0]) ? "leagueId" : pair[0];
            if (!pair[1].isBlank()) {
                parameters.put(parameterName, pair[1].trim());
            }
        }
    }

    private void addFixtureAuditParameters(Map<String, String> parameters, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        var matcher = AUDIT_MESSAGE_PARAMETER_PATTERN.matcher(message);
        while (matcher.find()) {
            String parameterName = switch (matcher.group(1)) {
                case "sequence" -> "eventSequence";
                case "player" -> "playerId";
                case "team" -> "teamId";
                default -> matcher.group(1);
            };
            parameters.put(parameterName, matcher.group(2));
        }
    }

    private void addWhitelistedParameters(Map<String, String> parameters, String details, Set<String> allowedKeys) {
        if (details == null || details.isBlank()) return;
        for (String part : details.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && allowedKeys.contains(pair[0]) && !pair[1].isBlank()) {
                parameters.put(pair[0], pair[1].trim());
            }
        }
    }

    private void addAllowedDetailParameter(Map<String, String> parameters, String details,
                                           String sourceName, String outputName) {
        if (details == null || details.isBlank()) {
            return;
        }
        for (String part : details.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && sourceName.equals(pair[0]) && !pair[1].isBlank()) {
                parameters.put(outputName, pair[1].trim());
                return;
            }
        }
    }

    private String publicAuditMessage(AdminAuditLog log) {
        return switch (log.getType()) {
            case TEAM_UPDATE -> "팀 정보가 수정되었습니다.";
            case PLAYER_UPDATE -> "선수 정보가 수정되었습니다.";
            case FIXTURE_UPDATE -> "경기 정보가 수정되었습니다.";
            case MEDIA_UPLOAD -> "관리자 이미지가 적용되었습니다.";
            case MEDIA_RESTORE -> "관리자 이미지가 원본으로 복원되었습니다.";
            case OVERRIDE_CLEAR -> "수동 수정 설정이 해제되었습니다.";
            case SYNC -> publicSyncAuditMessage(log);
            case EXTERNAL_API_CALL -> log.isSuccess()
                    ? "외부 API 호출이 완료되었습니다."
                    : "외부 API 호출에 실패했습니다.";
        };
    }

    private String publicSyncAuditMessage(AdminAuditLog log) {
        String message = log.getMessage() == null ? "" : log.getMessage().toLowerCase();
        if (message.contains("cancel")) {
            return "관리자 요청으로 동기화가 취소되었습니다.";
        }
        if (message.contains("started")) {
            return "동기화 작업을 시작했습니다.";
        }
        if (message.contains("partial")) {
            return "일부 데이터는 동기화하지 못했습니다.";
        }
        if (!log.isSuccess() || message.contains("failed")) {
            return "동기화에 실패했습니다.";
        }
        return "성공적으로 동기화되었습니다.";
    }

    private AdminDto.SyncStatusItem toSyncStatusItem(SyncStatusDefinition definition, Integer season) {
        String statusKey = syncStatusKey(definition.task(), season);
        var status = apiFootballSyncStatusRepository.findById(statusKey).orElse(null);
        return AdminDto.SyncStatusItem.builder()
                .task(definition.task())
                .label(definition.label())
                .lastSyncedAt(status == null ? null : toKoreaOffsetDateTime(status.getLastSyncedAt()))
                .lastAttemptAt(status == null ? null : toKoreaOffsetDateTime(status.getLastAttemptAt()))
                .lastSuccessAt(status == null ? null : toKoreaOffsetDateTime(status.getLastSuccessAt()))
                .lastFailureAt(status == null ? null : toKoreaOffsetDateTime(status.getLastFailureAt()))
                .failureCount(syncFailureCount(status))
                .lastErrorMessage(status == null ? null : status.getLastErrorMessage())
                .status(syncDisplayStatus(status))
                .provider(status == null ? null : status.getProvider())
                .lastOperation(status == null ? null : status.getLastOperation())
                .lastErrorCategory(status == null ? null : status.getLastErrorCategory())
                .lastHttpStatus(status == null ? null : status.getLastHttpStatus())
                .lastAttemptCount(status == null ? null : status.getLastAttemptCount())
                .build();
    }

    private String syncStatusKey(String task, Integer season) {
        if ("api-football".equals(task) || "serp-api".equals(task) || "openai".equals(task)) {
            return task;
        }
        if ("seasons".equals(task)) {
            return "league-seasons:39";
        }
        return season == null ? task : "%s:%d".formatted(task, season);
    }

    private String syncDisplayStatus(com.son.soccerStreaming.apifootball.entity.ApiFootballSyncStatus status) {
        if (status == null || status.getStatus() == null || status.getStatus().isBlank()) {
            return "NEVER_SYNCED";
        }
        if ("OK".equals(status.getStatus()) && status.getLastSuccessAt() != null
                && Duration.between(status.getLastSuccessAt(), LocalDateTime.now(KOREA_ZONE)).toHours() >= 24) {
            return "STALE";
        }
        return status.getStatus();
    }

    private int syncFailureCount(com.son.soccerStreaming.apifootball.entity.ApiFootballSyncStatus status) {
        if (status == null || status.getFailureCount() == null) {
            return 0;
        }
        return status.getFailureCount();
    }

    private void evictFixtureCaches(Long fixtureId) {
        fixtureRedisService.evictFixtureCaches(fixtureId);
    }

    private OffsetDateTime toKoreaOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.atZone(KOREA_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime utcToKoreaOffsetDateTime(LocalDateTime dateTime) {
        return dateTime == null
                ? null
                : dateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA_ZONE).toOffsetDateTime();
    }

    private record SyncStatusDefinition(String task, String label) {
    }

    private void recordSyncFailure(String task, Integer season, Exception exception) {
        if ("fixture-detail".equals(task)) {
            apiFootballSyncStatusService.recordFailure("fixture-detail", "Fixture Detail", null, exception);
            return;
        }
        if ("seasons".equals(task)) {
            apiFootballSyncStatusService.recordFailureByKey("league-seasons:39", "League Seasons", exception);
            return;
        }
        syncDisplayName(task).ifPresent(displayName ->
                apiFootballSyncStatusService.recordFailure(task, displayName, season, exception));
    }

    private Optional<String> syncDisplayName(String task) {
        return SYNC_STATUS_DEFINITIONS.stream()
                .filter(definition -> definition.task().equals(task))
                .map(SyncStatusDefinition::label)
                .findFirst();
    }

    private Integer seasonFromDetails(String details) {
        if (details == null) {
            return null;
        }
        for (String part : details.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("season=")) {
                try {
                    return Integer.parseInt(trimmed.substring("season=".length()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface SyncTask {
        int run();
    }
}

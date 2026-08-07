package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballInjurySyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballPlayerSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballTeamSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncStatusService;
import com.son.soccerStreaming.apifootball.scheduler.ApiFootballSyncFailureRetryScheduler;
import com.son.soccerStreaming.apifootball.service.SyncProgressReporter;
import com.son.soccerStreaming.apifootball.service.LeagueSeasonCoverageSyncService;
import com.son.soccerStreaming.admin.dto.AdminDto;
import com.son.soccerStreaming.admin.entity.AdminAuditLog;
import com.son.soccerStreaming.admin.entity.AdminAuditType;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.entity.AdminSyncJob;
import com.son.soccerStreaming.admin.entity.AdminSyncJobStatus;
import com.son.soccerStreaming.apifootball.repository.ApiFootballSyncStatusRepository;
import com.son.soccerStreaming.auth.entity.AppUser;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
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
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.admin.repository.AdminAuditLogRepository;
import com.son.soccerStreaming.auth.repository.AppUserRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamStandingRepository teamStandingRepository;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private EntityManager entityManager;
    @Mock
    private AdminOverrideService adminOverrideService;
    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;
    @Mock
    private FixtureRepository fixtureRepository;
    @Mock
    private FixtureEventRepository fixtureEventRepository;
    @Mock
    private FixtureLineupRepository fixtureLineupRepository;
    @Mock
    private FixtureStatRepository fixtureStatRepository;
    @Mock
    private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock
    private FixtureRedisService fixtureRedisService;
    @Mock
    private LeagueSeasonCoverageRepository leagueSeasonCoverageRepository;
    @Mock
    private ApiFootballSyncStatusRepository apiFootballSyncStatusRepository;
    @Mock
    private ApiFootballSyncStatusService apiFootballSyncStatusService;
    @Mock
    private ApiFootballTeamSyncService apiFootballTeamSyncService;
    @Mock
    private ApiFootballStandingSyncService apiFootballStandingSyncService;
    @Mock
    private ApiFootballFixtureSyncService apiFootballFixtureSyncService;
    @Mock
    private ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    @Mock
    private ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    @Mock
    private ApiFootballInjurySyncService apiFootballInjurySyncService;
    @Mock
    private LeagueSeasonCoverageSyncService leagueSeasonCoverageSyncService;
    @Mock
    private AdminSyncTaskRunner adminSyncTaskRunner;
    @Mock
    private AdminSyncJobService adminSyncJobService;
    @Mock
    private ApiFootballSyncExecutionGuard apiFootballSyncExecutionGuard;
    @Mock
    private ApiFootballSyncFailureRetryScheduler apiFootballSyncFailureRetryScheduler;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private AdminService adminService;

    @Test
    void getFixtureTeamsReturnsOnlyTeamsParticipatingInTheSeason() {
        Team arsenal = Team.builder().teamId(42L).name("Arsenal").build();
        Team chelsea = Team.builder().teamId(49L).name("Chelsea").build();
        when(teamRepository.findAllWithFixtureInSeasonOrderByNameAsc(2025))
                .thenReturn(List.of(arsenal, chelsea));

        List<AdminDto.FixtureTeamOptionResponse> response = adminService.getFixtureTeams(2025);

        assertThat(response).extracting(AdminDto.FixtureTeamOptionResponse::getTeamId)
                .containsExactly(42L, 49L);
        verify(teamRepository).findAllWithFixtureInSeasonOrderByNameAsc(2025);
    }

    @Test
    void syncLeagueSeasonsRunsWithoutExistingCoverage() {
        AppUser admin = adminUser();

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(leagueSeasonCoverageSyncService.syncLeagueSeasons(39)).thenReturn(15);

        AdminDto.SyncResponse response = adminService.syncLeagueSeasons(1L, 39);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCount()).isEqualTo(15);
        verify(leagueSeasonCoverageSyncService).syncLeagueSeasons(39);
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getTargetType()).isEqualTo("LEAGUE");
        assertThat(logCaptor.getValue().getTargetId()).isEqualTo(39L);
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("league=39");
    }

    @Test
    void auditLogsExposeOnlyUsefulRequestParameters() {
        AppUser admin = adminUser();
        AdminAuditLog syncLog = AdminAuditLog.of(
                admin,
                AdminAuditType.SYNC,
                "FIXTURE",
                null,
                "fixtures sync completed. league=39; season=2025; count=380",
                "league=39; season=2025",
                true
        );
        AdminAuditLog fixtureLog = AdminAuditLog.of(
                admin,
                AdminAuditType.FIXTURE_UPDATE,
                "FIXTURE",
                1000L,
                "Fixture player stats updated: player=10",
                "shotsTotal: 2 -> 3",
                true
        );
        PageRequest pageable = PageRequest.of(0, 20);
        when(adminAuditLogRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(List.of(syncLog, fixtureLog), pageable, 2));

        AdminDto.AuditLogListResponse response = adminService.getAuditLogs(0, 20);

        assertThat(response.getLogs()).extracting(AdminDto.AuditLogResponse::getDetails)
                .containsExactly(
                        "leagueId=39; season=2025",
                        "fixtureId=1000; playerId=10"
                );
        assertThat(response.getLogs()).extracting(AdminDto.AuditLogResponse::getSyncCategory)
                .containsExactly("Fixtures", null);
        assertThat(response.getLogs().get(1).getDetails()).doesNotContain("shotsTotal", "2", "3");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateTeamMarksOnlyChangedFieldsAsManualOverrides() {
        AppUser admin = AppUser.builder()
                .email("admin@example.com")
                .password("password")
                .nickname("Admin")
                .build();
        Team team = Team.builder()
                .teamId(47L)
                .name("Old")
                .build();
        AdminDto.TeamUpdateRequest request = new AdminDto.TeamUpdateRequest(
                0L,
                "New Team",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));

        AdminDto.TeamAdminResponse response = adminService.updateTeam(1L, 47L, request);

        assertThat(response.getName()).isEqualTo("New Team");
        ArgumentCaptor<List<String>> fieldsCaptor = ArgumentCaptor.forClass(List.class);
        verify(adminOverrideService).markOverrides(eq(AdminOverrideTargetType.TEAM), eq(47L), fieldsCaptor.capture());
        assertThat(fieldsCaptor.getValue()).containsExactly("name");
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("name: Old -> New Team");
        verify(entityManager).lock(team, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        verify(entityManager).flush();
    }

    @Test
    void updateTeamRejectsStaleVersion() {
        Team team = Team.builder()
                .teamId(47L)
                .version(3L)
                .name("Current Team")
                .build();
        AdminDto.TeamUpdateRequest request = new AdminDto.TeamUpdateRequest();
        ReflectionTestUtils.setField(request, "version", 2L);
        ReflectionTestUtils.setField(request, "name", "Stale Team");

        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> adminService.updateTeam(1L, 47L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_EDIT_CONFLICT));
        assertThat(team.getName()).isEqualTo("Current Team");
        verifyNoInteractions(adminOverrideService, adminAuditLogRepository, entityManager);
    }

    @Test
    void updatePlayerRejectsMissingVersion() {
        Player player = Player.builder()
                .playerId(10L)
                .version(1L)
                .name("Current Player")
                .build();
        AdminDto.PlayerUpdateRequest request = new AdminDto.PlayerUpdateRequest();
        ReflectionTestUtils.setField(request, "name", "Stale Player");

        when(playerRepository.findByPlayerId(10L)).thenReturn(Optional.of(player));

        assertThatThrownBy(() -> adminService.updatePlayer(1L, 10L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_EDIT_CONFLICT));
        assertThat(player.getName()).isEqualTo("Current Player");
        verifyNoInteractions(adminOverrideService, adminAuditLogRepository, entityManager);
    }

    @Test
    void updateTeamRejectsBlankRequiredName() {
        AdminDto.TeamUpdateRequest request = new AdminDto.TeamUpdateRequest();
        ReflectionTestUtils.setField(request, "name", "   ");

        assertThatThrownBy(() -> adminService.updateTeam(1L, 47L, request))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_ADMIN_EDIT_FIELD));
        verifyNoInteractions(teamRepository);
    }

    @Test
    void syncPlayersQueuesBackgroundTask() {
        AppUser admin = AppUser.builder()
                .email("admin@example.com")
                .password("password")
                .nickname("Admin")
                .build();

        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminSyncJobService.create(
                1L, "players", "PLAYER", null, 2025, "league=39; season=2025"
        )).thenReturn(AdminSyncJob.builder().id(99L).build());
        when(leagueSeasonCoverageRepository.findByLeagueIdAndSeasonYear(39, 2025)).thenReturn(Optional.of(
                LeagueSeasonCoverage.builder()
                        .leagueId(39)
                        .leagueName("Premier League")
                        .seasonYear(2025)
                        .players(true)
                        .build()
        ));
        when(teamStandingRepository.existsByLeagueIdAndSeason(39, 2025)).thenReturn(true);

        AdminDto.SyncResponse response = adminService.syncPlayers(1L, 39, 2025, 7000L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isQueued()).isTrue();
        assertThat(response.getJobId()).isEqualTo(99L);
        verify(adminSyncTaskRunner).run(
                eq(99L),
                eq(1L),
                eq("players"),
                eq("PLAYER"),
                eq((Long) null),
                eq("league=39; season=2025"),
                any(AdminSyncTaskRunner.SyncTask.class),
                any(Runnable.class)
        );
    }

    @Test
    void successfulSynchronousAdminSyncCancelsPendingAutomaticRetry() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));
        when(leagueSeasonCoverageSyncService.syncLeagueSeasons(39)).thenReturn(3);

        AdminDto.SyncResponse response = adminService.syncLeagueSeasons(1L, 39);

        assertThat(response.isSuccess()).isTrue();
        verify(apiFootballSyncFailureRetryScheduler)
                .cancelPendingByExecutionKey("seasons:league=39");
    }

    @Test
    void failedAdminSyncDoesNotScheduleSlowRetry() {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));
        when(leagueSeasonCoverageSyncService.syncLeagueSeasons(39))
                .thenThrow(new RuntimeException("upstream failed"));

        AdminDto.SyncResponse response = adminService.syncLeagueSeasons(1L, 39);

        assertThat(response.isSuccess()).isFalse();
        verifyNoInteractions(apiFootballSyncFailureRetryScheduler);
    }

    @Test
    void queuedAdminSyncCancelsPendingRetryOnlyAfterTheWorkerTaskSucceeds() throws Exception {
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));
        when(adminSyncJobService.create(
                1L, "players", "PLAYER", null, 2025, "league=39; season=2025"
        )).thenReturn(AdminSyncJob.builder().id(99L).build());
        when(leagueSeasonCoverageRepository.findByLeagueIdAndSeasonYear(39, 2025)).thenReturn(Optional.of(
                LeagueSeasonCoverage.builder()
                        .leagueId(39)
                        .seasonYear(2025)
                        .players(true)
                        .build()
        ));
        when(teamStandingRepository.existsByLeagueIdAndSeason(39, 2025)).thenReturn(true);
        when(apiFootballPlayerSyncService.syncRegisteredPlayers(
                eq(39), eq(2025), eq(7000L), any(SyncProgressReporter.class)))
                .thenReturn(20);

        adminService.syncPlayers(1L, 39, 2025, 7000L);

        verify(apiFootballSyncFailureRetryScheduler, org.mockito.Mockito.never())
                .cancelPendingByExecutionKey(any());
        ArgumentCaptor<AdminSyncTaskRunner.SyncTask> taskCaptor =
                ArgumentCaptor.forClass(AdminSyncTaskRunner.SyncTask.class);
        verify(adminSyncTaskRunner).run(
                eq(99L), eq(1L), eq("players"), eq("PLAYER"), eq((Long) null),
                eq("league=39; season=2025"), taskCaptor.capture(), any(Runnable.class));

        taskCaptor.getValue().run(mock(SyncProgressReporter.class));

        verify(apiFootballSyncFailureRetryScheduler)
                .cancelPendingByExecutionKey("players:league=39; season=2025");
    }

    @Test
    void syncPlayersDoesNotQueueWhenSameJobIsAlreadyActive() {
        when(leagueSeasonCoverageRepository.findByLeagueIdAndSeasonYear(39, 2025)).thenReturn(Optional.of(
                LeagueSeasonCoverage.builder()
                        .leagueId(39)
                        .seasonYear(2025)
                        .players(true)
                        .build()
        ));
        when(teamStandingRepository.existsByLeagueIdAndSeason(39, 2025)).thenReturn(true);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));
        when(adminSyncJobService.hasActiveJob("players", "league=39; season=2025")).thenReturn(true);

        assertThatThrownBy(() -> adminService.syncPlayers(1L, 39, 2025, 7000L))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_SYNC_ALREADY_RUNNING));

        verify(adminSyncJobService, org.mockito.Mockito.never())
                .create(any(), any(), any(), any(), any(), any());
        verify(adminSyncTaskRunner, org.mockito.Mockito.never())
                .run(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void syncPlayersRequiresStandingsBeforeCreatingJob() {
        when(leagueSeasonCoverageRepository.findByLeagueIdAndSeasonYear(39, 2025)).thenReturn(Optional.of(
                LeagueSeasonCoverage.builder()
                        .leagueId(39)
                        .seasonYear(2025)
                        .players(true)
                        .build()
        ));
        when(teamStandingRepository.existsByLeagueIdAndSeason(39, 2025)).thenReturn(false);

        assertThatThrownBy(() -> adminService.syncPlayers(1L, 39, 2025, 7000L))
                .isInstanceOf(CustomException.class)
                .hasMessage("선수 동기화 전에 해당 시즌의 팀과 순위를 먼저 동기화해 주세요.");

        verify(adminSyncJobService, org.mockito.Mockito.never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void cancelQueuedSyncJobReturnsCancelledStatusAndWritesAuditLog() {
        AppUser admin = adminUser();
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminSyncJobService.requestCancel(99L)).thenReturn(new AdminSyncJobService.CancelResult(
                99L, "players", "league=39; season=2025", AdminSyncJobStatus.CANCELLED, true));

        AdminDto.SyncCancelResponse response = adminService.cancelSyncJob(1L, 99L);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getMessage()).contains("cancelled before start");
    }

    @Test
    void clearTeamOverrideRemovesOneManualOverrideAndWritesAuditLog() {
        AppUser admin = adminUser();
        Team team = Team.builder()
                .teamId(47L)
                .name("Manual Team")
                .build();

        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.clearOverride(AdminOverrideTargetType.TEAM, 47L, "name")).thenReturn(1L);
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.TEAM, 47L)).thenReturn(List.of());

        AdminDto.TeamAdminResponse response = adminService.clearTeamOverride(1L, 47L, "name", 0L);

        assertThat(response.getManualOverrides()).isEmpty();
        verify(entityManager).lock(team, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        verify(entityManager).flush();
        verify(adminOverrideService).clearOverride(AdminOverrideTargetType.TEAM, 47L, "name");
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getType()).isEqualTo(AdminAuditType.OVERRIDE_CLEAR);
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("field=name; deletedCount=1");
    }

    @Test
    void clearTeamOverridesRemovesAllManualOverrides() {
        AppUser admin = adminUser();
        Team team = Team.builder()
                .teamId(47L)
                .name("Manual Team")
                .build();

        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.clearOverrides(AdminOverrideTargetType.TEAM, 47L)).thenReturn(3L);
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.TEAM, 47L)).thenReturn(List.of());

        AdminDto.TeamAdminResponse response = adminService.clearTeamOverrides(1L, 47L, 0L);

        assertThat(response.getManualOverrides()).isEmpty();
        verify(adminOverrideService).clearOverrides(AdminOverrideTargetType.TEAM, 47L);
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("field=ALL; deletedCount=3");
    }

    @Test
    void clearTeamOverrideRejectsStaleVersionBeforeDeletingOverride() {
        Team team = Team.builder()
                .teamId(47L)
                .version(3L)
                .name("Current Team")
                .build();
        when(teamRepository.findByTeamId(47L)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> adminService.clearTeamOverride(1L, 47L, "name", 2L))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_EDIT_CONFLICT));

        verifyNoInteractions(adminOverrideService, entityManager);
    }

    @Test
    void clearPlayerOverrideRemovesOneManualOverrideAndWritesAuditLog() {
        AppUser admin = adminUser();
        Player player = Player.builder()
                .playerId(10L)
                .name("Manual Player")
                .build();

        when(playerRepository.findByPlayerId(10L)).thenReturn(Optional.of(player));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.clearOverride(AdminOverrideTargetType.PLAYER, 10L, "photoUrl")).thenReturn(1L);
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.PLAYER, 10L)).thenReturn(List.of());

        AdminDto.PlayerAdminResponse response = adminService.clearPlayerOverride(1L, 10L, "photoUrl", 0L);

        assertThat(response.getManualOverrides()).isEmpty();
        verify(adminOverrideService).clearOverride(AdminOverrideTargetType.PLAYER, 10L, "photoUrl");
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getType()).isEqualTo(AdminAuditType.OVERRIDE_CLEAR);
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("field=photoUrl; deletedCount=1");
    }

    @Test
    void clearPlayerOverridesRemovesAllManualOverrides() {
        AppUser admin = adminUser();
        Player player = Player.builder()
                .playerId(10L)
                .name("Manual Player")
                .build();

        when(playerRepository.findByPlayerId(10L)).thenReturn(Optional.of(player));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.clearOverrides(AdminOverrideTargetType.PLAYER, 10L)).thenReturn(2L);
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.PLAYER, 10L)).thenReturn(List.of());

        AdminDto.PlayerAdminResponse response = adminService.clearPlayerOverrides(1L, 10L, 0L);

        assertThat(response.getManualOverrides()).isEmpty();
        verify(adminOverrideService).clearOverrides(AdminOverrideTargetType.PLAYER, 10L);
        ArgumentCaptor<AdminAuditLog> logCaptor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getDetails()).isEqualTo("field=ALL; deletedCount=2");
    }

    @Test
    void clearOverrideRejectsUnknownFieldName() {
        assertThatThrownBy(() -> adminService.clearTeamOverride(1L, 47L, "unknown", 0L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void updateFixtureConvertsKoreaTimeToUtcAndUpdatesTimestamp() {
        AppUser admin = adminUser();
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureDate(LocalDateTime.of(2025, 8, 22, 14, 0))
                .timestamp(1755871200L)
                .build();
        AdminDto.FixtureUpdateRequest request = new AdminDto.FixtureUpdateRequest();
        ReflectionTestUtils.setField(request, "version", 0L);
        OffsetDateTime koreaKickoff = OffsetDateTime.parse("2025-08-22T23:30:00+09:00");
        ReflectionTestUtils.setField(request, "fixtureDate", koreaKickoff);

        when(fixtureRepository.findWithTeamsByFixtureId(1000L)).thenReturn(Optional.of(fixture));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(fixtureEventRepository.findAllByFixtureFixtureIdOrderByMatchTimeAsc(1000L)).thenReturn(List.of());
        when(fixtureLineupRepository.findAllByFixtureId(1000L)).thenReturn(List.of());
        when(fixtureStatRepository.findAllByFixtureFixtureId(1000L)).thenReturn(List.of());
        when(playerFixtureStatRepository.findAllByFixtureFixtureId(1000L)).thenReturn(List.of());

        AdminDto.FixtureAdminDetailResponse response = adminService.updateFixture(1L, 1000L, request);

        assertThat(fixture.getFixtureDate()).isEqualTo(LocalDateTime.of(2025, 8, 22, 14, 30));
        assertThat(fixture.getTimestamp()).isEqualTo(koreaKickoff.toInstant().getEpochSecond());
        assertThat(response.getFixture().getFixtureDate()).isEqualTo(koreaKickoff);
        verify(entityManager).lock(fixture, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        verify(adminOverrideService).markOverrides(
                AdminOverrideTargetType.FIXTURE,
                1000L,
                List.of("fixtureDate")
        );
        verify(fixtureRedisService).evictFixtureCaches(1000L);
    }

    @Test
    void updatesFixtureLineupWithVersionAndMarksChangedFieldsOverridden() {
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Player player = Player.builder().playerId(10L).name("Player").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureDate(LocalDateTime.of(2026, 8, 7, 10, 0))
                .fixtureStatus("SCHEDULED")
                .build();
        FixtureLineup lineup = FixtureLineup.builder()
                .fixture(fixture)
                .team(home)
                .player(player)
                .jerseyNumber(7)
                .position("M")
                .grid("2:1")
                .isStarter(false)
                .build();
        ReflectionTestUtils.setField(lineup, "id", 301L);
        ReflectionTestUtils.setField(lineup, "version", 2L);
        AdminDto.FixtureLineupUpdateRequest request = new AdminDto.FixtureLineupUpdateRequest();
        ReflectionTestUtils.setField(request, "version", 2L);
        ReflectionTestUtils.setField(request, "jerseyNumber", 8);
        ReflectionTestUtils.setField(request, "position", "D");
        ReflectionTestUtils.setField(request, "grid", "1:2");
        ReflectionTestUtils.setField(request, "starter", true);

        when(fixtureRepository.findWithTeamsByFixtureId(1000L)).thenReturn(Optional.of(fixture));
        when(fixtureLineupRepository.findAllByFixtureId(1000L)).thenReturn(List.of(lineup));
        when(fixtureLineupRepository.findByFixtureFixtureIdAndTeamTeamIdAndPlayerPlayerId(1000L, 42L, 10L))
                .thenReturn(Optional.of(lineup));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));

        AdminDto.FixtureAdminDetailResponse response =
                adminService.updateFixtureLineup(1L, 1000L, 42L, 10L, request);

        assertThat(lineup.getJerseyNumber()).isEqualTo(8);
        assertThat(lineup.getPosition()).isEqualTo("D");
        assertThat(lineup.getGrid()).isEqualTo("1:2");
        assertThat(lineup.isStarter()).isTrue();
        assertThat(response.getLineups()).singleElement().satisfies(updated -> {
            assertThat(updated.getLineupId()).isEqualTo(301L);
            assertThat(updated.getVersion()).isEqualTo(2L);
        });
        verify(entityManager).lock(lineup, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        verify(adminOverrideService).markOverrides(
                AdminOverrideTargetType.FIXTURE_LINEUP,
                301L,
                List.of("jerseyNumber", "position", "grid", "starter")
        );
        verify(entityManager).flush();
        verify(fixtureRedisService).evictFixtureCaches(1000L);
    }

    @Test
    void rejectsEventAndStatEditsUntilFixtureIsFinished() {
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureStatus("LIVE")
                .statusShort("2H")
                .build();
        when(fixtureRepository.findWithTeamsByFixtureId(1000L)).thenReturn(Optional.of(fixture));
        when(fixtureRepository.findByFixtureIdForEventUpdate(1000L)).thenReturn(Optional.of(fixture));

        assertNotFinished(() -> adminService.createFixtureEvent(
                1L, 1000L, new AdminDto.FixtureEventUpdateRequest()));
        assertNotFinished(() -> adminService.updateFixtureEvent(
                1L, 1000L, 1, new AdminDto.FixtureEventUpdateRequest()));
        assertNotFinished(() -> adminService.deleteFixtureEvent(1L, 1000L, 1, 0L));
        assertNotFinished(() -> adminService.updateFixtureTeamStat(
                1L, 1000L, 42L, new AdminDto.FixtureTeamStatUpdateRequest()));
        assertNotFinished(() -> adminService.updateFixturePlayerStat(
                1L, 1000L, 10L, new AdminDto.FixturePlayerStatUpdateRequest()));

        verifyNoInteractions(
                fixtureEventRepository,
                fixtureStatRepository,
                playerFixtureStatRepository,
                fixtureRedisService,
                adminAuditLogRepository
        );
    }

    @Test
    void createsFixtureEventAfterLockingFixtureAndMarksWholeEventCollectionOverridden() {
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureStatus("FINISHED")
                .build();
        AdminDto.FixtureEventUpdateRequest request = new AdminDto.FixtureEventUpdateRequest(
                0L, 10, null, null, null, null, "Goal", "Normal Goal", null);
        when(fixtureRepository.findByFixtureIdForEventUpdate(1000L)).thenReturn(Optional.of(fixture));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.FIXTURE_EVENT, 1000L))
                .thenReturn(List.of(new AdminOverrideService.OverrideInfo(
                        "events", LocalDateTime.of(2026, 8, 7, 10, 30))));
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.FIXTURE, 1000L))
                .thenReturn(List.of());

        AdminDto.FixtureAdminDetailResponse response =
                adminService.createFixtureEvent(1L, 1000L, request);

        verify(fixtureRepository).findByFixtureIdForEventUpdate(1000L);
        ArgumentCaptor<FixtureEvent> eventCaptor = ArgumentCaptor.forClass(FixtureEvent.class);
        verify(fixtureEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventSequence()).isEqualTo(1);
        verify(adminOverrideService).markOverrides(
                AdminOverrideTargetType.FIXTURE_EVENT,
                1000L,
                List.of("events")
        );
        verify(entityManager).lock(fixture, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        verify(entityManager).flush();
        assertThat(response.getEventOverrides()).singleElement().satisfies(override -> {
            assertThat(override.getFieldName()).isEqualTo("events");
            assertThat(override.getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 8, 7, 10, 30));
        });
    }

    @Test
    void createsFixtureEventAfterHighestSequenceWhenEarlierEventWasDeleted() {
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureStatus("FINISHED")
                .build();
        AdminDto.FixtureEventUpdateRequest request = new AdminDto.FixtureEventUpdateRequest(
                0L, 10, null, null, null, null, "Goal", "Normal Goal", null);
        when(fixtureRepository.findByFixtureIdForEventUpdate(1000L)).thenReturn(Optional.of(fixture));
        when(fixtureEventRepository.findMaxEventSequenceByFixtureId(1000L)).thenReturn(3);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));

        adminService.createFixtureEvent(1L, 1000L, request);

        ArgumentCaptor<FixtureEvent> eventCaptor = ArgumentCaptor.forClass(FixtureEvent.class);
        verify(fixtureEventRepository).save(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventSequence()).isEqualTo(4);
    }

    @Test
    void deletesFixtureEventAfterLockingFixtureAndMarksWholeEventCollectionOverridden() {
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureStatus("FINISHED")
                .build();
        FixtureEvent event = FixtureEvent.builder()
                .fixture(fixture)
                .eventSequence(3)
                .elapsed(45)
                .eventType("Goal")
                .eventDetail("Normal Goal")
                .build();
        when(fixtureRepository.findByFixtureIdForEventUpdate(1000L)).thenReturn(Optional.of(fixture));
        when(fixtureEventRepository.findByFixtureFixtureIdAndEventSequence(1000L, 3))
                .thenReturn(Optional.of(event));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));

        AdminDto.FixtureAdminDetailResponse response = adminService.deleteFixtureEvent(1L, 1000L, 3, 0L);

        verify(fixtureRepository).findByFixtureIdForEventUpdate(1000L);
        verify(fixtureEventRepository).delete(event);
        verify(adminOverrideService).markOverrides(
                AdminOverrideTargetType.FIXTURE_EVENT,
                1000L,
                List.of("events")
        );
        verify(fixtureRedisService).evictFixtureCaches(1000L);
        assertThat(response.getEvents()).isEmpty();
    }

    @Test
    void clearFixtureEventOverrideRejectsStaleFixtureVersionWhileHoldingFixtureLock() {
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .version(4L)
                .fixtureStatus("FINISHED")
                .build();
        when(fixtureRepository.findByFixtureIdForEventUpdate(1000L)).thenReturn(Optional.of(fixture));

        assertThatThrownBy(() -> adminService.clearFixtureEventOverride(
                1L, 1000L, "events", 3L))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_EDIT_CONFLICT));

        verify(fixtureRepository).findByFixtureIdForEventUpdate(1000L);
        verifyNoInteractions(adminOverrideService, entityManager);
    }

    @Test
    void updateFixtureEventRejectsStaleFixtureVersionAfterAcquiringFixtureLock() {
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .version(4L)
                .fixtureStatus("FINISHED")
                .build();
        AdminDto.FixtureEventUpdateRequest request = new AdminDto.FixtureEventUpdateRequest();
        ReflectionTestUtils.setField(request, "version", 3L);
        when(fixtureRepository.findByFixtureIdForEventUpdate(1000L)).thenReturn(Optional.of(fixture));

        assertThatThrownBy(() -> adminService.updateFixtureEvent(1L, 1000L, 1, request))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.ADMIN_EDIT_CONFLICT));

        verify(fixtureRepository).findByFixtureIdForEventUpdate(1000L);
        verifyNoInteractions(fixtureEventRepository, adminOverrideService, entityManager);
    }

    @Test
    void clearsFixtureEventAndStatOverridesUsingTheirStoredTargetIds() {
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Player player = Player.builder().playerId(10L).name("Player").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureStatus("FINISHED")
                .build();
        FixtureStat teamStat = FixtureStat.builder()
                .id(101L)
                .fixture(fixture)
                .team(home)
                .build();
        PlayerFixtureStat playerStat = PlayerFixtureStat.builder()
                .id(201L)
                .fixture(fixture)
                .team(home)
                .player(player)
                .build();
        when(fixtureRepository.findByFixtureIdForEventUpdate(1000L)).thenReturn(Optional.of(fixture));
        when(fixtureRepository.findWithTeamsByFixtureId(1000L)).thenReturn(Optional.of(fixture));
        when(fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(1000L, 42L))
                .thenReturn(Optional.of(teamStat));
        when(playerFixtureStatRepository.findByFixtureFixtureIdAndPlayerPlayerId(1000L, 10L))
                .thenReturn(Optional.of(playerStat));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(adminUser()));

        adminService.clearFixtureEventOverrides(1L, 1000L, 0L);
        adminService.clearFixtureTeamStatOverrides(1L, 1000L, 42L, 0L);
        adminService.clearFixturePlayerStatOverrides(1L, 1000L, 10L, 0L);
        adminService.clearFixtureEventOverride(1L, 1000L, "events", 0L);
        adminService.clearFixtureTeamStatOverride(1L, 1000L, 42L, "totalShots", 0L);
        adminService.clearFixturePlayerStatOverride(1L, 1000L, 10L, "rating", 0L);

        verify(adminOverrideService).clearOverrides(AdminOverrideTargetType.FIXTURE_EVENT, 1000L);
        verify(adminOverrideService).clearOverrides(AdminOverrideTargetType.FIXTURE_TEAM_STAT, 101L);
        verify(adminOverrideService).clearOverrides(AdminOverrideTargetType.FIXTURE_PLAYER_STAT, 201L);
        verify(adminOverrideService).clearOverride(AdminOverrideTargetType.FIXTURE_EVENT, 1000L, "events");
        verify(adminOverrideService).clearOverride(
                AdminOverrideTargetType.FIXTURE_TEAM_STAT, 101L, "totalShots");
        verify(adminOverrideService).clearOverride(
                AdminOverrideTargetType.FIXTURE_PLAYER_STAT, 201L, "rating");
        verify(entityManager, times(2)).lock(fixture, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
        verify(entityManager, times(2)).lock(teamStat, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        verify(entityManager, times(2)).lock(playerStat, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
        verify(entityManager, times(6)).flush();
    }

    @Test
    void rejectsUnknownFixtureStatOverrideFieldBeforeLoadingFixture() {
        assertThatThrownBy(() -> adminService.clearFixtureTeamStatOverride(
                1L, 1000L, 42L, "unknown", 0L))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.INVALID_ADMIN_OVERRIDE_FIELD));

        verifyNoInteractions(fixtureRepository, fixtureStatRepository, adminOverrideService);
    }

    @Test
    void rejectsStaleFixtureStatVersionsBeforeChangingValues() {
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Player player = Player.builder().playerId(10L).name("Player").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureStatus("FINISHED")
                .build();
        FixtureStat teamStat = FixtureStat.builder()
                .id(101L)
                .fixture(fixture)
                .team(home)
                .version(3L)
                .totalShots(12)
                .build();
        PlayerFixtureStat playerStat = PlayerFixtureStat.builder()
                .id(201L)
                .fixture(fixture)
                .team(home)
                .player(player)
                .version(5L)
                .goals(1)
                .build();
        AdminDto.FixtureTeamStatUpdateRequest teamRequest = new AdminDto.FixtureTeamStatUpdateRequest();
        ReflectionTestUtils.setField(teamRequest, "version", 2L);
        ReflectionTestUtils.setField(teamRequest, "totalShots", 99);
        AdminDto.FixturePlayerStatUpdateRequest playerRequest = new AdminDto.FixturePlayerStatUpdateRequest();
        ReflectionTestUtils.setField(playerRequest, "version", 4L);
        ReflectionTestUtils.setField(playerRequest, "goals", 9);

        when(fixtureRepository.findWithTeamsByFixtureId(1000L)).thenReturn(Optional.of(fixture));
        when(fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(1000L, 42L)).thenReturn(Optional.of(teamStat));
        when(playerFixtureStatRepository.findByFixtureFixtureIdAndPlayerPlayerId(1000L, 10L))
                .thenReturn(Optional.of(playerStat));

        assertEditConflict(() -> adminService.updateFixtureTeamStat(1L, 1000L, 42L, teamRequest));
        assertEditConflict(() -> adminService.updateFixturePlayerStat(1L, 1000L, 10L, playerRequest));

        assertThat(teamStat.getTotalShots()).isEqualTo(12);
        assertThat(playerStat.getGoals()).isEqualTo(1);
        verifyNoInteractions(adminOverrideService, entityManager, fixtureRedisService, adminAuditLogRepository);
    }

    @Test
    void returnsCurrentFixtureStatVersionsAfterSuccessfulSaves() {
        AppUser admin = adminUser();
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Player player = Player.builder().playerId(10L).name("Player").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureStatus("FINISHED")
                .build();
        FixtureStat teamStat = FixtureStat.builder()
                .id(101L)
                .fixture(fixture)
                .team(home)
                .version(3L)
                .build();
        PlayerFixtureStat playerStat = PlayerFixtureStat.builder()
                .id(201L)
                .fixture(fixture)
                .team(home)
                .player(player)
                .version(5L)
                .build();
        AdminDto.FixtureTeamStatUpdateRequest teamRequest = new AdminDto.FixtureTeamStatUpdateRequest();
        ReflectionTestUtils.setField(teamRequest, "version", 3L);
        ReflectionTestUtils.setField(teamRequest, "totalShots", 10);
        AdminDto.FixturePlayerStatUpdateRequest playerRequest = new AdminDto.FixturePlayerStatUpdateRequest();
        ReflectionTestUtils.setField(playerRequest, "version", 5L);
        ReflectionTestUtils.setField(playerRequest, "rating", 7.5);

        when(fixtureRepository.findWithTeamsByFixtureId(1000L)).thenReturn(Optional.of(fixture));
        when(fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(1000L, 42L)).thenReturn(Optional.of(teamStat));
        when(playerFixtureStatRepository.findByFixtureFixtureIdAndPlayerPlayerId(1000L, 10L))
                .thenReturn(Optional.of(playerStat));
        when(fixtureEventRepository.findAllByFixtureFixtureIdOrderByMatchTimeAsc(1000L)).thenReturn(List.of());
        when(fixtureLineupRepository.findAllByFixtureId(1000L)).thenReturn(List.of());
        when(fixtureStatRepository.findAllByFixtureFixtureId(1000L)).thenReturn(List.of(teamStat));
        when(playerFixtureStatRepository.findAllByFixtureFixtureId(1000L)).thenReturn(List.of(playerStat));
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.FIXTURE_EVENT, 1000L))
                .thenReturn(List.of());
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.FIXTURE, 1000L))
                .thenReturn(List.of());
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.FIXTURE_TEAM_STAT, 101L))
                .thenReturn(List.of(new AdminOverrideService.OverrideInfo("totalShots", null)));
        when(adminOverrideService.overrideInfos(AdminOverrideTargetType.FIXTURE_PLAYER_STAT, 201L))
                .thenReturn(List.of(new AdminOverrideService.OverrideInfo("rating", null)));

        AdminDto.FixtureAdminDetailResponse teamResponse =
                adminService.updateFixtureTeamStat(1L, 1000L, 42L, teamRequest);
        AdminDto.FixtureAdminDetailResponse playerResponse =
                adminService.updateFixturePlayerStat(1L, 1000L, 10L, playerRequest);

        assertThat(teamResponse.getTeamStats().get(0).getVersion()).isEqualTo(3L);
        assertThat(teamResponse.getTeamStats().get(0).getManualOverrides())
                .extracting(AdminDto.ManualOverrideResponse::getFieldName)
                .containsExactly("totalShots");
        assertThat(playerResponse.getPlayerStats().get(0).getVersion()).isEqualTo(5L);
        assertThat(playerResponse.getPlayerStats().get(0).getManualOverrides())
                .extracting(AdminDto.ManualOverrideResponse::getFieldName)
                .containsExactly("rating");
        verify(adminOverrideService).markOverrides(
                AdminOverrideTargetType.FIXTURE_TEAM_STAT, 101L, List.of("totalShots"));
        verify(adminOverrideService).markOverrides(
                AdminOverrideTargetType.FIXTURE_PLAYER_STAT, 201L, List.of("rating"));
        verify(entityManager, times(2)).flush();
    }

    private void assertNotFinished(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_FIXTURE_NOT_FINISHED));
    }

    private void assertEditConflict(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ADMIN_EDIT_CONFLICT));
    }

    private AppUser adminUser() {
        return AppUser.builder()
                .email("admin@example.com")
                .password("password")
                .nickname("Admin")
                .build();
    }
}

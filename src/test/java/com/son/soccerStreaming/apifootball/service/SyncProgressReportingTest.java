package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.media.service.ImageCacheService;
import com.son.soccerStreaming.player.repository.PlayerAbsenceRepository;
import com.son.soccerStreaming.player.repository.PlayerRepository;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
import com.son.soccerStreaming.player.service.PlayerTeamSeasonStatAggregationService;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncProgressReportingTest {

    @Test
    void playerSyncFailsWhenStandingsHaveNotBeenSynchronized() {
        TeamRepository teamRepository = mock(TeamRepository.class);
        TeamStandingRepository standingRepository = mock(TeamStandingRepository.class);
        when(standingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of());
        SyncProgressReporter reporter = mock(SyncProgressReporter.class);
        ApiFootballPlayerSyncService service = new ApiFootballPlayerSyncService(
                mock(ApiFootballClient.class), mock(FixtureLineupRepository.class), mock(PlayerRepository.class),
                mock(PlayerTeamSeasonStatRepository.class), teamRepository, standingRepository,
                mock(AdminOverrideService.class),
                mock(OptimisticLockRetryExecutor.class), mock(EntityManager.class), mock(ApiFootballSyncStatusService.class),
                mock(ImageCacheService.class), mock(PlayerTeamSeasonStatAggregationService.class)
        );

        assertThatThrownBy(() -> service.syncRegisteredPlayers(39, 2025, 0L, reporter))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Team standings must be synchronized before player sync");

        verify(teamRepository, never()).findAllWithFixtureInSeasonOrderByNameAsc(any());
    }

    @Test
    void injurySyncInitializesRecordProgressAndKeepsExistingReturnContract() {
        ApiFootballClient client = mock(ApiFootballClient.class);
        when(client.getInjuries(39, 2025)).thenReturn(List.of());
        SyncProgressReporter reporter = mock(SyncProgressReporter.class);
        ApiFootballInjurySyncService service = new ApiFootballInjurySyncService(
                client, mock(FixtureRepository.class), mock(TeamRepository.class),
                mock(PlayerAbsenceRepository.class), mock(ApiFootballPlayerSyncService.class),
                mock(TransactionTemplate.class), mock(EntityManager.class), mock(ApiFootballSyncStatusService.class)
        );

        int count = service.syncInjuries(39, 2025, reporter);

        assertThat(count).isZero();
        verify(reporter).beginPhase("FETCHING_INJURIES", 0, "request", 0);
        verify(reporter).beginPhase("SYNCING_INJURIES", 0, "injuries", 0);
    }

    @Test
    void playerSyncUsesStandingTeamsWithoutFixtureFallback() {
        TeamRepository teamRepository = mock(TeamRepository.class);
        TeamStandingRepository standingRepository = mock(TeamStandingRepository.class);
        Team team = Team.builder().teamId(42L).name("Arsenal").build();
        when(standingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(
                TeamStanding.builder().team(team).leagueId(39).season(2025).build()));
        SyncProgressReporter reporter = mock(SyncProgressReporter.class);
        org.mockito.Mockito.doThrow(new SyncCancelledException()).when(reporter).checkCancelled();
        ApiFootballPlayerSyncService service = new ApiFootballPlayerSyncService(
                mock(ApiFootballClient.class), mock(FixtureLineupRepository.class), mock(PlayerRepository.class),
                mock(PlayerTeamSeasonStatRepository.class), teamRepository, standingRepository,
                mock(AdminOverrideService.class), mock(OptimisticLockRetryExecutor.class), mock(EntityManager.class),
                mock(ApiFootballSyncStatusService.class), mock(ImageCacheService.class),
                mock(PlayerTeamSeasonStatAggregationService.class)
        );

        assertThatThrownBy(() -> service.syncRegisteredPlayers(39, 2025, 0L, reporter))
                .isInstanceOf(SyncCancelledException.class);

        verify(reporter).beginPhase("SYNCING_PLAYERS", 1, "teams", 0);
        verify(teamRepository, never()).findAllWithFixtureInSeasonOrderByNameAsc(any());
    }
}

package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballInjuryDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.repository.PlayerAbsenceRepository;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiFootballInjuryReferenceSyncTest {

    @Test
    void missingFixtureIsNotFoundAndIncludesTheMissingId() {
        ApiFootballClient client = mock(ApiFootballClient.class);
        FixtureRepository fixtureRepository = mock(FixtureRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        ApiFootballSyncStatusService statusService = mock(ApiFootballSyncStatusService.class);
        TransactionTemplate transactionTemplate = immediateTransactionTemplate();
        SyncProgressReporter reporter = mock(SyncProgressReporter.class);
        ApiFootballInjuryDto.InjuryResponse injury = injury(100L, 200L, 300L);
        when(client.getInjuries(39, 2025)).thenReturn(List.of(injury));
        when(fixtureRepository.findByFixtureId(100L)).thenReturn(Optional.empty());
        when(teamRepository.findByTeamId(200L))
                .thenReturn(Optional.of(Team.builder().teamId(200L).name("Arsenal").build()));
        ApiFootballInjurySyncService service = new ApiFootballInjurySyncService(
                client,
                fixtureRepository,
                teamRepository,
                mock(PlayerAbsenceRepository.class),
                mock(ApiFootballPlayerSyncService.class),
                transactionTemplate,
                mock(EntityManager.class),
                statusService
        );

        assertThatThrownBy(() -> service.syncInjuries(39, 2025, reporter))
                .isInstanceOfSatisfying(ApiFootballInjuryReferenceSyncException.class, failure -> {
                    org.assertj.core.api.Assertions.assertThat(failure.getMissingFixtureCount()).isEqualTo(1);
                    org.assertj.core.api.Assertions.assertThat(failure.getMissingTeamCount()).isZero();
                    org.assertj.core.api.Assertions.assertThat(failure.getMissingPlayerCount()).isZero();
                    assertThat(failure.getCategory())
                            .isEqualTo(com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory.NOT_FOUND);
                    assertThat(failure.getMissingFixtureIds()).containsExactly(100L);
                });

        verify(reporter).update(1, 0, 1, 0);
        verify(statusService).recordFailure(
                org.mockito.ArgumentMatchers.eq("injuries"),
                org.mockito.ArgumentMatchers.eq("Injuries"),
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.any(ApiFootballInjuryReferenceSyncException.class)
        );
        verify(statusService, never()).recordSuccess("injuries", "Injuries", 2025);
    }

    @Test
    void invalidPayloadIsRecordedAsInvalidResponseFailure() {
        ApiFootballClient client = mock(ApiFootballClient.class);
        ApiFootballSyncStatusService statusService = mock(ApiFootballSyncStatusService.class);
        SyncProgressReporter reporter = mock(SyncProgressReporter.class);
        when(client.getInjuries(39, 2025)).thenReturn(List.of(new ApiFootballInjuryDto.InjuryResponse()));
        ApiFootballInjurySyncService service = new ApiFootballInjurySyncService(
                client,
                mock(FixtureRepository.class),
                mock(TeamRepository.class),
                mock(PlayerAbsenceRepository.class),
                mock(ApiFootballPlayerSyncService.class),
                immediateTransactionTemplate(),
                mock(EntityManager.class),
                statusService
        );

        assertThatThrownBy(() -> service.syncInjuries(39, 2025, reporter))
                .isInstanceOfSatisfying(ApiFootballInjuryReferenceSyncException.class, failure -> {
                    assertThat(failure.getCategory())
                            .isEqualTo(com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory.INVALID_RESPONSE);
                    assertThat(failure.getInvalidPayloadCount()).isEqualTo(1);
                    assertThat(failure.getMissingFixtureIds()).isEmpty();
                    assertThat(failure.getMissingTeamIds()).isEmpty();
                    assertThat(failure.getMissingPlayerIds()).isEmpty();
                });

        verify(reporter).update(1, 0, 1, 0);
        verify(statusService).recordFailure(
                org.mockito.ArgumentMatchers.eq("injuries"),
                org.mockito.ArgumentMatchers.eq("Injuries"),
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.any(ApiFootballInjuryReferenceSyncException.class)
        );
        verify(statusService, never()).recordSuccess("injuries", "Injuries", 2025);
    }

    @Test
    void missingTeamAndPlayerIdsAreIncludedInNotFoundFailure() {
        ApiFootballClient client = mock(ApiFootballClient.class);
        FixtureRepository fixtureRepository = mock(FixtureRepository.class);
        TeamRepository teamRepository = mock(TeamRepository.class);
        ApiFootballPlayerSyncService playerSyncService = mock(ApiFootballPlayerSyncService.class);
        ApiFootballSyncStatusService statusService = mock(ApiFootballSyncStatusService.class);
        when(client.getInjuries(39, 2025)).thenReturn(List.of(
                injury(101L, 201L, 301L),
                injury(102L, 202L, 302L)
        ));
        when(fixtureRepository.findByFixtureId(101L)).thenReturn(Optional.of(mock(Fixture.class)));
        when(fixtureRepository.findByFixtureId(102L)).thenReturn(Optional.of(mock(Fixture.class)));
        when(teamRepository.findByTeamId(201L)).thenReturn(Optional.empty());
        Team team = Team.builder().teamId(202L).name("Chelsea").build();
        when(teamRepository.findByTeamId(202L)).thenReturn(Optional.of(team));
        when(playerSyncService.findOrFetchPlayer(
                org.mockito.ArgumentMatchers.eq(302L),
                org.mockito.ArgumentMatchers.eq("Player"),
                org.mockito.ArgumentMatchers.same(team),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()
        )).thenReturn(Optional.empty());
        ApiFootballInjurySyncService service = new ApiFootballInjurySyncService(
                client,
                fixtureRepository,
                teamRepository,
                mock(PlayerAbsenceRepository.class),
                playerSyncService,
                immediateTransactionTemplate(),
                mock(EntityManager.class),
                statusService
        );

        assertThatThrownBy(() -> service.syncInjuries(39, 2025))
                .isInstanceOfSatisfying(ApiFootballInjuryReferenceSyncException.class, failure -> {
                    assertThat(failure.getCategory())
                            .isEqualTo(com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory.NOT_FOUND);
                    assertThat(failure.getMissingTeamIds()).containsExactly(201L);
                    assertThat(failure.getMissingPlayerIds()).containsExactly(302L);
                });
    }

    private TransactionTemplate immediateTransactionTemplate() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return transactionTemplate;
    }

    private ApiFootballInjuryDto.InjuryResponse injury(Long fixtureId, Long teamId, Long playerId) {
        ApiFootballInjuryDto.FixtureInfo fixture = new ApiFootballInjuryDto.FixtureInfo();
        ReflectionTestUtils.setField(fixture, "id", fixtureId);
        ApiFootballInjuryDto.TeamInfo team = new ApiFootballInjuryDto.TeamInfo();
        ReflectionTestUtils.setField(team, "id", teamId);
        ApiFootballInjuryDto.PlayerInfo player = new ApiFootballInjuryDto.PlayerInfo();
        ReflectionTestUtils.setField(player, "id", playerId);
        ReflectionTestUtils.setField(player, "name", "Player");
        ApiFootballInjuryDto.InjuryResponse injury = new ApiFootballInjuryDto.InjuryResponse();
        ReflectionTestUtils.setField(injury, "fixture", fixture);
        ReflectionTestUtils.setField(injury, "team", team);
        ReflectionTestUtils.setField(injury, "player", player);
        return injury;
    }
}

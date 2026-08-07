package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.admin.entity.AdminFieldOverride;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.repository.AdminFieldOverrideRepository;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.PlayerFixtureStat;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiFootballFixturePlayerStatSyncServiceTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private FixtureRepository fixtureRepository;
    @Mock private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ApiFootballPlayerSyncService playerSyncService;
    @Mock private AdminFieldOverrideRepository adminFieldOverrideRepository;

    @Test
    void preservesOverriddenPlayerStatEvenWhenApiNormalizationWouldClearIt() {
        AdminOverrideService overrideService = new AdminOverrideService(adminFieldOverrideRepository);
        ApiFootballFixturePlayerStatSyncService service = new ApiFootballFixturePlayerStatSyncService(
                apiFootballClient,
                fixtureRepository,
                playerFixtureStatRepository,
                teamRepository,
                playerSyncService,
                overrideService
        );
        Team team = Team.builder().teamId(42L).name("Arsenal").build();
        Player player = Player.builder().playerId(10L).name("Player").build();
        Fixture fixture = Fixture.builder().fixtureId(1000L).build();
        PlayerFixtureStat entity = PlayerFixtureStat.builder()
                .id(201L)
                .fixture(fixture)
                .team(team)
                .player(player)
                .minutesPlayed(90)
                .rating(7.0)
                .goals(2)
                .build();
        ApiFootballLiveDto.TeamInfo teamInfo = new ApiFootballLiveDto.TeamInfo();
        ReflectionTestUtils.setField(teamInfo, "id", 42L);
        ApiFootballLiveDto.PlayerInfo playerInfo = new ApiFootballLiveDto.PlayerInfo();
        ReflectionTestUtils.setField(playerInfo, "id", 10L);
        ApiFootballLiveDto.Games games = new ApiFootballLiveDto.Games();
        ReflectionTestUtils.setField(games, "minutes", 0);
        ReflectionTestUtils.setField(games, "rating", "8.0");
        ApiFootballLiveDto.GoalsStat goals = new ApiFootballLiveDto.GoalsStat();
        ReflectionTestUtils.setField(goals, "total", 9);
        ApiFootballLiveDto.PlayerStatistics statistics = new ApiFootballLiveDto.PlayerStatistics();
        ReflectionTestUtils.setField(statistics, "games", games);
        ReflectionTestUtils.setField(statistics, "goals", goals);
        ApiFootballLiveDto.PlayerStatResponse playerResponse = new ApiFootballLiveDto.PlayerStatResponse();
        ReflectionTestUtils.setField(playerResponse, "player", playerInfo);
        ReflectionTestUtils.setField(playerResponse, "statistics", List.of(statistics));
        ApiFootballLiveDto.FixturePlayersResponse response = new ApiFootballLiveDto.FixturePlayersResponse();
        ReflectionTestUtils.setField(response, "team", teamInfo);
        ReflectionTestUtils.setField(response, "players", List.of(playerResponse));

        when(teamRepository.findByTeamId(42L)).thenReturn(Optional.of(team));
        when(playerSyncService.findOrFetchPlayer(10L, null, team, null, null, null))
                .thenReturn(Optional.of(player));
        when(playerFixtureStatRepository.findByFixtureFixtureIdAndPlayerPlayerId(1000L, 10L))
                .thenReturn(Optional.of(entity));
        when(adminFieldOverrideRepository.findAllByTargetTypeAndTargetIdAndFieldNameIn(
                eq(AdminOverrideTargetType.FIXTURE_PLAYER_STAT), eq(201L), anyCollection()))
                .thenReturn(List.of(AdminFieldOverride.of(
                        AdminOverrideTargetType.FIXTURE_PLAYER_STAT, 201L, "goals")));

        int synced = service.syncPlayerStats(fixture, List.of(response));

        assertThat(synced).isEqualTo(1);
        assertThat(entity.getGoals()).isEqualTo(2);
        assertThat(entity.getMinutesPlayed()).isNull();
        assertThat(entity.getRating()).isEqualTo(8.0);
    }
}

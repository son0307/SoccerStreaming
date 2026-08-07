package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLineupDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureLineup;
import com.son.soccerStreaming.fixture.repository.FixtureLineupRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiFootballFixtureLineupSyncServiceTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private FixtureRepository fixtureRepository;
    @Mock private FixtureLineupRepository fixtureLineupRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    @Mock private AdminOverrideService adminOverrideService;
    @Mock private OptimisticLockRetryExecutor optimisticLockRetryExecutor;

    @InjectMocks
    private ApiFootballFixtureLineupSyncService service;

    @Test
    void lineupSyncKeepsAdminOverriddenFixtureAndPlayerFields() {
        Team home = Team.builder().teamId(42L).name("Arsenal").build();
        Team away = Team.builder().teamId(49L).name("Chelsea").build();
        Player player = Player.builder().playerId(10L).name("Player").build();
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureDate(java.time.LocalDateTime.of(2026, 8, 7, 10, 0))
                .homeFormation("4-4-2")
                .homeCoachName("Old coach")
                .build();
        FixtureLineup entity = FixtureLineup.builder()
                .fixture(fixture)
                .team(home)
                .player(player)
                .jerseyNumber(7)
                .position("M")
                .grid("2:1")
                .isStarter(false)
                .build();
        ReflectionTestUtils.setField(entity, "id", 301L);

        ApiFootballLineupDto.PlayerInfo playerInfo = new ApiFootballLineupDto.PlayerInfo();
        ReflectionTestUtils.setField(playerInfo, "id", 10L);
        ReflectionTestUtils.setField(playerInfo, "number", 8);
        ReflectionTestUtils.setField(playerInfo, "pos", "D");
        ReflectionTestUtils.setField(playerInfo, "grid", "1:2");
        ApiFootballLineupDto.PlayerEntry entry = new ApiFootballLineupDto.PlayerEntry();
        ReflectionTestUtils.setField(entry, "player", playerInfo);
        ApiFootballLineupDto.TeamInfo teamInfo = new ApiFootballLineupDto.TeamInfo();
        ReflectionTestUtils.setField(teamInfo, "id", 42L);
        ApiFootballLineupDto.CoachInfo coach = new ApiFootballLineupDto.CoachInfo();
        ReflectionTestUtils.setField(coach, "name", "API coach");
        ApiFootballLineupDto.LineupResponse response = new ApiFootballLineupDto.LineupResponse();
        ReflectionTestUtils.setField(response, "team", teamInfo);
        ReflectionTestUtils.setField(response, "coach", coach);
        ReflectionTestUtils.setField(response, "formation", "3-4-3");
        ReflectionTestUtils.setField(response, "startXI", List.of(entry));

        when(teamRepository.findByTeamId(42L)).thenReturn(Optional.of(home));
        when(apiFootballPlayerSyncService.findOrFetchPlayer(10L, null, home, 8, "D", null))
                .thenReturn(Optional.of(player));
        when(fixtureLineupRepository.findByFixtureFixtureIdAndTeamTeamIdAndPlayerPlayerId(1000L, 42L, 10L))
                .thenReturn(Optional.of(entity));
        when(adminOverrideService.overriddenFields(any(), anyLong(), anyCollection()))
                .thenAnswer(invocation -> invocation.getArgument(0) == AdminOverrideTargetType.FIXTURE
                        ? Set.of("homeFormation")
                        : Set.of("position"));
        keepCurrentValueWhenOverridden();

        int count = service.syncLineups(fixture, List.of(response));

        assertThat(count).isEqualTo(1);
        assertThat(fixture.getHomeFormation()).isEqualTo("4-4-2");
        assertThat(fixture.getHomeCoachName()).isEqualTo("API coach");
        assertThat(entity.getJerseyNumber()).isEqualTo(8);
        assertThat(entity.getPosition()).isEqualTo("M");
        assertThat(entity.getGrid()).isEqualTo("1:2");
        assertThat(entity.isStarter()).isTrue();
    }

    private void keepCurrentValueWhenOverridden() {
        doAnswer(invocation -> {
            Set<?> overrides = invocation.getArgument(0);
            String fieldName = invocation.getArgument(1);
            return overrides.contains(fieldName) ? invocation.getArgument(2) : invocation.getArgument(3);
        }).when(adminOverrideService).apiValueUnlessOverridden(any(), anyString(), any(), any());
    }
}

package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.admin.entity.AdminFieldOverride;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.repository.AdminFieldOverrideRepository;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballFixtureStatisticsDto;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.entity.FixtureStat;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.repository.FixtureStatRepository;
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
class ApiFootballFixtureStatSyncServiceTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private FixtureRepository fixtureRepository;
    @Mock private FixtureStatRepository fixtureStatRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private AdminFieldOverrideRepository adminFieldOverrideRepository;

    @Test
    void preservesOverriddenTeamStatFieldsAndUpdatesTheRest() {
        AdminOverrideService overrideService = new AdminOverrideService(adminFieldOverrideRepository);
        ApiFootballFixtureStatSyncService service = new ApiFootballFixtureStatSyncService(
                apiFootballClient, fixtureRepository, fixtureStatRepository, teamRepository, overrideService);
        Team team = Team.builder().teamId(42L).name("Arsenal").build();
        Fixture fixture = Fixture.builder().fixtureId(1000L).build();
        FixtureStat entity = FixtureStat.builder()
                .id(101L)
                .fixture(fixture)
                .team(team)
                .totalShots(12)
                .fouls(3)
                .build();
        ApiFootballLiveDto.TeamInfo teamInfo = new ApiFootballLiveDto.TeamInfo();
        ReflectionTestUtils.setField(teamInfo, "id", 42L);
        ApiFootballFixtureStatisticsDto.Statistic totalShots = statistic("Total Shots", 99);
        ApiFootballFixtureStatisticsDto.Statistic fouls = statistic("Fouls", 8);
        ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse response =
                new ApiFootballFixtureStatisticsDto.FixtureStatisticsResponse();
        ReflectionTestUtils.setField(response, "team", teamInfo);
        ReflectionTestUtils.setField(response, "statistics", List.of(totalShots, fouls));

        when(teamRepository.findByTeamId(42L)).thenReturn(Optional.of(team));
        when(fixtureStatRepository.findByFixtureFixtureIdAndTeamTeamId(1000L, 42L))
                .thenReturn(Optional.of(entity));
        when(adminFieldOverrideRepository.findAllByTargetTypeAndTargetIdAndFieldNameIn(
                eq(AdminOverrideTargetType.FIXTURE_TEAM_STAT), eq(101L), anyCollection()))
                .thenReturn(List.of(AdminFieldOverride.of(
                        AdminOverrideTargetType.FIXTURE_TEAM_STAT, 101L, "totalShots")));

        int synced = service.syncFixtureStats(fixture, List.of(response));

        assertThat(synced).isEqualTo(1);
        assertThat(entity.getTotalShots()).isEqualTo(12);
        assertThat(entity.getFouls()).isEqualTo(8);
    }

    private ApiFootballFixtureStatisticsDto.Statistic statistic(String type, Object value) {
        ApiFootballFixtureStatisticsDto.Statistic statistic = new ApiFootballFixtureStatisticsDto.Statistic();
        ReflectionTestUtils.setField(statistic, "type", type);
        ReflectionTestUtils.setField(statistic, "value", value);
        return statistic;
    }
}

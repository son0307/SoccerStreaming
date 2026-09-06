package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService.LiveStandingImpact;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiFootballStandingLocalUpdateServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ObjectMapper objectMapper;
    @Mock private TeamStandingRepository teamStandingRepository;

    private ApiFootballStandingLocalUpdateService service;

    @BeforeEach
    void setUp() {
        service = new ApiFootballStandingLocalUpdateService(redisTemplate, objectMapper, teamStandingRepository);
        ReflectionTestUtils.setField(service, "localLiveUpdateEnabled", true);
        ReflectionTestUtils.setField(service, "localFinishedUpdateEnabled", true);
        ReflectionTestUtils.setField(service, "season", 2025);
        ReflectionTestUtils.setField(service, "league", 39);
        ReflectionTestUtils.setField(service, "liveImpactTtlHours", 6L);
        ReflectionTestUtils.setField(service, "finishedImpactTtlHours", 48L);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void storesAuthoritativePlayedCountsWhenImpactIsCreated() throws Exception {
        Team home = team(42L);
        Team away = team(50L);
        Fixture fixture = fixture(home, away, "1H", 1, 0);
        String key = "standing:live-impact:2025:100";

        when(valueOperations.get(key)).thenReturn(null);
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(42L, 39, 2025))
                .thenReturn(Optional.of(standing(home, 10)));
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(50L, 39, 2025))
                .thenReturn(Optional.of(standing(away, 12)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.applyFixtureState(fixture);

        ArgumentCaptor<Object> impactCaptor = ArgumentCaptor.forClass(Object.class);
        verify(objectMapper).writeValueAsString(impactCaptor.capture());
        LiveStandingImpact impact = (LiveStandingImpact) impactCaptor.getValue();
        assertThat(impact.getHomePlayedBefore()).isEqualTo(10);
        assertThat(impact.getAwayPlayedBefore()).isEqualTo(12);
        assertThat(impact.getHomeBaseline().getPoints()).isEqualTo(20);
        assertThat(impact.getHomeBaseline().getVenuePlayed()).isEqualTo(5);
        assertThat(impact.getAwayBaseline().getPoints()).isEqualTo(20);
        assertThat(impact.getAwayBaseline().getVenuePlayed()).isEqualTo(5);
        assertThat(impact.getElapsed()).isEqualTo(23);
        verify(valueOperations).set(eq(key), eq("{}"), eq(Duration.ofHours(6)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FT", "AET", "PEN"})
    void storesFinishedImpactForFortyEightHours(String statusShort) throws Exception {
        Team home = team(42L);
        Team away = team(50L);
        Fixture fixture = fixture(home, away, statusShort, 2, 1);
        String key = "standing:live-impact:2025:100";

        when(valueOperations.get(key)).thenReturn(null);
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(42L, 39, 2025))
                .thenReturn(Optional.of(standing(home, 10)));
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(50L, 39, 2025))
                .thenReturn(Optional.of(standing(away, 12)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.applyFixtureState(fixture);

        verify(valueOperations).set(eq(key), eq("{}"), eq(Duration.ofHours(48)));
    }

    @Test
    void removesFinishedImpactOnlyAfterBothTeamsPlayedCountsIncrease() throws Exception {
        LiveStandingImpact impact = finishedImpact();
        String key = "standing:live-impact:2025:100";
        when(redisTemplate.keys("standing:live-impact:2025:*")).thenReturn(Set.of(key));
        when(valueOperations.get(key)).thenReturn("{}");
        when(objectMapper.readValue("{}", LiveStandingImpact.class)).thenReturn(impact);
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(42L, 39, 2025))
                .thenReturn(Optional.of(standingAfterHomeWin(team(42L), 11)));
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(50L, 39, 2025))
                .thenReturn(Optional.of(standing(team(50L), 12)));

        service.reconcileFinishedImpacts(39, 2025);

        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void removesFinishedImpactWhenBothTeamsPlayedCountsIncrease() throws Exception {
        LiveStandingImpact impact = finishedImpact();
        String key = "standing:live-impact:2025:100";
        when(redisTemplate.keys("standing:live-impact:2025:*")).thenReturn(Set.of(key));
        when(valueOperations.get(key)).thenReturn("{}");
        when(objectMapper.readValue("{}", LiveStandingImpact.class)).thenReturn(impact);
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(42L, 39, 2025))
                .thenReturn(Optional.of(standingAfterHomeWin(team(42L), 11)));
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(50L, 39, 2025))
                .thenReturn(Optional.of(standingAfterAwayLoss(team(50L), 13)));

        service.reconcileFinishedImpacts(39, 2025);

        verify(redisTemplate).delete(key);
    }

    @Test
    void keepsFinishedImpactWhenPlayedIncreasesButFixtureResultMetricsDoNotMatch() throws Exception {
        LiveStandingImpact impact = finishedImpact();
        String key = "standing:live-impact:2025:100";
        when(redisTemplate.keys("standing:live-impact:2025:*")).thenReturn(Set.of(key));
        when(valueOperations.get(key)).thenReturn("{}");
        when(objectMapper.readValue("{}", LiveStandingImpact.class)).thenReturn(impact);
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(42L, 39, 2025))
                .thenReturn(Optional.of(standingWithOnlyPlayedAdvanced(team(42L), 11)));
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(50L, 39, 2025))
                .thenReturn(Optional.of(standingWithOnlyPlayedAdvanced(team(50L), 13)));

        service.reconcileFinishedImpacts(39, 2025);

        verify(redisTemplate, never()).delete(key);
    }

    @Test
    void returnsNoImpactsWhenRedisIsUnavailable() {
        when(redisTemplate.keys("standing:live-impact:2025:*"))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertThat(service.findImpacts(2025)).isEmpty();
    }

    @Test
    void continuesFixtureSyncWhenRedisImpactReadAndWriteFail() throws Exception {
        Team home = team(42L);
        Team away = team(50L);
        Fixture fixture = fixture(home, away, "1H", 1, 0);
        String key = "standing:live-impact:2025:100";

        when(valueOperations.get(key))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(42L, 39, 2025))
                .thenReturn(Optional.of(standing(home, 10)));
        when(teamStandingRepository.findByTeamTeamIdAndLeagueIdAndSeason(50L, 39, 2025))
                .thenReturn(Optional.of(standing(away, 12)));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(valueOperations).set(eq(key), eq("{}"), eq(Duration.ofHours(6)));

        assertThatCode(() -> service.applyFixtureState(fixture)).doesNotThrowAnyException();
    }

    private Fixture fixture(Team home, Team away, String statusShort, int homeScore, int awayScore) {
        return Fixture.builder()
                .fixtureId(100L)
                .homeTeam(home)
                .awayTeam(away)
                .leagueId(39)
                .season(2025)
                .statusShort(statusShort)
                .fixtureStatus("LIVE")
                .elapsed(23)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .build();
    }

    private Team team(Long teamId) {
        return Team.builder().teamId(teamId).name("Team " + teamId).build();
    }

    private TeamStanding standing(Team team, int played) {
        return TeamStanding.builder()
                .team(team)
                .leagueId(39)
                .season(2025)
                .played(played)
                .points(20)
                .win(6)
                .draw(2)
                .lose(2)
                .goalsFor(20)
                .goalsAgainst(10)
                .homePlayed(5)
                .homeWin(3)
                .homeDraw(1)
                .homeLose(1)
                .homeGoalsFor(10)
                .homeGoalsAgainst(5)
                .awayPlayed(5)
                .awayWin(3)
                .awayDraw(1)
                .awayLose(1)
                .awayGoalsFor(10)
                .awayGoalsAgainst(5)
                .apiUpdatedAt(LocalDateTime.of(2026, 8, 28, 0, 0))
                .build();
    }

    private TeamStanding standingAfterHomeWin(Team team, int played) {
        return TeamStanding.builder()
                .team(team)
                .leagueId(39)
                .season(2025)
                .played(played)
                .points(23)
                .win(7)
                .draw(2)
                .lose(2)
                .goalsFor(22)
                .goalsAgainst(11)
                .homePlayed(6)
                .homeWin(4)
                .homeDraw(1)
                .homeLose(1)
                .homeGoalsFor(12)
                .homeGoalsAgainst(6)
                .apiUpdatedAt(LocalDateTime.of(2026, 8, 29, 0, 0))
                .build();
    }

    private TeamStanding standingAfterAwayLoss(Team team, int played) {
        return TeamStanding.builder()
                .team(team)
                .leagueId(39)
                .season(2025)
                .played(played)
                .points(20)
                .win(6)
                .draw(2)
                .lose(3)
                .goalsFor(21)
                .goalsAgainst(12)
                .awayPlayed(6)
                .awayWin(3)
                .awayDraw(1)
                .awayLose(2)
                .awayGoalsFor(11)
                .awayGoalsAgainst(7)
                .apiUpdatedAt(LocalDateTime.of(2026, 8, 29, 0, 0))
                .build();
    }

    private TeamStanding standingWithOnlyPlayedAdvanced(Team team, int played) {
        return TeamStanding.builder()
                .team(team)
                .leagueId(39)
                .season(2025)
                .played(played)
                .points(20)
                .win(6)
                .draw(2)
                .lose(2)
                .goalsFor(20)
                .goalsAgainst(10)
                .homePlayed(6)
                .homeWin(3)
                .homeDraw(1)
                .homeLose(1)
                .homeGoalsFor(10)
                .homeGoalsAgainst(5)
                .awayPlayed(6)
                .awayWin(3)
                .awayDraw(1)
                .awayLose(1)
                .awayGoalsFor(10)
                .awayGoalsAgainst(5)
                .apiUpdatedAt(LocalDateTime.of(2026, 8, 29, 0, 0))
                .build();
    }

    private LiveStandingImpact finishedImpact() {
        return new LiveStandingImpact(
                100L, 2025, 42L, 50L, 2, 1, "FT", 90, null, 10, 12,
                LocalDateTime.of(2026, 8, 28, 22, 0),
                baseline(10), baseline(12)
        );
    }

    private ApiFootballStandingLocalUpdateService.StandingBaseline baseline(int played) {
        return ApiFootballStandingLocalUpdateService.StandingBaseline.builder()
                .played(played)
                .points(20)
                .win(6)
                .draw(2)
                .lose(2)
                .goalsFor(20)
                .goalsAgainst(10)
                .venuePlayed(5)
                .venueWin(3)
                .venueDraw(1)
                .venueLose(1)
                .venueGoalsFor(10)
                .venueGoalsAgainst(5)
                .apiUpdatedAt(LocalDateTime.of(2026, 8, 28, 0, 0))
                .build();
    }
}

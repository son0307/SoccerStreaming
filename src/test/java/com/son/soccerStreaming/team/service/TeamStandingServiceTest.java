package com.son.soccerStreaming.team.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService.LiveStandingImpact;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamStandingServiceTest {

    @Mock
    private TeamStandingRepository teamStandingRepository;
    @Mock
    private ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;
    @Mock
    private FixtureRepository fixtureRepository;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private TeamStandingService teamStandingService;

    @Test
    void getStandingsIncludesRecentFiveFormFromFinishedFixtures() {
        Team arsenal = team(42L, "Arsenal");
        Team city = team(50L, "Manchester City");
        Team chelsea = team(49L, "Chelsea");
        Team spurs = team(47L, "Tottenham");

        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(
                standing(arsenal, 1, 80),
                standing(city, 2, 78)
        ));
        when(apiFootballStandingLocalUpdateService.findImpacts(2025)).thenReturn(List.of());
        when(fixtureRepository.findFinishedWithScoresBySeasonOrderByFixtureDateDesc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.anyCollection()
        ))
                .thenReturn(List.of(
                        fixture(1L, arsenal, city, 2, 1, 10),
                        fixture(2L, chelsea, arsenal, 0, 0, 9),
                        fixture(3L, arsenal, spurs, 1, 3, 8),
                        fixture(4L, city, arsenal, 1, 2, 7),
                        fixture(5L, arsenal, chelsea, 4, 0, 6),
                        fixture(6L, spurs, arsenal, 5, 0, 5),
                        fixture(7L, city, spurs, 3, 0, 4)
                ));

        var response = teamStandingService.getStandings(2025);

        var arsenalForm = response.get(0).getRecentForm();
        assertThat(arsenalForm.getPlayed()).isEqualTo(5);
        assertThat(arsenalForm.getWin()).isEqualTo(3);
        assertThat(arsenalForm.getDraw()).isEqualTo(1);
        assertThat(arsenalForm.getLose()).isEqualTo(1);
        assertThat(arsenalForm.getGoals().getGoalsFor()).isEqualTo(9);
        assertThat(arsenalForm.getGoals().getGoalsAgainst()).isEqualTo(5);
        assertThat(arsenalForm.getGoalsDiff()).isEqualTo(4);
        assertThat(arsenalForm.getPoints()).isEqualTo(10);
        assertThat(arsenalForm.getResults()).containsExactly("W", "D", "L", "W", "W");
        assertThat(arsenalForm.getMatches()).hasSize(5);
        assertThat(arsenalForm.getMatches().get(0).getFixtureId()).isEqualTo(1L);
        assertThat(arsenalForm.getMatches().get(0).getFixtureDate())
                .isEqualTo(LocalDateTime.of(2026, 5, 18, 21, 0));
        assertThat(arsenalForm.getMatches().get(0).getOpponent().getId()).isEqualTo(50L);
        assertThat(arsenalForm.getMatches().get(0).getVenue()).isEqualTo("HOME");
        assertThat(arsenalForm.getMatches().get(0).getScoreFor()).isEqualTo(2);
        assertThat(arsenalForm.getMatches().get(0).getScoreAgainst()).isEqualTo(1);
        assertThat(arsenalForm.getMatches().get(0).getResult()).isEqualTo("W");
        assertThat(arsenalForm.getMatches())
                .extracting(com.son.soccerStreaming.team.dto.TeamStandingResponseDto.RecentMatch::getFixtureId)
                .doesNotContain(6L);

        var cityForm = response.get(1).getRecentForm();
        assertThat(cityForm.getPlayed()).isEqualTo(3);
        assertThat(cityForm.getWin()).isEqualTo(1);
        assertThat(cityForm.getDraw()).isZero();
        assertThat(cityForm.getLose()).isEqualTo(2);
        assertThat(cityForm.getResults()).containsExactly("L", "L", "W");
    }

    @Test
    void getStandingsAssignsEachTeamsFirstUpcomingFixtureFromOneBulkQuery() {
        Team arsenal = team(42L, "Arsenal");
        Team city = team(50L, "Manchester City");
        Team chelsea = team(49L, "Chelsea");
        Team spurs = team(47L, "Tottenham");

        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(
                standing(arsenal, 1, 80),
                standing(city, 2, 78),
                standing(chelsea, 3, 76)
        ));
        when(apiFootballStandingLocalUpdateService.findImpacts(2025)).thenReturn(List.of());
        when(fixtureRepository.findFinishedWithScoresBySeasonOrderByFixtureDateDesc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(List.of());
        when(fixtureRepository.findUpcomingScheduledByLeagueAndSeason(
                org.mockito.ArgumentMatchers.eq(39),
                org.mockito.ArgumentMatchers.eq(2025),
                any(LocalDateTime.class)
        )).thenReturn(List.of(
                scheduledFixture(101L, city, chelsea, LocalDateTime.of(2026, 9, 1, 12, 0)),
                scheduledFixture(102L, arsenal, spurs, LocalDateTime.of(2026, 9, 2, 12, 0)),
                scheduledFixture(103L, arsenal, city, LocalDateTime.of(2026, 9, 10, 12, 0))
        ));

        var response = teamStandingService.getStandings(2025);

        var arsenalNextMatch = response.stream()
                .filter(row -> row.getTeam().getId().equals(42L))
                .findFirst()
                .orElseThrow()
                .getNextMatch();
        assertThat(arsenalNextMatch.getFixtureId()).isEqualTo(102L);
        assertThat(arsenalNextMatch.getFixtureDate()).isEqualTo(LocalDateTime.of(2026, 9, 2, 21, 0));
        assertThat(arsenalNextMatch.getOpponent().getId()).isEqualTo(47L);
        assertThat(arsenalNextMatch.getVenue()).isEqualTo("HOME");

        var cityNextMatch = response.stream()
                .filter(row -> row.getTeam().getId().equals(50L))
                .findFirst()
                .orElseThrow()
                .getNextMatch();
        assertThat(cityNextMatch.getFixtureId()).isEqualTo(101L);
        assertThat(cityNextMatch.getOpponent().getId()).isEqualTo(49L);
        assertThat(cityNextMatch.getVenue()).isEqualTo("HOME");

        var chelseaNextMatch = response.stream()
                .filter(row -> row.getTeam().getId().equals(49L))
                .findFirst()
                .orElseThrow()
                .getNextMatch();
        assertThat(chelseaNextMatch.getFixtureId()).isEqualTo(101L);
        assertThat(chelseaNextMatch.getOpponent().getId()).isEqualTo(50L);
        assertThat(chelseaNextMatch.getVenue()).isEqualTo("AWAY");

        verify(fixtureRepository).findUpcomingScheduledByLeagueAndSeason(
                org.mockito.ArgumentMatchers.eq(39),
                org.mockito.ArgumentMatchers.eq(2025),
                any(LocalDateTime.class)
        );
        verify(fixtureRepository, never()).findNextByTeam(any(), any(), any(), any());
    }

    @Test
    void getStandingsIncludesLiveScoreAndAppliesUnreflectedImpact() {
        Team arsenal = team(42L, "Arsenal");
        Team city = team(50L, "Manchester City");
        LiveStandingImpact impact = new LiveStandingImpact(
                100L, 2025, 42L, 50L, 2, 1, "2H", 67, 2, 38, 38, LocalDateTime.now()
        );

        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(
                standing(arsenal, 1, 80),
                standing(city, 2, 78)
        ));
        when(apiFootballStandingLocalUpdateService.findImpacts(2025)).thenReturn(List.of(impact));
        when(apiFootballStandingLocalUpdateService.isLiveImpact(impact)).thenReturn(true);
        when(apiFootballStandingLocalUpdateService.isReflected(
                org.mockito.ArgumentMatchers.eq(impact), any(), any()
        )).thenReturn(false);
        when(fixtureRepository.findFinishedWithScoresBySeasonOrderByFixtureDateDesc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(List.of());

        var response = teamStandingService.getStandings(2025);
        var arsenalResponse = response.stream()
                .filter(row -> row.getTeam().getId().equals(42L))
                .findFirst()
                .orElseThrow();

        assertThat(arsenalResponse.getAll().getPlayed()).isEqualTo(39);
        assertThat(arsenalResponse.getPoints()).isEqualTo(83);
        assertThat(arsenalResponse.getLiveMatch().getScoreFor()).isEqualTo(2);
        assertThat(arsenalResponse.getLiveMatch().getScoreAgainst()).isEqualTo(1);
        assertThat(arsenalResponse.getLiveMatch().getResult()).isEqualTo("WINNING");
        assertThat(arsenalResponse.getLiveMatch().getElapsed()).isEqualTo(67);
        assertThat(arsenalResponse.getLiveMatch().getExtra()).isEqualTo(2);
    }

    @Test
    void getStandingsAppliesUnreflectedFinishedImpactWithoutLiveScore() {
        Team arsenal = team(42L, "Arsenal");
        Team city = team(50L, "Manchester City");
        LiveStandingImpact impact = new LiveStandingImpact(
                100L, 2025, 42L, 50L, 2, 1, "FT", 90, null, 38, 38, LocalDateTime.now()
        );

        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(
                standing(arsenal, 1, 80),
                standing(city, 2, 78)
        ));
        when(apiFootballStandingLocalUpdateService.findImpacts(2025)).thenReturn(List.of(impact));
        when(apiFootballStandingLocalUpdateService.isLiveImpact(impact)).thenReturn(false);
        when(apiFootballStandingLocalUpdateService.isReflected(
                org.mockito.ArgumentMatchers.eq(impact), any(), any()
        )).thenReturn(false);
        when(fixtureRepository.findFinishedWithScoresBySeasonOrderByFixtureDateDesc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(List.of());

        var response = teamStandingService.getStandings(2025);
        var arsenalResponse = response.stream()
                .filter(row -> row.getTeam().getId().equals(42L))
                .findFirst()
                .orElseThrow();

        assertThat(arsenalResponse.getAll().getPlayed()).isEqualTo(39);
        assertThat(arsenalResponse.getPoints()).isEqualTo(83);
        assertThat(arsenalResponse.getLiveMatch()).isNull();
    }

    @Test
    void getStandingsDoesNotApplyImpactWhenAuthoritativePlayedCountsAlreadyIncreased() {
        Team arsenal = team(42L, "Arsenal");
        Team city = team(50L, "Manchester City");
        LiveStandingImpact impact = new LiveStandingImpact(
                100L, 2025, 42L, 50L, 2, 1, "FT", 90, null, 37, 37, LocalDateTime.now()
        );

        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(
                standing(arsenal, 1, 80),
                standing(city, 2, 78)
        ));
        when(apiFootballStandingLocalUpdateService.findImpacts(2025)).thenReturn(List.of(impact));
        when(apiFootballStandingLocalUpdateService.isLiveImpact(impact)).thenReturn(false);
        when(apiFootballStandingLocalUpdateService.isReflected(
                org.mockito.ArgumentMatchers.eq(impact), any(), any()
        )).thenReturn(true);
        when(fixtureRepository.findFinishedWithScoresBySeasonOrderByFixtureDateDesc(
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.anyCollection()
        )).thenReturn(List.of());

        var response = teamStandingService.getStandings(2025);
        var arsenalResponse = response.stream()
                .filter(row -> row.getTeam().getId().equals(42L))
                .findFirst()
                .orElseThrow();

        assertThat(arsenalResponse.getAll().getPlayed()).isEqualTo(38);
        assertThat(arsenalResponse.getPoints()).isEqualTo(80);
        assertThat(arsenalResponse.getLiveMatch()).isNull();
    }

    private Team team(Long teamId, String name) {
        return Team.builder()
                .teamId(teamId)
                .name(name)
                .logoUrl(name + ".png")
                .build();
    }

    private TeamStanding standing(Team team, Integer rank, Integer points) {
        return TeamStanding.builder()
                .team(team)
                .season(2025)
                .rank(rank)
                .points(points)
                .goalsDiff(points - 60)
                .played(38)
                .win(20)
                .draw(8)
                .lose(10)
                .goalsFor(70)
                .goalsAgainst(40)
                .homePlayed(19)
                .homeWin(12)
                .homeDraw(4)
                .homeLose(3)
                .homeGoalsFor(38)
                .homeGoalsAgainst(17)
                .awayPlayed(19)
                .awayWin(8)
                .awayDraw(4)
                .awayLose(7)
                .awayGoalsFor(32)
                .awayGoalsAgainst(23)
                .form("WWDLW")
                .build();
    }

    private Fixture fixture(Long fixtureId, Team home, Team away, Integer homeScore, Integer awayScore, int daysAgo) {
        return Fixture.builder()
                .fixtureId(fixtureId)
                .homeTeam(home)
                .awayTeam(away)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .fixtureStatus("FINISHED")
                .fixtureDate(LocalDateTime.of(2026, 5, 28, 12, 0).minusDays(daysAgo))
                .season(2025)
                .build();
    }

    private Fixture scheduledFixture(Long fixtureId, Team home, Team away, LocalDateTime fixtureDate) {
        return Fixture.builder()
                .fixtureId(fixtureId)
                .homeTeam(home)
                .awayTeam(away)
                .fixtureStatus("SCHEDULED")
                .fixtureDate(fixtureDate)
                .leagueId(39)
                .season(2025)
                .build();
    }
}

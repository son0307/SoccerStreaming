package com.son.soccerStreaming.league.service;

import com.son.soccerStreaming.fixture.repository.PlayerFixtureStatRepository;
import com.son.soccerStreaming.league.dto.LeaguePlayerRankingResponseDto;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerTeamSeasonStat;
import com.son.soccerStreaming.player.repository.PlayerTeamSeasonStatRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaguePlayerRankingServiceTest {

    @Mock
    private PlayerTeamSeasonStatRepository playerTeamSeasonStatRepository;
    @Mock
    private PlayerFixtureStatRepository playerFixtureStatRepository;
    @Mock
    private TeamStandingRepository teamStandingRepository;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private LeaguePlayerRankingService service;

    @Test
    void aggregatesTransferredPlayerAndUsesPenaltyGoalsAsScoringTieBreaker() {
        Team arsenal = team(42L, "Arsenal");
        Team chelsea = team(49L, "Chelsea");
        Team liverpool = team(40L, "Liverpool");
        Player transferred = player(1L, "Transferred");
        Player fewerPenalties = player(2L, "Fewer Penalties");

        PlayerTeamSeasonStat oldTeamStat = stat(transferred, arsenal, 8, 2, 900, 7.0, 2, 1, 1, 0, 0);
        PlayerTeamSeasonStat newTeamStat = stat(transferred, chelsea, 2, 1, 300, 8.0, 1, 1, 0, 0, 0);
        PlayerTeamSeasonStat rivalStat = stat(fewerPenalties, liverpool, 10, 1, 1000, 7.5, 3, 1, 0, 0, 0);

        when(playerTeamSeasonStatRepository.findAllForLeagueRankings(39L, 2025))
                .thenReturn(List.of(oldTeamStat, newTeamStat, rivalStat));
        when(playerFixtureStatRepository.findRankingMatchAggregates(eq(List.of(1L, 2L)), eq(2025)))
                .thenReturn(List.of(aggregate(1L, 7, 3, 8, 0), aggregate(2L, 7, 3, 8, 0)));
        when(playerFixtureStatRepository.findLatestTeamByPlayerIdsAndSeason(
                eq(List.of(1L, 2L)),
                eq(2025)
        )).thenReturn(List.of(
                latestTeam(1L, 49L, LocalDateTime.of(2025, 2, 1, 12, 0), 101L),
                latestTeam(2L, 40L, LocalDateTime.of(2025, 2, 1, 12, 0), 102L)
        ));
        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025))
                .thenReturn(List.of(standing(arsenal, 2), standing(chelsea, 4), standing(liverpool, 1)));

        LeaguePlayerRankingResponseDto response = service.getRankings(39, 2025);

        verify(playerFixtureStatRepository, never())
                .findTeamHistoryByPlayerIdsAndSeasonOrderByLatest(eq(List.of(1L, 2L)), eq(2025));
        assertThat(response.getGoals())
                .extracting(LeaguePlayerRankingResponseDto.Row::getPlayerId)
                .containsExactly(2L, 1L);
        LeaguePlayerRankingResponseDto.Row transferredRow = response.getGoals().get(1);
        assertThat(transferredRow.getGoals()).isEqualTo(10);
        assertThat(transferredRow.getAssists()).isEqualTo(3);
        assertThat(transferredRow.getAppearances()).isEqualTo(20);
        assertThat(transferredRow.getMinutes()).isEqualTo(1200);
        assertThat(transferredRow.getRating()).isEqualTo(7.5);
        assertThat(transferredRow.getTeamId()).isEqualTo(49L);
        assertThat(transferredRow.getTeamName()).isEqualTo("Chelsea");
    }

    @Test
    void appliesMatchCountersCleanSheetsAndRegularMinimumSaveAttempts() {
        Team everton = team(45L, "Everton");
        Player eligibleKeeper = player(10L, "Eligible Keeper");
        Player fewAppearancesKeeper = player(20L, "Few Appearances");
        Player fewAttemptsKeeper = player(30L, "Few Attempts");
        Player fieldPlayer = player(40L, "Field Player");

        PlayerTeamSeasonStat eligible = stat(eligibleKeeper, everton, 0, 0, 900, 7.1, 0, 0, 0, 8, 2);
        PlayerTeamSeasonStat fewAppearances =
                stat(fewAppearancesKeeper, everton, 0, 0, 810, 7.4, 0, 0, 0, 9, 1, 9);
        PlayerTeamSeasonStat fewAttempts =
                stat(fewAttemptsKeeper, everton, 0, 0, 900, 7.2, 0, 0, 0, 9, 0);
        PlayerTeamSeasonStat field =
                stat(fieldPlayer, everton, 0, 0, 900, 7.0, 0, 0, 0, 0, 0);

        when(playerTeamSeasonStatRepository.findAllForLeagueRankings(39L, 2025))
                .thenReturn(List.of(eligible, fewAppearances, fewAttempts, field));
        when(playerFixtureStatRepository.findRankingMatchAggregates(eq(List.of(10L, 20L, 30L, 40L)), eq(2025)))
                .thenReturn(List.of(
                        aggregate(10L, 0, 0, 0, 4),
                        aggregate(20L, 0, 0, 0, 3),
                        aggregate(30L, 0, 0, 0, 2),
                        aggregate(40L, 0, 0, 0, 8)
                ));
        when(playerFixtureStatRepository.findLatestTeamByPlayerIdsAndSeason(
                eq(List.of(10L, 20L, 30L, 40L)),
                eq(2025)
        )).thenReturn(List.of(
                latestTeam(10L, 45L, LocalDateTime.of(2025, 2, 1, 12, 0), 201L),
                latestTeam(20L, 45L, LocalDateTime.of(2025, 2, 1, 12, 0), 202L),
                latestTeam(30L, 45L, LocalDateTime.of(2025, 2, 1, 12, 0), 203L),
                latestTeam(40L, 45L, LocalDateTime.of(2025, 2, 1, 12, 0), 204L)
        ));
        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(standing(everton, 8)));

        LeaguePlayerRankingResponseDto response = service.getRankings(39, 2025);

        assertThat(response.getCleanSheets())
                .extracting(LeaguePlayerRankingResponseDto.Row::getPlayerId)
                .containsExactly(10L, 20L, 30L);
        assertThat(response.getCleanSheets().get(0).getCleanSheets()).isEqualTo(4);
        assertThat(response.getMinutes())
                .filteredOn(row -> row.getPlayerId().equals(40L))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getCleanSheets()).isZero();
                    assertThat(row.getSavePercentage()).isNull();
                });
        assertThat(response.getSavePercentages())
                .extracting(LeaguePlayerRankingResponseDto.Row::getPlayerId)
                .containsExactly(20L, 10L);
        assertThat(response.getSavePercentages())
                .allSatisfy(row -> assertThat(row.isProvisional()).isFalse());
    }

    @Test
    void recognizesSupportedGoalkeeperPositionFormats() {
        Team team = team(48L, "West Ham");
        Player gPlayer = player(11L, "G Keeper");
        Player gkPlayer = player(12L, "GK Keeper");
        Player goalkeeperPlayer = player(13L, "Goalkeeper");
        Player otherPlayer = player(14L, "Other");

        List<PlayerTeamSeasonStat> stats = List.of(
                statWithPosition(gPlayer, team, "G"),
                statWithPosition(gkPlayer, team, "GK"),
                statWithPosition(goalkeeperPlayer, team, "Goalkeeper"),
                statWithPosition(otherPlayer, team, "Defender")
        );

        when(playerTeamSeasonStatRepository.findAllForLeagueRankings(39L, 2025)).thenReturn(stats);
        when(playerFixtureStatRepository.findRankingMatchAggregates(eq(List.of(11L, 12L, 13L, 14L)), eq(2025)))
                .thenReturn(List.of(
                        aggregate(11L, 0, 0, 0, 1),
                        aggregate(12L, 0, 0, 0, 1),
                        aggregate(13L, 0, 0, 0, 1),
                        aggregate(14L, 0, 0, 0, 10)
                ));
        when(playerFixtureStatRepository.findLatestTeamByPlayerIdsAndSeason(
                eq(List.of(11L, 12L, 13L, 14L)),
                eq(2025)
        )).thenReturn(List.of());
        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(standing(team, 10)));

        LeaguePlayerRankingResponseDto response = service.getRankings(39, 2025);

        assertThat(response.getCleanSheets())
                .extracting(LeaguePlayerRankingResponseDto.Row::getPlayerId)
                .containsExactly(11L, 12L, 13L);
    }

    @Test
    void excludesRatingsBelowFiveAppearancesAndUsesStablePlayerIdFallback() {
        Team team = team(47L, "Tottenham");
        Player first = player(100L, "First");
        Player second = player(200L, "Second");
        Player smallSample = player(300L, "Small");

        PlayerTeamSeasonStat firstStat = stat(first, team, 1, 0, 500, 7.2, 0, 0, 0, 0, 0);
        PlayerTeamSeasonStat secondStat = stat(second, team, 1, 0, 500, 7.2, 0, 0, 0, 0, 0);
        PlayerTeamSeasonStat smallStat = stat(smallSample, team, 1, 0, 360, 9.5, 0, 0, 0, 0, 0, 4);

        when(playerTeamSeasonStatRepository.findAllForLeagueRankings(39L, 2025))
                .thenReturn(List.of(secondStat, smallStat, firstStat));
        when(playerFixtureStatRepository.findRankingMatchAggregates(eq(List.of(200L, 300L, 100L)), eq(2025)))
                .thenReturn(List.of());
        when(playerFixtureStatRepository.findLatestTeamByPlayerIdsAndSeason(
                eq(List.of(200L, 300L, 100L)),
                eq(2025)
        )).thenReturn(List.of());
        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025)).thenReturn(List.of(standing(team, 5)));

        LeaguePlayerRankingResponseDto response = service.getRankings(39, 2025);

        assertThat(response.getRatings())
                .extracting(LeaguePlayerRankingResponseDto.Row::getPlayerId)
                .containsExactly(100L, 200L);
        assertThat(response.getRatings())
                .allSatisfy(row -> assertThat(row.isProvisional()).isFalse());
    }

    @Test
    void assignsCompetitionRanksWhenGoalAndPenaltyGoalCountsAreEqual() {
        Team team = team(51L, "Tie Team");
        Player first = player(501L, "First");
        Player second = player(502L, "Second");
        Player third = player(503L, "Third");
        Player fourth = player(504L, "Fourth");

        List<PlayerTeamSeasonStat> stats = List.of(
                stat(third, team, 3, 0, 300, 7.0, 0, 0, 0, 0, 0),
                stat(first, team, 3, 0, 900, 8.0, 5, 0, 0, 0, 0),
                stat(fourth, team, 2, 0, 900, 8.5, 5, 0, 0, 0, 0),
                stat(second, team, 3, 0, 600, 7.5, 2, 0, 0, 0, 0)
        );

        when(playerTeamSeasonStatRepository.findAllForLeagueRankings(39L, 2025)).thenReturn(stats);
        when(playerFixtureStatRepository.findRankingMatchAggregates(
                eq(List.of(503L, 501L, 504L, 502L)), eq(2025)
        )).thenReturn(List.of());
        when(playerFixtureStatRepository.findLatestTeamByPlayerIdsAndSeason(
                eq(List.of(503L, 501L, 504L, 502L)), eq(2025)
        )).thenReturn(List.of());
        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025))
                .thenReturn(List.of(standing(team, 1)));

        LeaguePlayerRankingResponseDto response = service.getRankings(39, 2025);

        assertThat(response.getGoals())
                .extracting(LeaguePlayerRankingResponseDto.Row::getPlayerId)
                .containsExactly(501L, 502L, 503L, 504L);
        assertThat(response.getGoals())
                .extracting(LeaguePlayerRankingResponseDto.Row::getRank)
                .containsExactly(1, 1, 1, 4);
    }

    @Test
    void relaxesRatingAndSaveAttemptThresholdsAtTheStartOfTheSeason() {
        Team team = team(50L, "Early Season Team");
        Player ratingEligible = player(401L, "Rating Eligible");
        Player ratingTooFew = player(402L, "Rating Too Few");
        Player saveEligible = player(403L, "Save Eligible");
        Player saveTooFew = player(404L, "Save Too Few");

        List<PlayerTeamSeasonStat> stats = List.of(
                stat(ratingEligible, team, 0, 0, 180, 8.5, 0, 0, 0, 0, 0, 2),
                stat(ratingTooFew, team, 0, 0, 90, 9.5, 0, 0, 0, 0, 0, 1),
                stat(saveEligible, team, 0, 0, 90, 7.0, 0, 0, 0, 3, 1, 1),
                stat(saveTooFew, team, 0, 0, 90, 7.0, 0, 0, 0, 2, 1, 1)
        );

        when(playerTeamSeasonStatRepository.findAllForLeagueRankings(39L, 2025)).thenReturn(stats);
        when(playerFixtureStatRepository.findRankingMatchAggregates(
                eq(List.of(401L, 402L, 403L, 404L)), eq(2025)
        )).thenReturn(List.of());
        when(playerFixtureStatRepository.findLatestTeamByPlayerIdsAndSeason(
                eq(List.of(401L, 402L, 403L, 404L)), eq(2025)
        )).thenReturn(List.of());
        when(teamStandingRepository.findAllByLeagueIdAndSeason(39, 2025))
                .thenReturn(List.of(standing(team, 1, 2)));

        LeaguePlayerRankingResponseDto response = service.getRankings(39, 2025);

        assertThat(response.getRatings()).singleElement().satisfies(row -> {
            assertThat(row.getPlayerId()).isEqualTo(401L);
            assertThat(row.isProvisional()).isTrue();
        });
        assertThat(response.getSavePercentages()).singleElement().satisfies(row -> {
            assertThat(row.getPlayerId()).isEqualTo(403L);
            assertThat(row.getSavePercentage()).isEqualTo(75.0);
            assertThat(row.isProvisional()).isTrue();
        });
    }

    private PlayerTeamSeasonStat stat(
            Player player,
            Team team,
            int goals,
            int penaltyGoals,
            int minutes,
            double rating,
            int assists,
            int yellowCards,
            int redCards,
            int saves,
            int conceded
    ) {
        return stat(player, team, goals, penaltyGoals, minutes, rating, assists, yellowCards, redCards, saves, conceded, 10);
    }

    private PlayerTeamSeasonStat stat(
            Player player,
            Team team,
            int goals,
            int penaltyGoals,
            int minutes,
            double rating,
            int assists,
            int yellowCards,
            int redCards,
            int saves,
            int conceded,
            int appearances
    ) {
        return PlayerTeamSeasonStat.builder()
                .player(player)
                .team(team)
                .leagueId(39L)
                .season(2025)
                .position(saves > 0 ? "Goalkeeper" : "Attacker")
                .appearances(appearances)
                .minutes(minutes)
                .rating(rating)
                .goals(goals)
                .penaltyScored(penaltyGoals)
                .assists(assists)
                .yellowCards(yellowCards)
                .redCards(redCards)
                .saves(saves)
                .conceded(conceded)
                .build();
    }

    private PlayerTeamSeasonStat statWithPosition(Player player, Team team, String position) {
        return PlayerTeamSeasonStat.builder()
                .player(player)
                .team(team)
                .leagueId(39L)
                .season(2025)
                .position(position)
                .appearances(10)
                .minutes(900)
                .rating(7.0)
                .goals(0)
                .assists(0)
                .saves(10)
                .conceded(2)
                .build();
    }

    private Player player(Long id, String name) {
        return Player.builder().playerId(id).name(name).build();
    }

    private Team team(Long id, String name) {
        return Team.builder().teamId(id).name(name).build();
    }

    private TeamStanding standing(Team team, int rank) {
        return standing(team, rank, 38);
    }

    private TeamStanding standing(Team team, int rank, int played) {
        return TeamStanding.builder()
                .team(team)
                .leagueId(39)
                .season(2025)
                .rank(rank)
                .played(played)
                .build();
    }

    private PlayerFixtureStatRepository.LatestPlayerTeam latestTeam(
            Long playerId,
            Long teamId,
            LocalDateTime fixtureDate,
            Long fixtureId
    ) {
        return new PlayerFixtureStatRepository.LatestPlayerTeam() {
            public Long getPlayerId() { return playerId; }
            public Long getTeamId() { return teamId; }
            public LocalDateTime getFixtureDate() { return fixtureDate; }
            public Long getFixtureId() { return fixtureId; }
        };
    }

    private PlayerFixtureStatRepository.PlayerRankingMatchAggregate aggregate(
            Long playerId,
            long goalMatches,
            long assistMatches,
            long attackPointMatches,
            long cleanSheets
    ) {
        return new PlayerFixtureStatRepository.PlayerRankingMatchAggregate() {
            public Long getPlayerId() { return playerId; }
            public Long getGoalMatches() { return goalMatches; }
            public Long getAssistMatches() { return assistMatches; }
            public Long getAttackPointMatches() { return attackPointMatches; }
            public Long getCleanSheets() { return cleanSheets; }
        };
    }
}

package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.media.service.MediaUrlService;
import com.son.soccerStreaming.team.entity.Team;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureServiceTest {

    @Mock
    private FixtureRepository fixtureRepository;
    @Mock
    private MediaUrlService mediaUrlService;

    @InjectMocks
    private FixtureService fixtureService;

    @Test
    void getFixtureReturnsSummary() {
        Team homeTeam = Team.builder()
                .teamId(47L)
                .name("Tottenham")
                .logoUrl("home.png")
                .build();
        Team awayTeam = Team.builder()
                .teamId(49L)
                .name("Chelsea")
                .logoUrl("away.png")
                .build();
        Fixture fixture = Fixture.builder()
                .id(1L)
                .fixtureId(100L)
                .fixtureDate(LocalDateTime.of(2026, 5, 22, 12, 0))
                .season(2025)
                .round(38)
                .referee("Michael Oliver")
                .venueName("Tottenham Hotspur Stadium")
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .homeScore(2)
                .awayScore(1)
                .fixtureStatus("FINISHED")
                .statusShort("FT")
                .statusLong("Match Finished")
                .elapsed(90)
                .extra(4)
                .build();

        when(fixtureRepository.findByFixtureId(100L)).thenReturn(Optional.of(fixture));
        when(mediaUrlService.teamLogoUrl(homeTeam)).thenReturn("home.png");
        when(mediaUrlService.teamLogoUrl(awayTeam)).thenReturn("away.png");

        var response = fixtureService.getFixture(100L);

        assertThat(response.getFixtureId()).isEqualTo(100L);
        assertThat(response.getHomeTeamId()).isEqualTo(47L);
        assertThat(response.getAwayTeamId()).isEqualTo(49L);
        assertThat(response.getHomeTeamName()).isEqualTo("Tottenham");
        assertThat(response.getAwayTeamLogoUrl()).isEqualTo("away.png");
        assertThat(response.getHomeScore()).isEqualTo(2);
        assertThat(response.getAwayScore()).isEqualTo(1);
        assertThat(response.getRound()).isEqualTo(38);
        assertThat(response.getSeason()).isEqualTo(2025);
        assertThat(response.getReferee()).isEqualTo("Michael Oliver");
        assertThat(response.getVenueName()).isEqualTo("Tottenham Hotspur Stadium");
        assertThat(response.getFixtureStatus()).isEqualTo("FINISHED");
        assertThat(response.getStatusShort()).isEqualTo("FT");
        assertThat(response.getStatusLong()).isEqualTo("Match Finished");
        assertThat(response.getElapsed()).isEqualTo(90);
        assertThat(response.getExtra()).isEqualTo(4);
    }

    @Test
    void getFixtureThrowsWhenFixtureDoesNotExist() {
        when(fixtureRepository.findByFixtureId(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixtureService.getFixture(404L))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void getHeadToHeadIncludesSelectedFinishedFixtureAndAggregatesFromCurrentHomeTeamPerspective() {
        Team tottenham = Team.builder()
                .id(1L)
                .teamId(47L)
                .name("Tottenham")
                .build();
        Team chelsea = Team.builder()
                .id(2L)
                .teamId(49L)
                .name("Chelsea")
                .build();
        Fixture current = Fixture.builder()
                .fixtureId(100L)
                .leagueId(39)
                .fixtureDate(LocalDateTime.of(2026, 5, 22, 12, 0))
                .homeTeam(tottenham)
                .awayTeam(chelsea)
                .homeScore(1)
                .awayScore(0)
                .fixtureStatus("FINISHED")
                .build();
        Fixture homeWin = Fixture.builder()
                .fixtureId(90L)
                .leagueId(39)
                .fixtureDate(LocalDateTime.of(2025, 5, 10, 12, 0))
                .homeTeam(tottenham)
                .awayTeam(chelsea)
                .homeScore(2)
                .awayScore(1)
                .fixtureStatus("FINISHED")
                .build();
        Fixture awayWinWithSidesReversed = Fixture.builder()
                .fixtureId(80L)
                .leagueId(39)
                .fixtureDate(LocalDateTime.of(2024, 5, 10, 12, 0))
                .homeTeam(chelsea)
                .awayTeam(tottenham)
                .homeScore(1)
                .awayScore(3)
                .fixtureStatus("FT")
                .build();
        Fixture draw = Fixture.builder()
                .fixtureId(70L)
                .leagueId(39)
                .fixtureDate(LocalDateTime.of(2023, 5, 10, 12, 0))
                .homeTeam(chelsea)
                .awayTeam(tottenham)
                .homeScore(0)
                .awayScore(0)
                .fixtureStatus("FT")
                .build();

        when(fixtureRepository.findWithTeamsByFixtureId(100L)).thenReturn(Optional.of(current));
        when(fixtureRepository.findHeadToHeadFixtures(
                org.mockito.ArgumentMatchers.eq(39),
                org.mockito.ArgumentMatchers.eq(47L),
                org.mockito.ArgumentMatchers.eq(49L),
                org.mockito.ArgumentMatchers.eq(List.of("FINISHED", "FT", "AET", "PEN")),
                org.mockito.ArgumentMatchers.eq(10)
        )).thenReturn(List.of(current, homeWin, awayWinWithSidesReversed, draw));

        var response = fixtureService.getHeadToHead(100L);

        assertThat(response.getSummary().getMatches()).isEqualTo(4);
        assertThat(response.getSummary().getHomeWins()).isEqualTo(3);
        assertThat(response.getSummary().getDraws()).isEqualTo(1);
        assertThat(response.getSummary().getAwayWins()).isZero();
        assertThat(response.getSummary().getHomeGoals()).isEqualTo(6);
        assertThat(response.getSummary().getAwayGoals()).isEqualTo(2);
        assertThat(response.getRecentMatches()).extracting(FixtureResponseDto.HeadToHeadMatch::getFixtureId)
                .containsExactly(100L, 90L, 80L, 70L);
    }

    @Test
    void getRecentFixturesPassesRoundFilterAndReturnsRound() {
        Team homeTeam = Team.builder().teamId(47L).name("Tottenham").build();
        Team awayTeam = Team.builder().teamId(49L).name("Chelsea").build();
        Fixture fixture = Fixture.builder()
                .id(1L)
                .fixtureId(100L)
                .fixtureDate(LocalDateTime.of(2026, 5, 22, 12, 0))
                .round(38)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .fixtureStatus("SCHEDULED")
                .build();

        when(fixtureRepository.findRecentFixturesWithCursor(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.eq(38),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(100)
        )).thenReturn(List.of(fixture));

        var response = fixtureService.getRecentFixtures(null, 2025, 38, LocalDate.of(2026, 5, 22), 100);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getRound()).isEqualTo(38);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fixtureRepository).findRecentFixturesWithCursor(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.eq(38),
                startCaptor.capture(),
                endCaptor.capture(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(100)
        );
        assertThat(endCaptor.getValue()).isEqualTo(startCaptor.getValue().plusDays(1));
    }

    @Test
    void getRecentFixturesPassesDateRangeAndTeamFilter() {
        when(fixtureRepository.findRecentFixturesWithCursor(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(47L),
                org.mockito.ArgumentMatchers.eq(20)
        )).thenReturn(List.of());

        fixtureService.getRecentFixtures(
                10L,
                2025,
                null,
                null,
                LocalDate.of(2026, 5, 15),
                LocalDate.of(2026, 5, 21),
                47L,
                20
        );

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(fixtureRepository).findRecentFixturesWithCursor(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(2025),
                org.mockito.ArgumentMatchers.isNull(),
                startCaptor.capture(),
                endCaptor.capture(),
                org.mockito.ArgumentMatchers.eq(47L),
                org.mockito.ArgumentMatchers.eq(20)
        );
        assertThat(endCaptor.getValue()).isEqualTo(startCaptor.getValue().plusDays(7));
    }

    @Test
    void getFixtureMetaUsesLatestStartedFixtureDateAsDefaultDateSource() {
        when(fixtureRepository.findMinFixtureDateBySeason(2022))
                .thenReturn(Optional.of(LocalDateTime.of(2022, 8, 5, 19, 0)));
        when(fixtureRepository.findMaxFixtureDateBySeason(2022))
                .thenReturn(Optional.of(LocalDateTime.of(2023, 5, 28, 15, 30)));
        when(fixtureRepository.findLatestStartedFixtureDateBySeason(2022))
                .thenReturn(Optional.of(LocalDateTime.of(2023, 4, 30, 15, 30)));
        when(fixtureRepository.findMinRoundBySeason(2022)).thenReturn(Optional.of(1));
        when(fixtureRepository.findMaxRoundBySeason(2022)).thenReturn(Optional.of(38));

        var response = fixtureService.getFixtureMeta(2022);

        assertThat(response.getMinDate()).isEqualTo(LocalDate.of(2022, 8, 6));
        assertThat(response.getMaxDate()).isEqualTo(LocalDate.of(2023, 5, 29));
        assertThat(response.getLatestStartedDate()).isEqualTo(LocalDate.of(2023, 5, 1));
        assertThat(response.getMinRound()).isEqualTo(1);
        assertThat(response.getMaxRound()).isEqualTo(38);
    }
}

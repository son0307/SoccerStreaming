package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.fixture.dto.CursorResponse;
import com.son.soccerStreaming.fixture.dto.FixtureMetaResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.global.util.DateTimeUtils;
import com.son.soccerStreaming.media.service.MediaUrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FixtureService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<String> FINISHED_FIXTURE_STATUSES = List.of("FINISHED", "FT", "AET", "PEN");
    private static final int HEAD_TO_HEAD_MATCH_LIMIT = 10;

    private final FixtureRepository fixtureRepository;
    private final MediaUrlService mediaUrlService;

    @Transactional(readOnly = true)
    public FixtureResponseDto.Summary getFixture(Long fixtureId) {
        Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        return toSummary(fixture);
    }

    @Transactional(readOnly = true)
    public FixtureResponseDto.HeadToHead getHeadToHead(Long fixtureId) {
        Fixture fixture = fixtureRepository.findWithTeamsByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));
        Long homeTeamId = fixture.getHomeTeam().getTeamId();
        Long awayTeamId = fixture.getAwayTeam().getTeamId();
        List<Fixture> matches = fixtureRepository.findHeadToHeadFixtures(
                fixture.getLeagueId(),
                homeTeamId,
                awayTeamId,
                FINISHED_FIXTURE_STATUSES,
                HEAD_TO_HEAD_MATCH_LIMIT
        );

        int homeWins = 0;
        int draws = 0;
        int awayWins = 0;
        int homeGoals = 0;
        int awayGoals = 0;
        for (Fixture match : matches) {
            Scoreline scoreline = scorelineForFixtureTeams(match, homeTeamId);
            homeGoals += scoreline.homeTeamGoals();
            awayGoals += scoreline.awayTeamGoals();
            if (scoreline.homeTeamGoals() > scoreline.awayTeamGoals()) {
                homeWins++;
            } else if (scoreline.homeTeamGoals() < scoreline.awayTeamGoals()) {
                awayWins++;
            } else {
                draws++;
            }
        }

        return FixtureResponseDto.HeadToHead.builder()
                .summary(FixtureResponseDto.HeadToHeadSummary.builder()
                        .homeTeamId(homeTeamId)
                        .homeTeamName(fixture.getHomeTeam().getName())
                        .homeTeamNameKo(fixture.getHomeTeam().getKoreanName())
                        .awayTeamId(awayTeamId)
                        .awayTeamName(fixture.getAwayTeam().getName())
                        .awayTeamNameKo(fixture.getAwayTeam().getKoreanName())
                        .matches(matches.size())
                        .homeWins(homeWins)
                        .draws(draws)
                        .awayWins(awayWins)
                        .homeGoals(homeGoals)
                        .awayGoals(awayGoals)
                        .build())
                .recentMatches(matches.stream()
                        .map(this::toHeadToHeadMatch)
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public CursorResponse<FixtureResponseDto.Summary> getRecentFixtures(Long cursorId, Integer season, Integer round,
                                                                        LocalDate date, int size) {
        return getRecentFixtures(cursorId, season, round, date, null, null, null, size);
    }

    @Transactional(readOnly = true)
    public CursorResponse<FixtureResponseDto.Summary> getRecentFixtures(Long cursorId, Integer season, Integer round,
                                                                        LocalDate date, LocalDate dateFrom,
                                                                        LocalDate dateTo, Long teamId, int size) {
        DateRange dateRange = utcRangeForKoreaDates(date, dateFrom, dateTo);
        List<Fixture> fixtures = fixtureRepository.findRecentFixturesWithCursor(
                cursorId, season, round, dateRange.startDateTime(), dateRange.endDateTime(), teamId, size);

        boolean hasNext = false;
        if (fixtures.size() > size) {
            hasNext = true;
            fixtures.remove(fixtures.size() - 1);
        }

        List<FixtureResponseDto.Summary> content = fixtures.stream()
                .map(this::toSummary)
                .toList();

        Long nextCursor = hasNext ? fixtures.get(fixtures.size() - 1).getId() : null;

        return CursorResponse.<FixtureResponseDto.Summary>builder()
                .content(content)
                .hasNext(hasNext)
                .nextCursor(nextCursor)
                .build();
    }

    @Transactional(readOnly = true)
    public FixtureMetaResponseDto getFixtureMeta(Integer season) {
        return FixtureMetaResponseDto.builder()
                .minDate(toKoreaDate(fixtureRepository.findMinFixtureDateBySeason(season).orElse(null)))
                .maxDate(toKoreaDate(fixtureRepository.findMaxFixtureDateBySeason(season).orElse(null)))
                .latestStartedDate(toKoreaDate(
                        fixtureRepository.findLatestStartedFixtureDateBySeason(season).orElse(null)))
                .minRound(fixtureRepository.findMinRoundBySeason(season).orElse(null))
                .maxRound(fixtureRepository.findMaxRoundBySeason(season).orElse(null))
                .build();
    }

    private FixtureResponseDto.Summary toSummary(Fixture fixture) {
        return FixtureResponseDto.Summary.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .season(fixture.getSeason())
                .round(fixture.getRound())
                .referee(fixture.getReferee())
                .venueName(fixture.getVenueName())
                .venueNameKo(fixture.getVenueNameKo())
                .homeTeamId(fixture.getHomeTeam().getTeamId())
                .awayTeamId(fixture.getAwayTeam().getTeamId())
                .homeTeamName(fixture.getHomeTeam().getName())
                .homeTeamNameKo(fixture.getHomeTeam().getKoreanName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .awayTeamNameKo(fixture.getAwayTeam().getKoreanName())
                .homeTeamLogoUrl(mediaUrlService.teamLogoUrl(fixture.getHomeTeam()))
                .awayTeamLogoUrl(mediaUrlService.teamLogoUrl(fixture.getAwayTeam()))
                .homeScore(valueOf(fixture.getHomeScore()))
                .awayScore(valueOf(fixture.getAwayScore()))
                .homeWinner(fixture.getHomeWinner())
                .awayWinner(fixture.getAwayWinner())
                .halftimeHomeScore(fixture.getHalftimeHomeScore())
                .halftimeAwayScore(fixture.getHalftimeAwayScore())
                .fulltimeHomeScore(fixture.getFulltimeHomeScore())
                .fulltimeAwayScore(fixture.getFulltimeAwayScore())
                .extratimeHomeScore(fixture.getExtratimeHomeScore())
                .extratimeAwayScore(fixture.getExtratimeAwayScore())
                .penaltyHomeScore(fixture.getPenaltyHomeScore())
                .penaltyAwayScore(fixture.getPenaltyAwayScore())
                .fixtureStatus(fixture.getFixtureStatus())
                .statusShort(fixture.getStatusShort())
                .statusLong(fixture.getStatusLong())
                .elapsed(fixture.getElapsed())
                .extra(fixture.getExtra())
                .build();
    }

    private FixtureResponseDto.HeadToHeadMatch toHeadToHeadMatch(Fixture fixture) {
        return FixtureResponseDto.HeadToHeadMatch.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .season(fixture.getSeason())
                .round(fixture.getRound())
                .homeTeamId(fixture.getHomeTeam().getTeamId())
                .homeTeamName(fixture.getHomeTeam().getName())
                .homeTeamNameKo(fixture.getHomeTeam().getKoreanName())
                .awayTeamId(fixture.getAwayTeam().getTeamId())
                .awayTeamName(fixture.getAwayTeam().getName())
                .awayTeamNameKo(fixture.getAwayTeam().getKoreanName())
                .homeScore(fixture.getHomeScore())
                .awayScore(fixture.getAwayScore())
                .fixtureStatus(fixture.getFixtureStatus())
                .build();
    }

    private Scoreline scorelineForFixtureTeams(Fixture fixture, Long homeTeamId) {
        if (homeTeamId.equals(fixture.getHomeTeam().getTeamId())) {
            return new Scoreline(valueOf(fixture.getHomeScore()), valueOf(fixture.getAwayScore()));
        }
        return new Scoreline(valueOf(fixture.getAwayScore()), valueOf(fixture.getHomeScore()));
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }

    private DateRange utcRangeForKoreaDates(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null || dateTo != null) {
            LocalDateTime startDateTime = dateFrom == null ? null : koreaStartAsUtc(dateFrom);
            LocalDateTime endDateTime = dateTo == null ? null : koreaStartAsUtc(dateTo.plusDays(1));
            return new DateRange(startDateTime, endDateTime);
        }

        if (date == null) {
            return new DateRange(null, null);
        }

        return new DateRange(koreaStartAsUtc(date), koreaStartAsUtc(date.plusDays(1)));
    }

    private LocalDateTime koreaStartAsUtc(LocalDate date) {
        return date.atStartOfDay(KOREA_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private LocalDate toKoreaDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atOffset(ZoneOffset.UTC).atZoneSameInstant(KOREA_ZONE).toLocalDate();
    }

    private record DateRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
    }

    private record Scoreline(int homeTeamGoals, int awayTeamGoals) {
    }
}

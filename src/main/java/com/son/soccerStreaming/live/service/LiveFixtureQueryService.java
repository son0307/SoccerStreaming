package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.fixture.dto.FixtureResponseDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.global.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LiveFixtureQueryService {

    private static final String LIVE_STATUS = "LIVE";

    private final FixtureRepository fixtureRepository;

    @Transactional(readOnly = true)
    public List<FixtureResponseDto.Summary> getTodayLiveFixtures(Integer season) {
        return fixtureRepository
                .findAllBySeasonAndFixtureStatusOrderByFixtureDateAsc(season, LIVE_STATUS)
                .stream()
                .map(this::toFixtureSummary)
                .toList();
    }

    private FixtureResponseDto.Summary toFixtureSummary(Fixture fixture) {
        return FixtureResponseDto.Summary.builder()
                .fixtureId(fixture.getFixtureId())
                .fixtureDate(DateTimeUtils.utcToKorea(fixture.getFixtureDate()))
                .round(fixture.getRound())
                .homeTeamId(fixture.getHomeTeam().getTeamId())
                .awayTeamId(fixture.getAwayTeam().getTeamId())
                .homeTeamName(fixture.getHomeTeam().getName())
                .homeTeamNameKo(fixture.getHomeTeam().getKoreanName())
                .awayTeamName(fixture.getAwayTeam().getName())
                .awayTeamNameKo(fixture.getAwayTeam().getKoreanName())
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

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}

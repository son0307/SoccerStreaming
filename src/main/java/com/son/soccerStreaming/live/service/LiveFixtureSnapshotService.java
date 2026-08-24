package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.fixture.service.FixtureStatService;

import com.son.soccerStreaming.fixture.service.FixtureRedisService;

import com.son.soccerStreaming.live.dto.LiveFixtureSnapshotDto;
import com.son.soccerStreaming.fixture.dto.FixtureEventDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.global.exception.CustomException;
import com.son.soccerStreaming.global.exception.ErrorCode;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LiveFixtureSnapshotService {

    private final FixtureRepository fixtureRepository;
    private final FixtureStatService fixtureStatService;
    private final FixtureRedisService fixtureRedisService;

    @Transactional(readOnly = true)
    public LiveFixtureSnapshotDto rebuildAndCacheSnapshot(Long fixtureId, FixtureEventDto latestEvent) {
        Fixture fixture = fixtureRepository.findByFixtureId(fixtureId)
                .orElseThrow(() -> new CustomException(ErrorCode.FIXTURE_NOT_FOUND));

        LiveFixtureSnapshotDto snapshot = LiveFixtureSnapshotDto.builder()
                .fixtureId(fixtureId)
                .statusShort(fixture.getStatusShort())
                .statusLong(fixture.getStatusLong())
                .fixtureStatus(fixture.getFixtureStatus())
                .elapsed(fixture.getElapsed())
                .extra(fixture.getExtra())
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
                .homeTeamStat(fixtureStatService.getTeamStatSummary(
                        fixtureId,
                        fixture.getHomeTeam().getTeamId(),
                        valueOf(fixture.getHomeScore())
                ))
                .awayTeamStat(fixtureStatService.getTeamStatSummary(
                        fixtureId,
                        fixture.getAwayTeam().getTeamId(),
                        valueOf(fixture.getAwayScore())
                ))
                .latestEvent(latestEvent)
                .build();

        fixtureRedisService.saveLiveSnapshot(snapshot);
        return snapshot;
    }

    private int valueOf(Integer value) {
        return value == null ? 0 : value;
    }
}

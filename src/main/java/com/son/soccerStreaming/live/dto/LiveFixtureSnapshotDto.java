package com.son.soccerStreaming.live.dto;

import com.son.soccerStreaming.fixture.dto.FixtureStatResponseDto;

import com.son.soccerStreaming.fixture.dto.FixtureEventDto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiveFixtureSnapshotDto {

    private Long fixtureId;
    private String statusShort;
    private String statusLong;
    private String fixtureStatus;
    private Integer elapsed;
    private Integer extra;
    private Boolean homeWinner;
    private Boolean awayWinner;
    private Integer halftimeHomeScore;
    private Integer halftimeAwayScore;
    private Integer fulltimeHomeScore;
    private Integer fulltimeAwayScore;
    private Integer extratimeHomeScore;
    private Integer extratimeAwayScore;
    private Integer penaltyHomeScore;
    private Integer penaltyAwayScore;
    private FixtureStatResponseDto.TeamStatSummary homeTeamStat;
    private FixtureStatResponseDto.TeamStatSummary awayTeamStat;
    private FixtureEventDto latestEvent;
}

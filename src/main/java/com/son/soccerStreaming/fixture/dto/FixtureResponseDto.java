package com.son.soccerStreaming.fixture.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

public class FixtureResponseDto {
    @Getter
    @Builder
    @AllArgsConstructor
    public static class Summary {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private Integer season;
        private Integer round;
        private String referee;
        private String venueName;
        private String venueNameKo;
        private Long homeTeamId;
        private Long awayTeamId;
        private String homeTeamName;
        private String homeTeamNameKo;
        private String awayTeamName;
        private String awayTeamNameKo;
        private String homeTeamLogoUrl;
        private String awayTeamLogoUrl;
        private int homeScore;
        private int awayScore;
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
        private String fixtureStatus;
        private String statusShort;
        private String statusLong;
        private Integer elapsed;
        private Integer extra;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class HeadToHead {
        private HeadToHeadSummary summary;
        private java.util.List<HeadToHeadMatch> recentMatches;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class HeadToHeadSummary {
        private Long homeTeamId;
        private String homeTeamName;
        private String homeTeamNameKo;
        private Long awayTeamId;
        private String awayTeamName;
        private String awayTeamNameKo;
        private int matches;
        private int homeWins;
        private int draws;
        private int awayWins;
        private int homeGoals;
        private int awayGoals;
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class HeadToHeadMatch {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private Integer season;
        private Integer round;
        private Long homeTeamId;
        private String homeTeamName;
        private String homeTeamNameKo;
        private Long awayTeamId;
        private String awayTeamName;
        private String awayTeamNameKo;
        private Integer homeScore;
        private Integer awayScore;
        private String fixtureStatus;
    }
}

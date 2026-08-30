package com.son.soccerStreaming.team.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class TeamStandingResponseDto {

    private Integer season;
    private Integer rank;
    private TeamInfo team;
    private Integer points;
    private Integer goalsDiff;
    private String group;
    private String form;
    private String status;
    private String description;
    private Record all;
    private Record home;
    private Record away;
    private RecentForm recentForm;
    private LiveMatch liveMatch;
    private NextMatch nextMatch;
    private LocalDateTime updatedAt;

    @Getter
    @Builder
    public static class TeamInfo {
        private Long id;
        private String name;
        private String nameKo;
        private String logo;
    }

    @Getter
    @Builder
    public static class Record {
        private Integer played;
        private Integer win;
        private Integer draw;
        private Integer lose;
        private Goals goals;
    }

    @Getter
    @Builder
    public static class Goals {
        private Integer goalsFor;
        private Integer goalsAgainst;
    }

    @Getter
    @Builder
    public static class RecentForm {
        private Integer played;
        private Integer win;
        private Integer draw;
        private Integer lose;
        private Goals goals;
        private Integer points;
        private Integer goalsDiff;
        private List<String> results;
        private List<RecentMatch> matches;
    }

    @Getter
    @Builder
    public static class RecentMatch {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private TeamInfo opponent;
        private String venue;
        private Integer scoreFor;
        private Integer scoreAgainst;
        private String result;
    }

    @Getter
    @Builder
    public static class LiveMatch {
        private Long fixtureId;
        private Integer scoreFor;
        private Integer scoreAgainst;
        private String statusShort;
        private Integer elapsed;
        private Integer extra;
        private String result;
    }

    @Getter
    @Builder
    public static class NextMatch {
        private Long fixtureId;
        private LocalDateTime fixtureDate;
        private TeamInfo opponent;
        private String venue;
    }
}

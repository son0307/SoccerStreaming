package com.son.soccerStreaming.league.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaguePlayerRankingResponseDto {

    private Integer leagueId;
    private Integer season;
    private List<Row> goals;
    private List<Row> assists;
    private List<Row> attackPoints;
    private List<Row> ratings;
    private List<Row> minutes;
    private List<Row> yellowCards;
    private List<Row> redCards;
    private List<Row> saves;
    private List<Row> cleanSheets;
    private List<Row> savePercentages;

    @Getter
    @Builder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Row {
        private Integer rank;
        private Long playerId;
        private String playerName;
        private String playerNameKo;
        private String photoUrl;
        private String position;
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private String teamLogoUrl;
        private int appearances;
        private int minutes;
        private double rating;
        private int goals;
        private int penaltyGoals;
        private int assists;
        private int attackPoints;
        private int goalMatches;
        private int assistMatches;
        private int attackPointMatches;
        private int yellowCards;
        private int redCards;
        private int saves;
        private int conceded;
        private int cleanSheets;
        private Double savePercentage;
        private boolean provisional;

        public Row(
                Integer rank,
                Long playerId,
                String playerName,
                String photoUrl,
                String position,
                Long teamId,
                String teamName,
                String teamLogoUrl,
                int appearances,
                int minutes,
                double rating,
                int goals,
                int penaltyGoals,
                int assists,
                int attackPoints,
                int goalMatches,
                int assistMatches,
                int attackPointMatches,
                int yellowCards,
                int redCards,
                int saves,
                int conceded,
                int cleanSheets,
                Double savePercentage
        ) {
            this(
                    rank,
                    playerId,
                    playerName,
                    null,
                    photoUrl,
                    position,
                    teamId,
                    teamName,
                    null,
                    teamLogoUrl,
                    appearances,
                    minutes,
                    rating,
                    goals,
                    penaltyGoals,
                    assists,
                    attackPoints,
                    goalMatches,
                    assistMatches,
                    attackPointMatches,
                    yellowCards,
                    redCards,
                    saves,
                    conceded,
                    cleanSheets,
                    savePercentage,
                    false
            );
        }
    }
}

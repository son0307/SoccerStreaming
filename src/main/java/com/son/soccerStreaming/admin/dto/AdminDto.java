package com.son.soccerStreaming.admin.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

public class AdminDto {

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamUpdateRequest {
        private Long version;
        private String name;
        private String koreanName;
        private String code;
        private String country;
        private Integer founded;
        private String logoUrl;
        private Long venueId;
        private String venueName;
        private String venueNameKo;
        private String venueAddress;
        private String venueCity;
        private Integer capacity;
        private String surface;
        private String venueImageUrl;

        public void normalizeTextFields() {
            name = normalizeText(name);
            koreanName = normalizeText(koreanName);
            code = normalizeText(code);
            country = normalizeText(country);
            logoUrl = normalizeText(logoUrl);
            venueName = normalizeText(venueName);
            venueNameKo = normalizeText(venueNameKo);
            venueAddress = normalizeText(venueAddress);
            venueCity = normalizeText(venueCity);
            surface = normalizeText(surface);
            venueImageUrl = normalizeText(venueImageUrl);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerUpdateRequest {
        private Long version;
        private String name;
        private String koreanName;
        private String firstname;
        private String lastname;
        private Integer age;
        private LocalDate birthDate;
        private String birthPlace;
        private String birthCountry;
        private String nationality;
        private Integer height;
        private Integer weight;
        private String position;
        private Integer number;
        private String photoUrl;

        public void normalizeTextFields() {
            name = normalizeText(name);
            koreanName = normalizeText(koreanName);
            firstname = normalizeText(firstname);
            lastname = normalizeText(lastname);
            birthPlace = normalizeText(birthPlace);
            birthCountry = normalizeText(birthCountry);
            nationality = normalizeText(nationality);
            position = normalizeText(position);
            photoUrl = normalizeText(photoUrl);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixtureUpdateRequest {
        private Long version;
        private OffsetDateTime fixtureDate;
        private String referee;
        private String timezone;
        private Long timestamp;
        private Long firstPeriod;
        private Long secondPeriod;
        private Integer round;
        private Integer season;
        private Long venueId;
        private String venueName;
        private String venueNameKo;
        private String venueCity;
        private String statusShort;
        private String statusLong;
        private Integer elapsed;
        private String fixtureStatus;
        private Integer homeScore;
        private Integer awayScore;
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
        private String homeFormation;
        private String awayFormation;
        private String homeCoachName;
        private String awayCoachName;
        private String homePlayerColorPrimary;
        private String homePlayerColorNumber;
        private String homePlayerColorBorder;
        private String homeGoalkeeperColorPrimary;
        private String homeGoalkeeperColorNumber;
        private String homeGoalkeeperColorBorder;
        private String awayPlayerColorPrimary;
        private String awayPlayerColorNumber;
        private String awayPlayerColorBorder;
        private String awayGoalkeeperColorPrimary;
        private String awayGoalkeeperColorNumber;
        private String awayGoalkeeperColorBorder;

        public void normalizeTextFields() {
            referee = normalizeText(referee);
            timezone = normalizeText(timezone);
            venueName = normalizeText(venueName);
            venueNameKo = normalizeText(venueNameKo);
            venueCity = normalizeText(venueCity);
            statusShort = normalizeText(statusShort);
            statusLong = normalizeText(statusLong);
            fixtureStatus = normalizeText(fixtureStatus);
            homeFormation = normalizeText(homeFormation);
            awayFormation = normalizeText(awayFormation);
            homeCoachName = normalizeText(homeCoachName);
            awayCoachName = normalizeText(awayCoachName);
            homePlayerColorPrimary = normalizeText(homePlayerColorPrimary);
            homePlayerColorNumber = normalizeText(homePlayerColorNumber);
            homePlayerColorBorder = normalizeText(homePlayerColorBorder);
            homeGoalkeeperColorPrimary = normalizeText(homeGoalkeeperColorPrimary);
            homeGoalkeeperColorNumber = normalizeText(homeGoalkeeperColorNumber);
            homeGoalkeeperColorBorder = normalizeText(homeGoalkeeperColorBorder);
            awayPlayerColorPrimary = normalizeText(awayPlayerColorPrimary);
            awayPlayerColorNumber = normalizeText(awayPlayerColorNumber);
            awayPlayerColorBorder = normalizeText(awayPlayerColorBorder);
            awayGoalkeeperColorPrimary = normalizeText(awayGoalkeeperColorPrimary);
            awayGoalkeeperColorNumber = normalizeText(awayGoalkeeperColorNumber);
            awayGoalkeeperColorBorder = normalizeText(awayGoalkeeperColorBorder);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixtureEventUpdateRequest {
        private Long version;
        private Integer elapsed;
        private Integer extra;
        private Long teamId;
        private Long playerId;
        private Long assistPlayerId;
        private String eventType;
        private String eventDetail;
        private String comments;

        public void normalizeTextFields() {
            eventType = normalizeText(eventType);
            eventDetail = normalizeText(eventDetail);
            comments = normalizeText(comments);
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixtureLineupUpdateRequest {
        private Long version;
        private Integer jerseyNumber;
        private String position;
        private String grid;
        private Boolean starter;

        public void normalizeTextFields() {
            position = normalizeText(position);
            grid = normalizeText(grid);
        }
    }

    private static String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixtureTeamStatUpdateRequest {
        private Long version;
        private Integer shotsOnGoal;
        private Integer shotsOffGoal;
        private Integer totalShots;
        private Integer blockedShots;
        private Integer shotsInsideBox;
        private Integer shotsOutsideBox;
        private Integer fouls;
        private Integer cornerKicks;
        private Integer offsides;
        private Integer ballPossession;
        private Integer yellowCards;
        private Integer redCards;
        private Integer goalkeeperSaves;
        private Integer totalPasses;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Double expectedGoals;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FixturePlayerStatUpdateRequest {
        private Long version;
        private Integer minutesPlayed;
        private Double rating;
        private Boolean captain;
        private Boolean substitute;
        private Integer goals;
        private Integer assists;
        private Integer conceded;
        private Integer saves;
        private Integer shotsTotal;
        private Integer shotsOnTarget;
        private Integer passesTotal;
        private Integer passesKey;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Integer tacklesTotal;
        private Integer blocks;
        private Integer interceptions;
        private Integer duelsTotal;
        private Integer duelsWon;
        private Integer dribblesAttempts;
        private Integer dribblesSuccess;
        private Integer dribblesPast;
        private Integer foulsDrawn;
        private Integer foulsCommitted;
        private Integer yellowCards;
        private Integer redCards;
        private Integer offsides;
        private Integer penaltyWon;
        private Integer penaltyCommitted;
        private Integer penaltyScored;
        private Integer penaltyMissed;
        private Integer penaltySaved;
    }

    @Getter
    @Builder
    public static class TeamAdminResponse {
        private Long teamId;
        private long version;
        private String name;
        private String koreanName;
        private String code;
        private String country;
        private Integer founded;
        private String logoUrl;
        private String logoDisplayUrl;
        private boolean adminLogo;
        private Long venueId;
        private String venueName;
        private String venueNameKo;
        private String venueAddress;
        private String venueCity;
        private Integer capacity;
        private String surface;
        private String venueImageUrl;
        private String venueImageDisplayUrl;
        private boolean adminVenueImage;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class PlayerAdminResponse {
        private Long playerId;
        private long version;
        private String name;
        private String koreanName;
        private String firstname;
        private String lastname;
        private Integer age;
        private LocalDate birthDate;
        private String birthPlace;
        private String birthCountry;
        private String nationality;
        private Integer height;
        private Integer weight;
        private String position;
        private Integer number;
        private String photoUrl;
        private String photoDisplayUrl;
        private boolean adminPhoto;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class FixtureAdminSummaryResponse {
        private Long fixtureId;
        private OffsetDateTime fixtureDate;
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

    @Getter
    @Builder
    public static class FixtureTeamOptionResponse {
        private Long teamId;
        private String name;
        private String koreanName;
    }

    @Getter
    @Builder
    public static class FixtureAdminDetailResponse {
        private FixtureAdminResponse fixture;
        private List<ManualOverrideResponse> eventOverrides;
        private List<FixtureEventAdminResponse> events;
        private List<FixtureLineupAdminResponse> lineups;
        private List<FixtureTeamStatAdminResponse> teamStats;
        private List<FixturePlayerStatAdminResponse> playerStats;
    }

    @Getter
    @Builder
    public static class FixtureAdminResponse {
        private Long fixtureId;
        private long version;
        private OffsetDateTime fixtureDate;
        private String referee;
        private String timezone;
        private Long timestamp;
        private Long firstPeriod;
        private Long secondPeriod;
        private Integer round;
        private Integer season;
        private Long venueId;
        private String venueName;
        private String venueNameKo;
        private String venueCity;
        private String statusShort;
        private String statusLong;
        private Integer elapsed;
        private String fixtureStatus;
        private Integer homeScore;
        private Integer awayScore;
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
        private Long homeTeamId;
        private String homeTeamName;
        private String homeTeamNameKo;
        private Long awayTeamId;
        private String awayTeamName;
        private String awayTeamNameKo;
        private String homeFormation;
        private String awayFormation;
        private String homeCoachName;
        private String awayCoachName;
        private String homePlayerColorPrimary;
        private String homePlayerColorNumber;
        private String homePlayerColorBorder;
        private String homeGoalkeeperColorPrimary;
        private String homeGoalkeeperColorNumber;
        private String homeGoalkeeperColorBorder;
        private String awayPlayerColorPrimary;
        private String awayPlayerColorNumber;
        private String awayPlayerColorBorder;
        private String awayGoalkeeperColorPrimary;
        private String awayGoalkeeperColorNumber;
        private String awayGoalkeeperColorBorder;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class FixtureEventAdminResponse {
        private Integer eventSequence;
        private Integer elapsed;
        private Integer extra;
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private Long playerId;
        private String playerName;
        private String playerNameKo;
        private Long assistPlayerId;
        private String assistPlayerName;
        private String assistPlayerNameKo;
        private String eventType;
        private String eventDetail;
        private String comments;
    }

    @Getter
    @Builder
    public static class FixtureLineupAdminResponse {
        private Long lineupId;
        private long version;
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private Long playerId;
        private String playerName;
        private String playerNameKo;
        private Integer jerseyNumber;
        private String position;
        private String grid;
        private boolean starter;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class FixtureTeamStatAdminResponse {
        private Long teamId;
        private long version;
        private String teamName;
        private String teamNameKo;
        private Integer shotsOnGoal;
        private Integer shotsOffGoal;
        private Integer totalShots;
        private Integer blockedShots;
        private Integer shotsInsideBox;
        private Integer shotsOutsideBox;
        private Integer fouls;
        private Integer cornerKicks;
        private Integer offsides;
        private Integer ballPossession;
        private Integer yellowCards;
        private Integer redCards;
        private Integer goalkeeperSaves;
        private Integer totalPasses;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Double expectedGoals;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class FixturePlayerStatAdminResponse {
        private Long playerId;
        private long version;
        private String playerName;
        private String playerNameKo;
        private Long teamId;
        private String teamName;
        private String teamNameKo;
        private Integer minutesPlayed;
        private Double rating;
        private Boolean captain;
        private Boolean substitute;
        private Integer goals;
        private Integer assists;
        private Integer conceded;
        private Integer saves;
        private Integer shotsTotal;
        private Integer shotsOnTarget;
        private Integer passesTotal;
        private Integer passesKey;
        private Integer passesAccurate;
        private Integer passAccuracy;
        private Integer tacklesTotal;
        private Integer blocks;
        private Integer interceptions;
        private Integer duelsTotal;
        private Integer duelsWon;
        private Integer dribblesAttempts;
        private Integer dribblesSuccess;
        private Integer dribblesPast;
        private Integer foulsDrawn;
        private Integer foulsCommitted;
        private Integer yellowCards;
        private Integer redCards;
        private Integer offsides;
        private Integer penaltyWon;
        private Integer penaltyCommitted;
        private Integer penaltyScored;
        private Integer penaltyMissed;
        private Integer penaltySaved;
        private List<ManualOverrideResponse> manualOverrides;
    }

    @Getter
    @Builder
    public static class ManualOverrideResponse {
        private String fieldName;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class SyncResponse {
        private Long jobId;
        private String task;
        private boolean success;
        private boolean queued;
        private int count;
        private String message;
    }

    @Getter
    @Builder
    public static class SyncJobErrorResponse {
        private String unitType;
        private String unitId;
        private String message;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class SyncJobResponse {
        private Long id;
        private String task;
        private String adminEmail;
        private String targetType;
        private Long targetId;
        private Integer season;
        private String details;
        private String status;
        private boolean active;
        private int totalUnits;
        private int processedUnits;
        private int successfulUnits;
        private int failedUnits;
        private int savedCount;
        private String phase;
        private String unitLabel;
        private String message;
        private LocalDateTime createdAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private List<SyncJobErrorResponse> errors;
    }

    @Getter
    @Builder
    public static class SyncJobListResponse {
        private List<SyncJobResponse> jobs;
    }

    @Getter
    @Builder
    public static class SyncCancelResponse {
        private Long jobId;
        private String status;
        private String message;
    }

    @Getter
    @Builder
    public static class AuditLogResponse {
        private Long id;
        private String adminEmail;
        private String type;
        private String syncCategory;
        private String targetType;
        private Long targetId;
        private String message;
        private String details;
        private String provider;
        private boolean success;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class AuditLogListResponse {
        private List<AuditLogResponse> logs;
        private int page;
        private int size;
        private int totalPages;
        private long totalElements;
    }

    @Getter
    @Builder
    public static class SyncStatusResponse {
        private List<SyncStatusItem> statuses;
    }

    @Getter
    @Builder
    public static class SyncStatusItem {
        private String task;
        private String label;
        private OffsetDateTime lastSyncedAt;
        private OffsetDateTime lastAttemptAt;
        private OffsetDateTime lastSuccessAt;
        private OffsetDateTime lastFailureAt;
        private Integer failureCount;
        private String lastErrorMessage;
        private String status;
        private String provider;
        private String lastOperation;
        private String lastErrorCategory;
        private Integer lastHttpStatus;
        private Integer lastAttemptCount;
    }
}

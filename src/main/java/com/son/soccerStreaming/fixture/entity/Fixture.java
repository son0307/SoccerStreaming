package com.son.soccerStreaming.fixture.entity;

import com.son.soccerStreaming.team.entity.Team;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(indexes = {
        @Index(name = "idx_fixture_league_season_date", columnList = "league_id, season, fixture_date"),
        @Index(name = "idx_fixture_season_date", columnList = "season, fixture_date"),
        @Index(name = "idx_fixture_season_status_date", columnList = "season, fixture_status, fixture_date"),
        @Index(name = "idx_fixture_home_season_date", columnList = "home_team_id, season, fixture_date"),
        @Index(name = "idx_fixture_away_season_date", columnList = "away_team_id, season, fixture_date")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Fixture {

    private static final Pattern ROUND_NUMBER_PATTERN = Pattern.compile("(\\d+)\\s*$");
    private static final Set<String> FINISHED_STATUS_SHORTS = Set.of("FT", "AET", "PEN");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    // API 통신용 고유 경기 ID
    @Column(nullable = false, unique = true)
    private Long fixtureId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    // 경기 메타데이터
    @Column(nullable = false)
    private LocalDateTime fixtureDate;
    private String referee;
    private String timezone;
    private Long timestamp;
    private Long firstPeriod;
    private Long secondPeriod;
    private Integer round;

    @Builder.Default
    @Column(name = "league_id", nullable = false, columnDefinition = "int default 39")
    private Integer leagueId = 39;

    private Integer season;

    // 경기장 정보
    private Long venueId;
    private String venueName;
    private String venueNameKo;
    private String venueCity;

    // 상태 및 스코어
    @Column(length = 10)
    private String statusShort;
    private String statusLong;
    private Integer elapsed;

    // 조회를 간단히 하기 위한 매크로 상태
    @Column(length = 20)
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

    // 포메이션 정보
    @Column(length = 20)
    private String homeFormation;

    @Column(length = 20)
    private String awayFormation;

    // 감독 정보
    private String homeCoachName;
    private String awayCoachName;

    @Column(length = 20)
    private String homePlayerColorPrimary;
    @Column(length = 20)
    private String homePlayerColorNumber;
    @Column(length = 20)
    private String homePlayerColorBorder;
    @Column(length = 20)
    private String homeGoalkeeperColorPrimary;
    @Column(length = 20)
    private String homeGoalkeeperColorNumber;
    @Column(length = 20)
    private String homeGoalkeeperColorBorder;
    @Column(length = 20)
    private String awayPlayerColorPrimary;
    @Column(length = 20)
    private String awayPlayerColorNumber;
    @Column(length = 20)
    private String awayPlayerColorBorder;
    @Column(length = 20)
    private String awayGoalkeeperColorPrimary;
    @Column(length = 20)
    private String awayGoalkeeperColorNumber;
    @Column(length = 20)
    private String awayGoalkeeperColorBorder;

    public boolean isFinished() {
        return "FINISHED".equalsIgnoreCase(fixtureStatus)
                || (statusShort != null && FINISHED_STATUS_SHORTS.contains(statusShort.toUpperCase(Locale.ROOT)));
    }

    public void updateFixtureState(String statusShort, String statusLong, String fixtureStatus,
                                 Integer elapsed, Integer homeScore, Integer awayScore) {
        this.statusShort = statusShort;
        this.statusLong = statusLong;
        this.fixtureStatus = fixtureStatus;
        this.elapsed = elapsed;
        this.homeScore = homeScore;
        this.awayScore = awayScore;
    }

    public void updateFixtureMetadata(LocalDateTime fixtureDate, String referee, String timezone,
                                      Long timestamp, Long firstPeriod, Long secondPeriod,
                                      Long venueId, String venueName, String venueCity) {
        this.fixtureDate = fixtureDate;
        this.referee = referee;
        this.timezone = timezone;
        this.timestamp = timestamp;
        this.firstPeriod = firstPeriod;
        this.secondPeriod = secondPeriod;
        this.venueId = venueId;
        this.venueName = venueName;
        this.venueCity = venueCity;
    }

    public void updateTeamResult(Boolean homeWinner, Boolean awayWinner) {
        this.homeWinner = homeWinner;
        this.awayWinner = awayWinner;
    }

    public void updateVenueKoreanName(String venueNameKo) {
        this.venueNameKo = venueNameKo == null || venueNameKo.isBlank()
                ? null
                : venueNameKo.trim();
    }

    public void updateScoreBreakdown(Integer halftimeHomeScore, Integer halftimeAwayScore,
                                     Integer fulltimeHomeScore, Integer fulltimeAwayScore,
                                     Integer extratimeHomeScore, Integer extratimeAwayScore,
                                     Integer penaltyHomeScore, Integer penaltyAwayScore) {
        this.halftimeHomeScore = halftimeHomeScore;
        this.halftimeAwayScore = halftimeAwayScore;
        this.fulltimeHomeScore = fulltimeHomeScore;
        this.fulltimeAwayScore = fulltimeAwayScore;
        this.extratimeHomeScore = extratimeHomeScore;
        this.extratimeAwayScore = extratimeAwayScore;
        this.penaltyHomeScore = penaltyHomeScore;
        this.penaltyAwayScore = penaltyAwayScore;
    }

    public void updateTactics(String homeFormation, String awayFormation,
                              String homeCoachName, String awayCoachName) {
        this.homeFormation = homeFormation;
        this.awayFormation = awayFormation;
        this.homeCoachName = homeCoachName;
        this.awayCoachName = awayCoachName;
    }

    public void updateRound(String round) {
        Integer parsedRound = parseRoundNumber(round);
        if (parsedRound != null) {
            this.round = parsedRound;
        }
    }

    public static Integer parseRoundNumber(String round) {
        if (round == null || round.isBlank()) {
            return null;
        }

        Matcher matcher = ROUND_NUMBER_PATTERN.matcher(round);
        if (!matcher.find()) {
            return null;
        }

        return Integer.valueOf(matcher.group(1));
    }

    public void updateLeagueAndSeason(Integer leagueId, Integer season) {
        if (leagueId != null) {
            this.leagueId = leagueId;
        }
        this.season = season;
    }

    public void updateHomeLineupColors(String playerPrimary, String playerNumber, String playerBorder,
                                       String goalkeeperPrimary, String goalkeeperNumber, String goalkeeperBorder) {
        this.homePlayerColorPrimary = playerPrimary;
        this.homePlayerColorNumber = playerNumber;
        this.homePlayerColorBorder = playerBorder;
        this.homeGoalkeeperColorPrimary = goalkeeperPrimary;
        this.homeGoalkeeperColorNumber = goalkeeperNumber;
        this.homeGoalkeeperColorBorder = goalkeeperBorder;
    }

    public void updateAwayLineupColors(String playerPrimary, String playerNumber, String playerBorder,
                                       String goalkeeperPrimary, String goalkeeperNumber, String goalkeeperBorder) {
        this.awayPlayerColorPrimary = playerPrimary;
        this.awayPlayerColorNumber = playerNumber;
        this.awayPlayerColorBorder = playerBorder;
        this.awayGoalkeeperColorPrimary = goalkeeperPrimary;
        this.awayGoalkeeperColorNumber = goalkeeperNumber;
        this.awayGoalkeeperColorBorder = goalkeeperBorder;
    }
}

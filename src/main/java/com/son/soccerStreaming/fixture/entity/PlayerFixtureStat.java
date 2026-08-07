package com.son.soccerStreaming.fixture.entity;

import com.son.soccerStreaming.team.entity.Team;

import com.son.soccerStreaming.player.entity.Player;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_fixture_stat", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fixture_id", "player_id"})
}
    , indexes = {
        @Index(name = "idx_player_fixture_stat_player_fixture", columnList = "player_id, fixture_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class PlayerFixtureStat {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    // 💡 연관관계 설정
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    // 출전 정보 (Games)
    private Integer minutesPlayed; // 출전 시간
    private Double rating;         // 평점
    private Boolean isCaptain;     // 주장 여부
    private Boolean isSubstitute;  // 교체 출전 여부

    // 득점 및 도움 (Goals)
    private Integer goals;         // 득점
    private Integer assists;       // 도움
    private Integer conceded;      // 실점
    private Integer saves;         // 선방 (골키퍼 전용)

    // 슈팅 (Shots)
    private Integer shotsTotal;    // 총 슈팅
    private Integer shotsOnTarget; // 유효 슈팅

    // 패스 (Passes)
    private Integer passesTotal;   // 총 패스
    private Integer passesKey;     // 키 패스
    private Integer passesAccurate;// 성공 패스
    private Integer passAccuracy;  // 패스 성공률

    // 수비 (Tackles & Blocks)
    private Integer tacklesTotal;  // 총 태클
    private Integer blocks;        // 블락
    private Integer interceptions; // 가로채기

    // 경합 (Duels & Dribbles)
    private Integer duelsTotal;    // 경합 시도
    private Integer duelsWon;      // 경합 승리
    private Integer dribblesAttempts;// 드리블 시도
    private Integer dribblesSuccess; // 드리블 성공
    private Integer dribblesPast;  // 돌파 허용

    // 파울 및 카드 (Fouls & Cards)
    private Integer foulsDrawn;    // 파울 얻음
    private Integer foulsCommitted;// 파울 범함
    private Integer yellowCards;   // 경고
    private Integer redCards;      // 퇴장
    private Integer offsides;      // 오프사이드

    // 페널티 (Penalty)
    private Integer penaltyWon;    // 페널티킥 얻음
    private Integer penaltyCommitted;// 페널티킥 허용
    private Integer penaltyScored; // 페널티킥 득점
    private Integer penaltyMissed; // 페널티킥 실축
    private Integer penaltySaved;  // 페널티킥 선방 (골키퍼)
    public void updateLiveStat(Integer minutesPlayed, Double rating, Boolean isCaptain, Boolean isSubstitute,
                               Integer goals, Integer assists, Integer conceded, Integer saves,
                               Integer shotsTotal, Integer shotsOnTarget, Integer passesTotal,
                               Integer passesKey, Integer passesAccurate, Integer passAccuracy, Integer tacklesTotal,
                               Integer blocks, Integer interceptions, Integer duelsTotal,
                               Integer duelsWon, Integer dribblesAttempts, Integer dribblesSuccess,
                               Integer dribblesPast, Integer foulsDrawn, Integer foulsCommitted,
                               Integer yellowCards, Integer redCards, Integer offsides,
                               Integer penaltyWon, Integer penaltyCommitted, Integer penaltyScored,
                               Integer penaltyMissed, Integer penaltySaved) {
        boolean appeared = hasAppearance(minutesPlayed);
        updateStatValues(
                normalizeMinutesPlayed(minutesPlayed), rating, isCaptain, isSubstitute,
                normalizeScoringStat(appeared, goals), normalizeScoringStat(appeared, assists), conceded, saves,
                shotsTotal, shotsOnTarget, passesTotal, passesKey, passesAccurate, passAccuracy,
                tacklesTotal, blocks, interceptions, duelsTotal, duelsWon,
                dribblesAttempts, dribblesSuccess, dribblesPast, foulsDrawn, foulsCommitted,
                normalizeYellowCards(yellowCards, redCards), redCards, offsides,
                penaltyWon, penaltyCommitted, penaltyScored, penaltyMissed, penaltySaved
        );
    }

    public void updateStatValues(Integer minutesPlayed, Double rating, Boolean isCaptain, Boolean isSubstitute,
                                 Integer goals, Integer assists, Integer conceded, Integer saves,
                                 Integer shotsTotal, Integer shotsOnTarget, Integer passesTotal,
                                 Integer passesKey, Integer passesAccurate, Integer passAccuracy, Integer tacklesTotal,
                                 Integer blocks, Integer interceptions, Integer duelsTotal,
                                 Integer duelsWon, Integer dribblesAttempts, Integer dribblesSuccess,
                                 Integer dribblesPast, Integer foulsDrawn, Integer foulsCommitted,
                                 Integer yellowCards, Integer redCards, Integer offsides,
                                 Integer penaltyWon, Integer penaltyCommitted, Integer penaltyScored,
                                 Integer penaltyMissed, Integer penaltySaved) {
        this.minutesPlayed = minutesPlayed;
        this.rating = rating;
        this.isCaptain = isCaptain;
        this.isSubstitute = isSubstitute;
        this.goals = goals;
        this.assists = assists;
        this.conceded = conceded;
        this.saves = saves;
        this.shotsTotal = shotsTotal;
        this.shotsOnTarget = shotsOnTarget;
        this.passesTotal = passesTotal;
        this.passesKey = passesKey;
        this.passesAccurate = passesAccurate;
        this.passAccuracy = passAccuracy;
        this.tacklesTotal = tacklesTotal;
        this.blocks = blocks;
        this.interceptions = interceptions;
        this.duelsTotal = duelsTotal;
        this.duelsWon = duelsWon;
        this.dribblesAttempts = dribblesAttempts;
        this.dribblesSuccess = dribblesSuccess;
        this.dribblesPast = dribblesPast;
        this.foulsDrawn = foulsDrawn;
        this.foulsCommitted = foulsCommitted;
        this.yellowCards = yellowCards;
        this.redCards = redCards;
        this.offsides = offsides;
        this.penaltyWon = penaltyWon;
        this.penaltyCommitted = penaltyCommitted;
        this.penaltyScored = penaltyScored;
        this.penaltyMissed = penaltyMissed;
        this.penaltySaved = penaltySaved;
    }

    public static Integer normalizeScoringStat(Integer minutesPlayed, Integer value) {
        return normalizeScoringStat(hasAppearance(minutesPlayed), value);
    }

    public static Integer normalizeMinutesPlayed(Integer minutesPlayed) {
        return hasAppearance(minutesPlayed) ? minutesPlayed : null;
    }

    public void normalizeCards() {
        this.yellowCards = normalizeYellowCards(this.yellowCards, this.redCards);
    }

    public static Integer normalizeYellowCards(Integer yellowCards, Integer redCards) {
        return redCards != null && redCards > 0 ? Integer.valueOf(0) : yellowCards;
    }

    private static Integer normalizeScoringStat(boolean appeared, Integer value) {
        if (!appeared) {
            return null;
        }
        return value != null ? value : 0;
    }

    private static boolean hasAppearance(Integer minutesPlayed) {
        return minutesPlayed != null && minutesPlayed > 0;
    }
}

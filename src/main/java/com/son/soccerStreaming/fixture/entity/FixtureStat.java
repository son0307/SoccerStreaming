package com.son.soccerStreaming.fixture.entity;

import com.son.soccerStreaming.team.entity.Team;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fixture_stat", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fixture_id", "team_id"})
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class FixtureStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fixture_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Fixture fixture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Team team;

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

    public void updateStats(Integer shotsOnGoal, Integer shotsOffGoal, Integer totalShots, Integer blockedShots,
                            Integer shotsInsideBox, Integer shotsOutsideBox, Integer fouls, Integer cornerKicks,
                            Integer offsides, Integer ballPossession, Integer yellowCards, Integer redCards,
                            Integer goalkeeperSaves, Integer totalPasses, Integer passesAccurate,
                            Integer passAccuracy, Double expectedGoals) {
        this.shotsOnGoal = shotsOnGoal;
        this.shotsOffGoal = shotsOffGoal;
        this.totalShots = totalShots;
        this.blockedShots = blockedShots;
        this.shotsInsideBox = shotsInsideBox;
        this.shotsOutsideBox = shotsOutsideBox;
        this.fouls = fouls;
        this.cornerKicks = cornerKicks;
        this.offsides = offsides;
        this.ballPossession = ballPossession;
        this.yellowCards = yellowCards;
        this.redCards = redCards;
        this.goalkeeperSaves = goalkeeperSaves;
        this.totalPasses = totalPasses;
        this.passesAccurate = passesAccurate;
        this.passAccuracy = passAccuracy;
        this.expectedGoals = expectedGoals;
    }
}

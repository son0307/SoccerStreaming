package com.son.soccerStreaming.fixture.entity;

import com.son.soccerStreaming.team.entity.Team;

import com.son.soccerStreaming.player.entity.Player;

import jakarta.persistence.Column;
import jakarta.persistence.ConstraintMode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "fixture_lineup", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"fixture_id", "team_id", "player_id"})
}, indexes = {
        @Index(name = "idx_fixture_lineup_player", columnList = "player_id")
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class FixtureLineup {

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Player player;

    @Column(nullable = false)
    private Integer jerseyNumber;

    @Column(nullable = false, length = 10)
    private String position;

    @Column(length = 10)
    private String grid;

    @Column(nullable = false)
    private boolean isStarter;

    public void updateLineup(Integer jerseyNumber, String position, String grid, boolean isStarter) {
        this.jerseyNumber = jerseyNumber;
        this.position = position;
        this.grid = grid;
        this.isStarter = isStarter;
    }
}

package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.Fixture;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FixtureRepository extends JpaRepository<Fixture, Long>, FixtureRepositoryCustom {
    Optional<Fixture> findByFixtureId(Long fixtureId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM Fixture f WHERE f.fixtureId = :fixtureId")
    Optional<Fixture> findByFixtureIdForEventUpdate(@Param("fixtureId") Long fixtureId);

    @Query("SELECT f FROM Fixture f JOIN FETCH f.homeTeam JOIN FETCH f.awayTeam WHERE f.fixtureId = :fixtureId")
    Optional<Fixture> findWithTeamsByFixtureId(@Param("fixtureId") Long fixtureId);

    List<Fixture> findAllBySeasonOrderByFixtureDateAsc(Integer season);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE (:season IS NULL OR f.season = :season) " +
            "AND (LOWER(f.homeTeam.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(f.awayTeam.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR CAST(f.fixtureId AS string) LIKE CONCAT('%', :keyword, '%')) " +
            "ORDER BY f.fixtureDate DESC")
    List<Fixture> searchAdminFixtures(
            @Param("keyword") String keyword,
            @Param("season") Integer season,
            org.springframework.data.domain.Pageable pageable
    );

    boolean existsByFixtureStatus(String fixtureStatus);

    boolean existsByFixtureDateBetweenAndFixtureStatusIn(LocalDateTime start, LocalDateTime end, Collection<String> fixtureStatuses);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<Fixture> findAllBySeasonAndFixtureDateGreaterThanEqualAndFixtureDateLessThanOrderByFixtureDateAsc(
            Integer season,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("SELECT MIN(f.fixtureDate) FROM Fixture f WHERE f.season = :season")
    Optional<LocalDateTime> findMinFixtureDateBySeason(@Param("season") Integer season);

    @Query("SELECT MAX(f.fixtureDate) FROM Fixture f WHERE f.season = :season")
    Optional<LocalDateTime> findMaxFixtureDateBySeason(@Param("season") Integer season);

    @Query("SELECT MAX(f.fixtureDate) FROM Fixture f " +
            "WHERE f.season = :season AND f.fixtureStatus IN ('LIVE', 'FINISHED')")
    Optional<LocalDateTime> findLatestStartedFixtureDateBySeason(@Param("season") Integer season);

    @Query("SELECT MIN(f.round) FROM Fixture f WHERE f.season = :season AND f.round IS NOT NULL")
    Optional<Integer> findMinRoundBySeason(@Param("season") Integer season);

    @Query("SELECT MAX(f.round) FROM Fixture f WHERE f.season = :season AND f.round IS NOT NULL")
    Optional<Integer> findMaxRoundBySeason(@Param("season") Integer season);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    List<Fixture> findAllBySeasonAndFixtureStatusOrderByFixtureDateAsc(Integer season, String fixtureStatus);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.season = :season " +
            "AND (f.fixtureStatus IN :finishedStatuses OR f.statusShort IN :finishedStatuses) " +
            "AND f.homeScore IS NOT NULL " +
            "AND f.awayScore IS NOT NULL " +
            "ORDER BY f.fixtureDate DESC")
    List<Fixture> findFinishedWithScoresBySeasonOrderByFixtureDateDesc(
            @Param("season") Integer season,
            @Param("finishedStatuses") Collection<String> finishedStatuses
    );

    List<Fixture> findAllByFixtureStatus(String fixtureStatus);

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.season = :season " +
            "AND (f.homeTeam.teamId = :teamId OR f.awayTeam.teamId = :teamId) " +
            "AND f.fixtureDate <= :now " +
            "ORDER BY f.fixtureDate DESC")
    List<Fixture> findRecentByTeam(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("now") LocalDateTime now,
            org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.season = :season " +
            "AND (f.homeTeam.teamId = :teamId OR f.awayTeam.teamId = :teamId) " +
            "AND f.fixtureDate > :now " +
            "ORDER BY f.fixtureDate ASC")
    List<Fixture> findNextByTeam(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("now") LocalDateTime now,
            org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.leagueId = :leagueId " +
            "AND f.season = :season " +
            "AND f.fixtureStatus = 'SCHEDULED' " +
            "AND f.fixtureDate > :now " +
            "ORDER BY f.fixtureDate ASC, f.fixtureId ASC")
    List<Fixture> findUpcomingScheduledByLeagueAndSeason(
            @Param("leagueId") Integer leagueId,
            @Param("season") Integer season,
            @Param("now") LocalDateTime now
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.season = :season " +
            "AND (f.homeTeam.teamId = :teamId OR f.awayTeam.teamId = :teamId) " +
            "AND f.fixtureStatus = 'LIVE' " +
            "ORDER BY f.fixtureDate ASC")
    List<Fixture> findLiveByTeam(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            org.springframework.data.domain.Pageable pageable
    );

    @EntityGraph(attributePaths = {"homeTeam", "awayTeam"})
    @Query("SELECT f FROM Fixture f " +
            "WHERE f.season = :season " +
            "AND (f.homeTeam.teamId = :teamId OR f.awayTeam.teamId = :teamId) " +
            "AND (f.fixtureStatus = 'FINISHED' OR f.statusShort IN :finishedStatusShorts) " +
            "ORDER BY f.fixtureDate DESC, f.fixtureId DESC")
    List<Fixture> findRecentFinishedByTeam(
            @Param("teamId") Long teamId,
            @Param("season") Integer season,
            @Param("finishedStatusShorts") Collection<String> finishedStatusShorts,
            org.springframework.data.domain.Pageable pageable
    );
}

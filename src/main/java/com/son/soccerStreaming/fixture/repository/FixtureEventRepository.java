package com.son.soccerStreaming.fixture.repository;

import com.son.soccerStreaming.fixture.entity.FixtureEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FixtureEventRepository extends JpaRepository<FixtureEvent, Long> {

    @EntityGraph(attributePaths = {"team", "player", "assistPlayer"})
    @Query("select e from FixtureEvent e " +
            "where e.fixture.fixtureId = :fixtureId " +
            "order by e.elapsed asc, coalesce(e.extra, 0) asc, e.eventSequence asc")
    List<FixtureEvent> findAllByFixtureFixtureIdOrderByMatchTimeAsc(@Param("fixtureId") Long fixtureId);

    @Query("select max(e.eventSequence) from FixtureEvent e where e.fixture.fixtureId = :fixtureId")
    Integer findMaxEventSequenceByFixtureId(@Param("fixtureId") Long fixtureId);

    Optional<FixtureEvent> findByFixtureFixtureIdAndEventSequence(Long fixtureId, Integer eventSequence);
}

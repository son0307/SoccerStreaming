package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballStandingDto;
import com.son.soccerStreaming.apifootball.event.StandingSyncCompleted;
import com.son.soccerStreaming.global.config.RedisCacheConfig;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.TeamStanding;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.team.repository.TeamStandingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballStandingSyncService {

    private final ApiFootballClient apiFootballClient;
    private final TeamRepository teamRepository;
    private final TeamStandingRepository teamStandingRepository;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;
    private final ApplicationEventPublisher eventPublisher;

    @Caching(evict = {
            @CacheEvict(
                    cacheNames = RedisCacheConfig.FAVORITE_TEAM_CARD_CACHE,
                    allEntries = true
            ),
            @CacheEvict(
                    cacheManager = RedisCacheConfig.RANKINGS_CACHE_MANAGER,
                    cacheNames = RedisCacheConfig.LEAGUE_TEAM_RANKINGS_CACHE,
                    allEntries = true
            )
    })
    @Transactional
    public int syncStandings(Integer league, Integer season) {
        apiFootballSyncStatusService.recordAttempt("standings", "Standings", season);
        List<ApiFootballStandingDto.StandingResponse> responses = apiFootballClient.getStandings(league, season);
        int syncedCount = 0;

        for (ApiFootballStandingDto.StandingResponse response : responses) {
            ApiFootballStandingDto.League leagueInfo = response.getLeague();
            if (leagueInfo == null || leagueInfo.getStandings() == null) {
                continue;
            }

            Integer responseSeason = leagueInfo.getSeason() != null ? leagueInfo.getSeason() : season;
            Integer responseLeagueId = leagueInfo.getId() != null ? Math.toIntExact(leagueInfo.getId()) : league;
            for (List<ApiFootballStandingDto.Standing> groupStandings : leagueInfo.getStandings()) {
                for (ApiFootballStandingDto.Standing standingInfo : groupStandings) {
                    if (upsertStanding(responseLeagueId, responseSeason, standingInfo)) {
                        syncedCount++;
                    }
                }
            }
        }

        log.info("API-Football standing sync completed. league={}, season={}, count={}", league, season, syncedCount);
        apiFootballSyncStatusService.recordSuccess("standings", "Standings", season);
        eventPublisher.publishEvent(new StandingSyncCompleted(league, season));
        return syncedCount;
    }

    private boolean upsertStanding(Integer leagueId, Integer season, ApiFootballStandingDto.Standing standingInfo) {
        if (standingInfo == null || standingInfo.getTeam() == null || standingInfo.getTeam().getId() == null) {
            return false;
        }

        Optional<Team> team = teamRepository.findByTeamId(standingInfo.getTeam().getId());
        if (team.isEmpty()) {
            log.warn("Skip standing sync because team does not exist. teamId={}", standingInfo.getTeam().getId());
            return false;
        }

        TeamStanding standing = teamStandingRepository
                .findByTeamTeamIdAndLeagueIdAndSeason(team.get().getTeamId(), leagueId, season)
                .orElseGet(() -> TeamStanding.builder()
                        .team(team.get())
                        .leagueId(leagueId)
                        .season(season)
                        .build());

        ApiFootballStandingDto.Record all = standingInfo.getAll();
        ApiFootballStandingDto.Record home = standingInfo.getHome();
        ApiFootballStandingDto.Record away = standingInfo.getAway();

        standing.updateStanding(
                standingInfo.getRank(),
                standingInfo.getPoints(),
                standingInfo.getGoalsDiff(),
                standingInfo.getGroup(),
                standingInfo.getForm(),
                standingInfo.getStatus(),
                standingInfo.getDescription(),
                playedOf(all),
                winOf(all),
                drawOf(all),
                loseOf(all),
                goalsForOf(all),
                goalsAgainstOf(all),
                playedOf(home),
                winOf(home),
                drawOf(home),
                loseOf(home),
                goalsForOf(home),
                goalsAgainstOf(home),
                playedOf(away),
                winOf(away),
                drawOf(away),
                loseOf(away),
                goalsForOf(away),
                goalsAgainstOf(away),
                parseUpdatedAt(standingInfo.getUpdate())
        );

        teamStandingRepository.save(standing);
        return true;
    }

    private Integer playedOf(ApiFootballStandingDto.Record record) {
        return record != null ? record.getPlayed() : null;
    }

    private Integer winOf(ApiFootballStandingDto.Record record) {
        return record != null ? record.getWin() : null;
    }

    private Integer drawOf(ApiFootballStandingDto.Record record) {
        return record != null ? record.getDraw() : null;
    }

    private Integer loseOf(ApiFootballStandingDto.Record record) {
        return record != null ? record.getLose() : null;
    }

    private Integer goalsForOf(ApiFootballStandingDto.Record record) {
        return record != null && record.getGoals() != null ? record.getGoals().getGoalsFor() : null;
    }

    private Integer goalsAgainstOf(ApiFootballStandingDto.Record record) {
        return record != null && record.getGoals() != null ? record.getGoals().getAgainst() : null;
    }

    private LocalDateTime parseUpdatedAt(String update) {
        if (update == null || update.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(update).toLocalDateTime();
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse API-Football standing update time. update={}", update);
            return LocalDateTime.now();
        }
    }
}

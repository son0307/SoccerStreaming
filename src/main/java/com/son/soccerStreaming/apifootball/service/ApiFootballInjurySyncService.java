package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballInjuryDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.player.entity.Player;
import com.son.soccerStreaming.player.entity.PlayerAbsence;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.player.repository.PlayerAbsenceRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballInjurySyncService {

    private static final int CHUNK_SIZE = 100;
    private static final String DEFAULT_ABSENCE_TYPE = "Missing Fixture";
    private static final String DEFAULT_REASON = "Unknown";

    private final ApiFootballClient apiFootballClient;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final PlayerAbsenceRepository playerAbsenceRepository;
    private final ApiFootballPlayerSyncService apiFootballPlayerSyncService;
    private final TransactionTemplate transactionTemplate;
    private final EntityManager entityManager;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;

    public int syncInjuries(Integer league, Integer season) {
        return syncInjuries(league, season, SyncProgressReporter.NO_OP);
    }

    public int syncInjuries(Integer league, Integer season, SyncProgressReporter progressReporter) {
        apiFootballSyncStatusService.recordAttempt("injuries", "Injuries", season);
        progressReporter.beginPhase("FETCHING_INJURIES", 0, "request", 0);
        progressReporter.checkCancelled();
        List<ApiFootballInjuryDto.InjuryResponse> injuries = Optional.ofNullable(apiFootballClient.getInjuries(league, season))
                .orElse(List.of());
        int syncedCount = 0;

        List<List<ApiFootballInjuryDto.InjuryResponse>> chunks = chunks(injuries);
        int processedUnits = 0;
        int failedUnits = 0;
        InjurySyncSummary summary = InjurySyncSummary.empty();
        progressReporter.beginPhase("SYNCING_INJURIES", injuries.size(), "injuries", 0);
        for (int i = 0; i < chunks.size(); i++) {
            progressReporter.checkCancelled();
            List<ApiFootballInjuryDto.InjuryResponse> chunk = chunks.get(i);
            log.debug("API-Football injury chunk started. chunk={}/{}, size={}", i + 1, chunks.size(), chunk.size());
            try {
                InjurySyncSummary chunkSummary = syncInjuryChunk(chunk);
                summary = summary.plus(chunkSummary);
                syncedCount += chunkSummary.syncedCount();
                processedUnits += chunk.size();
                int skippedCount = chunkSummary.skippedCount();
                failedUnits += skippedCount;
                if (skippedCount > 0) {
                    progressReporter.error("INJURY_CHUNK", String.valueOf(i + 1),
                            chunkSummary.failureMessage());
                    log.atWarn()
                            .addKeyValue("event.action", "api-football-injury-sync")
                            .addKeyValue("event.outcome", "partial_failure")
                            .addKeyValue("api_football.injury_chunk", i + 1)
                            .addKeyValue("api_football.invalid_payload_count", chunkSummary.invalidPayloadCount())
                            .addKeyValue("api_football.missing_fixture_count", chunkSummary.missingFixtureCount())
                            .addKeyValue("api_football.missing_fixture_ids", chunkSummary.missingFixtureIds())
                            .addKeyValue("api_football.missing_team_count", chunkSummary.missingTeamCount())
                            .addKeyValue("api_football.missing_team_ids", chunkSummary.missingTeamIds())
                            .addKeyValue("api_football.missing_player_count", chunkSummary.missingPlayerCount())
                            .addKeyValue("api_football.missing_player_ids", chunkSummary.missingPlayerIds())
                            .log("Some injury records were skipped.");
                }
                progressReporter.update(processedUnits, processedUnits - failedUnits, failedUnits, syncedCount);
            } catch (SyncCancelledException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                processedUnits += chunk.size();
                failedUnits += chunk.size();
                progressReporter.error("INJURY_CHUNK", String.valueOf(i + 1), exception.getMessage());
                progressReporter.update(processedUnits, processedUnits - failedUnits, failedUnits, syncedCount);
                throw exception;
            }
            log.info("API-Football injury chunk completed. chunk={}/{}, size={}", i + 1, chunks.size(), chunk.size());
        }

        progressReporter.checkCancelled();
        if (summary.hasFailures()) {
            ApiFootballInjuryReferenceSyncException failure = new ApiFootballInjuryReferenceSyncException(
                    summary.invalidPayloadCount(),
                    summary.missingFixtureCount(),
                    summary.missingTeamCount(),
                    summary.missingPlayerCount(),
                    summary.missingFixtureIds(),
                    summary.missingTeamIds(),
                    summary.missingPlayerIds()
            );
            apiFootballSyncStatusService.recordFailure("injuries", "Injuries", season, failure);
            throw failure;
        }
        log.info("API-Football injury sync completed. league={}, season={}, count={}", league, season, syncedCount);
        apiFootballSyncStatusService.recordSuccess("injuries", "Injuries", season);
        return syncedCount;
    }

    private InjurySyncSummary syncInjuryChunk(List<ApiFootballInjuryDto.InjuryResponse> injuries) {
        InjurySyncSummary result = transactionTemplate.execute(status -> {
            InjurySyncSummary summary = InjurySyncSummary.empty();
            for (ApiFootballInjuryDto.InjuryResponse injury : injuries) {
                summary = summary.add(upsertInjury(injury));
            }
            // Clear each injury chunk so bulk admin sync does not keep every absence managed until completion.
            entityManager.flush();
            entityManager.clear();
            return summary;
        });
        return result != null ? result : InjurySyncSummary.empty();
    }

    private InjurySyncResult upsertInjury(ApiFootballInjuryDto.InjuryResponse injury) {
        if (injury == null) {
            return InjurySyncResult.of(InjurySyncOutcome.INVALID_PAYLOAD);
        }
        ApiFootballInjuryDto.FixtureInfo fixtureInfo = injury.getFixture();
        ApiFootballInjuryDto.TeamInfo teamInfo = injury.getTeam();
        ApiFootballInjuryDto.PlayerInfo playerInfo = injury.getPlayer();

        if (fixtureInfo == null || fixtureInfo.getId() == null
                || teamInfo == null || teamInfo.getId() == null
                || playerInfo == null || playerInfo.getId() == null) {
            return InjurySyncResult.of(InjurySyncOutcome.INVALID_PAYLOAD);
        }

        Optional<Fixture> fixture = fixtureRepository.findByFixtureId(fixtureInfo.getId());
        Optional<Team> team = teamRepository.findByTeamId(teamInfo.getId());
        if (fixture.isEmpty()) {
            log.warn("Skip injury sync because fixture does not exist. fixtureId={}, teamId={}",
                    fixtureInfo.getId(), teamInfo.getId());
            return InjurySyncResult.missing(InjurySyncOutcome.FIXTURE_NOT_FOUND, fixtureInfo.getId());
        }
        if (team.isEmpty()) {
            log.warn("Skip injury sync because team does not exist. fixtureId={}, teamId={}",
                    fixtureInfo.getId(), teamInfo.getId());
            return InjurySyncResult.missing(InjurySyncOutcome.TEAM_NOT_FOUND, teamInfo.getId());
        }

        Optional<Player> player = apiFootballPlayerSyncService.findOrFetchPlayer(
                playerInfo.getId(),
                playerInfo.getName(),
                team.get(),
                null,
                null,
                playerInfo.getPhoto()
        );
        if (player.isEmpty()) {
            log.warn("Skip injury sync because player does not exist. fixtureId={}, playerId={}",
                    fixtureInfo.getId(), playerInfo.getId());
            return InjurySyncResult.missing(InjurySyncOutcome.PLAYER_NOT_FOUND, playerInfo.getId());
        }

        PlayerAbsence absence = playerAbsenceRepository
                .findByPlayerPlayerIdAndFixtureFixtureId(player.get().getPlayerId(), fixture.get().getFixtureId())
                .orElseGet(() -> PlayerAbsence.builder()
                        .fixture(fixture.get())
                        .team(team.get())
                        .player(player.get())
                        .build());

        absence.updateAbsence(
                valueOrDefault(playerInfo.getType(), DEFAULT_ABSENCE_TYPE),
                valueOrDefault(playerInfo.getReason(), DEFAULT_REASON)
        );
        playerAbsenceRepository.save(absence);
        return InjurySyncResult.of(InjurySyncOutcome.SYNCED);
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private List<List<ApiFootballInjuryDto.InjuryResponse>> chunks(List<ApiFootballInjuryDto.InjuryResponse> injuries) {
        List<List<ApiFootballInjuryDto.InjuryResponse>> chunks = new ArrayList<>();
        for (int i = 0; i < injuries.size(); i += CHUNK_SIZE) {
            chunks.add(injuries.subList(i, Math.min(i + CHUNK_SIZE, injuries.size())));
        }
        return chunks;
    }

    private enum InjurySyncOutcome {
        SYNCED,
        INVALID_PAYLOAD,
        FIXTURE_NOT_FOUND,
        TEAM_NOT_FOUND,
        PLAYER_NOT_FOUND
    }

    private record InjurySyncResult(InjurySyncOutcome outcome, Long missingReferenceId) {
        private static InjurySyncResult of(InjurySyncOutcome outcome) {
            return new InjurySyncResult(outcome, null);
        }

        private static InjurySyncResult missing(InjurySyncOutcome outcome, Long missingReferenceId) {
            return new InjurySyncResult(outcome, missingReferenceId);
        }
    }

    private record InjurySyncSummary(
            int syncedCount,
            int invalidPayloadCount,
            int missingFixtureCount,
            int missingTeamCount,
            int missingPlayerCount,
            Set<Long> missingFixtureIdSet,
            Set<Long> missingTeamIdSet,
            Set<Long> missingPlayerIdSet
    ) {
        private static InjurySyncSummary empty() {
            return new InjurySyncSummary(0, 0, 0, 0, 0,
                    new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
        }

        private InjurySyncSummary add(InjurySyncResult result) {
            Set<Long> fixtureIds = new LinkedHashSet<>(missingFixtureIdSet);
            Set<Long> teamIds = new LinkedHashSet<>(missingTeamIdSet);
            Set<Long> playerIds = new LinkedHashSet<>(missingPlayerIdSet);
            return switch (result.outcome()) {
                case SYNCED -> new InjurySyncSummary(
                        syncedCount + 1, invalidPayloadCount,
                        missingFixtureCount, missingTeamCount, missingPlayerCount,
                        fixtureIds, teamIds, playerIds);
                case INVALID_PAYLOAD -> new InjurySyncSummary(
                        syncedCount, invalidPayloadCount + 1,
                        missingFixtureCount, missingTeamCount, missingPlayerCount,
                        fixtureIds, teamIds, playerIds);
                case FIXTURE_NOT_FOUND -> {
                    fixtureIds.add(result.missingReferenceId());
                    yield new InjurySyncSummary(syncedCount, invalidPayloadCount,
                            missingFixtureCount + 1, missingTeamCount, missingPlayerCount,
                            fixtureIds, teamIds, playerIds);
                }
                case TEAM_NOT_FOUND -> {
                    teamIds.add(result.missingReferenceId());
                    yield new InjurySyncSummary(syncedCount, invalidPayloadCount,
                            missingFixtureCount, missingTeamCount + 1, missingPlayerCount,
                            fixtureIds, teamIds, playerIds);
                }
                case PLAYER_NOT_FOUND -> {
                    playerIds.add(result.missingReferenceId());
                    yield new InjurySyncSummary(syncedCount, invalidPayloadCount,
                            missingFixtureCount, missingTeamCount, missingPlayerCount + 1,
                            fixtureIds, teamIds, playerIds);
                }
            };
        }

        private InjurySyncSummary plus(InjurySyncSummary other) {
            Set<Long> fixtureIds = new LinkedHashSet<>(missingFixtureIdSet);
            fixtureIds.addAll(other.missingFixtureIdSet);
            Set<Long> teamIds = new LinkedHashSet<>(missingTeamIdSet);
            teamIds.addAll(other.missingTeamIdSet);
            Set<Long> playerIds = new LinkedHashSet<>(missingPlayerIdSet);
            playerIds.addAll(other.missingPlayerIdSet);
            return new InjurySyncSummary(
                    syncedCount + other.syncedCount,
                    invalidPayloadCount + other.invalidPayloadCount,
                    missingFixtureCount + other.missingFixtureCount,
                    missingTeamCount + other.missingTeamCount,
                    missingPlayerCount + other.missingPlayerCount,
                    fixtureIds,
                    teamIds,
                    playerIds
            );
        }

        private int skippedCount() {
            return invalidPayloadCount + missingFixtureCount + missingTeamCount + missingPlayerCount;
        }

        private boolean hasFailures() {
            return skippedCount() > 0;
        }

        private List<Long> missingFixtureIds() {
            return List.copyOf(missingFixtureIdSet);
        }

        private List<Long> missingTeamIds() {
            return List.copyOf(missingTeamIdSet);
        }

        private List<Long> missingPlayerIds() {
            return List.copyOf(missingPlayerIdSet);
        }

        private String failureMessage() {
            return "Some injuries were skipped. invalidPayloadCount=" + invalidPayloadCount
                    + "; missingFixtureCount=" + missingFixtureCount
                    + "; missingFixtureIds=" + missingFixtureIds()
                    + "; missingTeamCount=" + missingTeamCount
                    + "; missingTeamIds=" + missingTeamIds()
                    + "; missingPlayerCount=" + missingPlayerCount
                    + "; missingPlayerIds=" + missingPlayerIds();
        }
    }
}

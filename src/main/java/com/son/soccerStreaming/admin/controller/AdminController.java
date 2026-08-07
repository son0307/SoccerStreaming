package com.son.soccerStreaming.admin.controller;

import com.son.soccerStreaming.admin.dto.AdminDto;
import com.son.soccerStreaming.admin.dto.AdminMediaDto;
import com.son.soccerStreaming.admin.entity.AdminMediaTargetType;
import com.son.soccerStreaming.auth.security.AuthUserDetails;
import com.son.soccerStreaming.admin.service.AdminService;
import com.son.soccerStreaming.admin.service.AdminMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminMediaService adminMediaService;

    @PostMapping("/media/uploads/presign")
    public ResponseEntity<AdminMediaDto.PresignResponse> presignMediaUpload(
            @RequestBody AdminMediaDto.PresignRequest request
    ) {
        return ResponseEntity.ok(adminMediaService.presign(request));
    }

    @PostMapping("/media/uploads/complete")
    public ResponseEntity<AdminMediaDto.MediaResponse> completeMediaUpload(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestBody AdminMediaDto.CompleteRequest request
    ) {
        return ResponseEntity.ok(adminMediaService.complete(userDetails.getId(), request));
    }

    @DeleteMapping("/media/{targetType}/{targetId}")
    public ResponseEntity<AdminMediaDto.MediaResponse> restoreMedia(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable AdminMediaTargetType targetType,
            @PathVariable Long targetId
    ) {
        return ResponseEntity.ok(adminMediaService.restore(userDetails.getId(), targetType, targetId));
    }

    @GetMapping("/teams")
    public List<AdminDto.TeamAdminResponse> searchTeams(@RequestParam(defaultValue = "") String keyword) {
        return adminService.searchTeams(keyword);
    }

    @GetMapping("/teams/{teamId}")
    public ResponseEntity<AdminDto.TeamAdminResponse> getTeam(@PathVariable Long teamId) {
        return ResponseEntity.ok(adminService.getTeamAdminDetail(teamId));
    }

    @PutMapping("/teams/{teamId}")
    public ResponseEntity<AdminDto.TeamAdminResponse> updateTeam(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId,
            @RequestBody AdminDto.TeamUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateTeam(userDetails.getId(), teamId, request));
    }

    @DeleteMapping("/teams/{teamId}/overrides")
    public ResponseEntity<AdminDto.TeamAdminResponse> clearTeamOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearTeamOverrides(userDetails.getId(), teamId, version));
    }

    @DeleteMapping("/teams/{teamId}/overrides/{fieldName}")
    public ResponseEntity<AdminDto.TeamAdminResponse> clearTeamOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long teamId,
            @PathVariable String fieldName,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearTeamOverride(userDetails.getId(), teamId, fieldName, version));
    }

    @GetMapping("/players")
    public List<AdminDto.PlayerAdminResponse> searchPlayers(@RequestParam(defaultValue = "") String keyword) {
        return adminService.searchPlayers(keyword);
    }

    @GetMapping("/players/{playerId}")
    public ResponseEntity<AdminDto.PlayerAdminResponse> getPlayer(@PathVariable Long playerId) {
        return ResponseEntity.ok(adminService.getPlayerAdminDetail(playerId));
    }

    @GetMapping("/fixtures")
    public List<AdminDto.FixtureAdminSummaryResponse> searchFixtures(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Integer season
    ) {
        return adminService.searchFixtures(keyword, season);
    }

    @GetMapping("/fixture-teams")
    public List<AdminDto.FixtureTeamOptionResponse> getFixtureTeams(@RequestParam Integer season) {
        return adminService.getFixtureTeams(season);
    }

    @GetMapping("/fixtures/{fixtureId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> getFixture(
            @PathVariable Long fixtureId
    ) {
        return ResponseEntity.ok(adminService.getFixtureAdminDetail(fixtureId));
    }

    @PutMapping("/fixtures/{fixtureId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixture(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @RequestBody AdminDto.FixtureUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixture(userDetails.getId(), fixtureId, request));
    }

    @DeleteMapping("/fixtures/{fixtureId}/overrides")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixtureOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixtureOverrides(userDetails.getId(), fixtureId, version));
    }

    @DeleteMapping("/fixtures/{fixtureId}/overrides/{fieldName}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixtureOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable String fieldName,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixtureOverride(userDetails.getId(), fixtureId, fieldName, version));
    }

    @PutMapping("/fixtures/{fixtureId}/events/{eventSequence}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixtureEvent(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Integer eventSequence,
            @RequestBody AdminDto.FixtureEventUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixtureEvent(userDetails.getId(), fixtureId, eventSequence, request));
    }

    @PostMapping("/fixtures/{fixtureId}/events")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> createFixtureEvent(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @RequestBody AdminDto.FixtureEventUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.createFixtureEvent(userDetails.getId(), fixtureId, request));
    }

    @DeleteMapping("/fixtures/{fixtureId}/events/{eventSequence}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> deleteFixtureEvent(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Integer eventSequence,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.deleteFixtureEvent(
                userDetails.getId(), fixtureId, eventSequence, version));
    }

    @DeleteMapping("/fixtures/{fixtureId}/events/overrides")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixtureEventOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixtureEventOverrides(userDetails.getId(), fixtureId, version));
    }

    @DeleteMapping("/fixtures/{fixtureId}/events/overrides/{fieldName}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixtureEventOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable String fieldName,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixtureEventOverride(
                userDetails.getId(), fixtureId, fieldName, version));
    }

    @PutMapping("/fixtures/{fixtureId}/lineups/{teamId}/{playerId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixtureLineup(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            @RequestBody AdminDto.FixtureLineupUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixtureLineup(userDetails.getId(), fixtureId, teamId, playerId, request));
    }

    @DeleteMapping("/fixtures/{fixtureId}/lineups/{teamId}/{playerId}/overrides")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixtureLineupOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixtureLineupOverrides(
                userDetails.getId(), fixtureId, teamId, playerId, version));
    }

    @DeleteMapping("/fixtures/{fixtureId}/lineups/{teamId}/{playerId}/overrides/{fieldName}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixtureLineupOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long teamId,
            @PathVariable Long playerId,
            @PathVariable String fieldName,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixtureLineupOverride(
                userDetails.getId(), fixtureId, teamId, playerId, fieldName, version));
    }

    @PutMapping("/fixtures/{fixtureId}/team-stats/{teamId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixtureTeamStat(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long teamId,
            @RequestBody AdminDto.FixtureTeamStatUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixtureTeamStat(userDetails.getId(), fixtureId, teamId, request));
    }

    @DeleteMapping("/fixtures/{fixtureId}/team-stats/{teamId}/overrides")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixtureTeamStatOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long teamId,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixtureTeamStatOverrides(
                userDetails.getId(), fixtureId, teamId, version));
    }

    @DeleteMapping("/fixtures/{fixtureId}/team-stats/{teamId}/overrides/{fieldName}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixtureTeamStatOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long teamId,
            @PathVariable String fieldName,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixtureTeamStatOverride(
                userDetails.getId(), fixtureId, teamId, fieldName, version));
    }

    @PutMapping("/fixtures/{fixtureId}/player-stats/{playerId}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> updateFixturePlayerStat(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long playerId,
            @RequestBody AdminDto.FixturePlayerStatUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updateFixturePlayerStat(userDetails.getId(), fixtureId, playerId, request));
    }

    @DeleteMapping("/fixtures/{fixtureId}/player-stats/{playerId}/overrides")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixturePlayerStatOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long playerId,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixturePlayerStatOverrides(
                userDetails.getId(), fixtureId, playerId, version));
    }

    @DeleteMapping("/fixtures/{fixtureId}/player-stats/{playerId}/overrides/{fieldName}")
    public ResponseEntity<AdminDto.FixtureAdminDetailResponse> clearFixturePlayerStatOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId,
            @PathVariable Long playerId,
            @PathVariable String fieldName,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearFixturePlayerStatOverride(
                userDetails.getId(), fixtureId, playerId, fieldName, version));
    }

    @PutMapping("/players/{playerId}")
    public ResponseEntity<AdminDto.PlayerAdminResponse> updatePlayer(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long playerId,
            @RequestBody AdminDto.PlayerUpdateRequest request
    ) {
        return ResponseEntity.ok(adminService.updatePlayer(userDetails.getId(), playerId, request));
    }

    @DeleteMapping("/players/{playerId}/overrides")
    public ResponseEntity<AdminDto.PlayerAdminResponse> clearPlayerOverrides(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long playerId,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearPlayerOverrides(userDetails.getId(), playerId, version));
    }

    @DeleteMapping("/players/{playerId}/overrides/{fieldName}")
    public ResponseEntity<AdminDto.PlayerAdminResponse> clearPlayerOverride(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long playerId,
            @PathVariable String fieldName,
            @RequestParam Long version
    ) {
        return ResponseEntity.ok(adminService.clearPlayerOverride(userDetails.getId(), playerId, fieldName, version));
    }

    @PostMapping("/sync/teams")
    public ResponseEntity<AdminDto.SyncResponse> syncTeams(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncTeams(userDetails.getId(), league, season));
    }

    @PostMapping("/sync/seasons")
    public ResponseEntity<AdminDto.SyncResponse> syncLeagueSeasons(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league
    ) {
        return ResponseEntity.ok(adminService.syncLeagueSeasons(userDetails.getId(), league));
    }

    @PostMapping("/sync/standings")
    public ResponseEntity<AdminDto.SyncResponse> syncStandings(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncStandings(userDetails.getId(), league, season));
    }

    @PostMapping("/sync/fixtures")
    public ResponseEntity<AdminDto.SyncResponse> syncFixtures(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncFixtures(userDetails.getId(), league, season));
    }

    @PostMapping("/sync/fixture-details")
    public ResponseEntity<AdminDto.SyncResponse> syncSeasonFixtureDetails(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncSeasonFixtureDetails(userDetails.getId(), season));
    }

    @PostMapping("/sync/fixture-details/{fixtureId}")
    public ResponseEntity<AdminDto.SyncResponse> syncFixtureDetail(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long fixtureId
    ) {
        return ResponseEntity.ok(adminService.syncFixtureDetail(userDetails.getId(), fixtureId));
    }

    @PostMapping("/sync/players")
    public ResponseEntity<AdminDto.SyncResponse> syncPlayers(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season,
            @RequestParam(defaultValue = "7000") Long delayMs
    ) {
        return ResponseEntity.ok(adminService.syncPlayers(userDetails.getId(), league, season, delayMs));
    }

    @PostMapping("/sync/injuries")
    public ResponseEntity<AdminDto.SyncResponse> syncInjuries(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @RequestParam(defaultValue = "39") Integer league,
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.syncInjuries(userDetails.getId(), league, season));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<AdminDto.AuditLogListResponse> getAuditLogs(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size
    ) {
        return ResponseEntity.ok(adminService.getAuditLogs(page, size));
    }

    @GetMapping("/sync/statuses")
    public ResponseEntity<AdminDto.SyncStatusResponse> getSyncStatuses(
            @RequestParam(defaultValue = "2025") Integer season
    ) {
        return ResponseEntity.ok(adminService.getSyncStatuses(season));
    }

    @GetMapping("/sync/jobs")
    public ResponseEntity<AdminDto.SyncJobListResponse> getSyncJobs(
            @RequestParam(defaultValue = "10") Integer limit
    ) {
        return ResponseEntity.ok(adminService.getSyncJobs(limit));
    }

    @PostMapping("/sync/jobs/{jobId}/cancel")
    public ResponseEntity<AdminDto.SyncCancelResponse> cancelSyncJob(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @PathVariable Long jobId
    ) {
        return ResponseEntity.ok(adminService.cancelSyncJob(userDetails.getId(), jobId));
    }

}

package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballTeamDto;
import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.media.service.ImageCacheService;
import com.son.soccerStreaming.team.entity.Team;
import com.son.soccerStreaming.team.entity.Venue;
import com.son.soccerStreaming.team.repository.TeamRepository;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballTeamSyncService {

    private final ApiFootballClient apiFootballClient;
    private final TeamRepository teamRepository;
    private final AdminOverrideService adminOverrideService;
    private final ApiFootballSyncStatusService apiFootballSyncStatusService;
    private final ImageCacheService imageCacheService;
    private final OptimisticLockRetryExecutor optimisticLockRetryExecutor;
    private static final List<String> OVERRIDE_FIELDS = List.of(
            "name", "code", "country", "founded", "logoUrl",
            "venueId", "venueName", "venueAddress", "venueCity", "capacity", "surface", "venueImageUrl"
    );

    public int syncTeams(Integer league, Integer season) {
        apiFootballSyncStatusService.recordAttempt("teams", "Teams", season);
        List<ApiFootballTeamDto.TeamResponse> responses = apiFootballClient.getTeams(league, season);
        int syncedCount = optimisticLockRetryExecutor.execute(
                "teams:league=%s;season=%s".formatted(league, season),
                () -> persistTeams(responses)
        );

        log.info("API-Football team sync completed. league={}, season={}, count={}", league, season, syncedCount);
        apiFootballSyncStatusService.recordSuccess("teams", "Teams", season);
        return syncedCount;
    }

    private int persistTeams(List<ApiFootballTeamDto.TeamResponse> responses) {
        int syncedCount = 0;

        for (ApiFootballTeamDto.TeamResponse response : responses) {
            ApiFootballTeamDto.TeamInfo teamInfo = response.getTeam();
            if (teamInfo == null || teamInfo.getId() == null) {
                continue;
            }

            Team team = teamRepository.findByTeamId(teamInfo.getId())
                    .orElseGet(() -> Team.builder()
                            .teamId(teamInfo.getId())
                            .name(teamInfo.getName())
                            .code(teamInfo.getCode())
                            .country(teamInfo.getCountry())
                            .founded(teamInfo.getFounded())
                            .logoUrl(teamInfo.getLogo())
                            .build());

            Set<String> overrides = adminOverrideService.overriddenFields(
                    AdminOverrideTargetType.TEAM,
                    teamInfo.getId(),
                    OVERRIDE_FIELDS
            );

            team.updateTeam(
                    adminOverrideService.apiValueUnlessOverridden(overrides, "name", team.getName(), teamInfo.getName()),
                    adminOverrideService.apiValueUnlessOverridden(overrides, "code", team.getCode(), teamInfo.getCode()),
                    adminOverrideService.apiValueUnlessOverridden(overrides, "country", team.getCountry(), teamInfo.getCountry()),
                    adminOverrideService.apiValueUnlessOverridden(overrides, "founded", team.getFounded(), teamInfo.getFounded()),
                    adminOverrideService.apiValueUnlessOverridden(overrides, "logoUrl", team.getLogoUrl(), teamInfo.getLogo())
            );
            upsertVenue(team, response.getVenue(), overrides);

            Team savedTeam = teamRepository.save(team);
            imageCacheService.requestTeamLogoCache(savedTeam);
            imageCacheService.requestVenueImageCache(savedTeam.getVenue());
            syncedCount++;
        }
        return syncedCount;
    }

    private void upsertVenue(Team team, ApiFootballTeamDto.VenueInfo venueInfo, Set<String> overrides) {
        if (venueInfo == null || venueInfo.getId() == null) {
            return;
        }

        Venue venue = team.getVenue();
        Long nextVenueId = adminOverrideService.apiValueUnlessOverridden(
                overrides,
                "venueId",
                venue != null ? venue.getVenueId() : venueInfo.getId(),
                venueInfo.getId()
        );
        if (venue == null || !nextVenueId.equals(venue.getVenueId())) {
            venue = Venue.builder()
                    .venueId(nextVenueId)
                    .build();
        }

        venue.updateVenue(
                adminOverrideService.apiValueUnlessOverridden(overrides, "venueName", venue.getVenueName(), venueInfo.getName()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "venueAddress", venue.getVenueAddress(), venueInfo.getAddress()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "venueCity", venue.getVenueCity(), venueInfo.getCity()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "capacity", venue.getCapacity(), venueInfo.getCapacity()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "surface", venue.getSurface(), venueInfo.getSurface()),
                adminOverrideService.apiValueUnlessOverridden(overrides, "venueImageUrl", venue.getVenueImageUrl(), venueInfo.getImage())
        );
        team.updateVenue(venue);
    }
}

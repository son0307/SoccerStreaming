package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import lombok.Getter;

import java.util.List;

@Getter
public class ApiFootballInjuryReferenceSyncException extends ExternalApiException {

    private final int invalidPayloadCount;
    private final int missingFixtureCount;
    private final int missingTeamCount;
    private final int missingPlayerCount;
    private final List<Long> missingFixtureIds;
    private final List<Long> missingTeamIds;
    private final List<Long> missingPlayerIds;

    public ApiFootballInjuryReferenceSyncException(
            int invalidPayloadCount,
            int missingFixtureCount,
            int missingTeamCount,
            int missingPlayerCount,
            List<Long> missingFixtureIds,
            List<Long> missingTeamIds,
            List<Long> missingPlayerIds
    ) {
        super(
                ExternalApiProvider.API_FOOTBALL,
                "syncInjuries",
                invalidPayloadCount > 0
                        ? ExternalApiErrorCategory.INVALID_RESPONSE
                        : ExternalApiErrorCategory.NOT_FOUND,
                null,
                false,
                null,
                "API-Football injury sync skipped invalid or unresolved records. "
                        + "invalidPayloads=" + invalidPayloadCount
                        + "; missingFixtures=" + missingFixtureCount
                        + "; missingFixtureIds=" + missingFixtureIds
                        + "; missingTeams=" + missingTeamCount
                        + "; missingTeamIds=" + missingTeamIds
                        + "; missingPlayers=" + missingPlayerCount
                        + "; missingPlayerIds=" + missingPlayerIds,
                null
        );
        this.invalidPayloadCount = invalidPayloadCount;
        this.missingFixtureCount = missingFixtureCount;
        this.missingTeamCount = missingTeamCount;
        this.missingPlayerCount = missingPlayerCount;
        this.missingFixtureIds = List.copyOf(missingFixtureIds);
        this.missingTeamIds = List.copyOf(missingTeamIds);
        this.missingPlayerIds = List.copyOf(missingPlayerIds);
    }
}

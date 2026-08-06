package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.global.externalapi.ExternalApiErrorCategory;
import com.son.soccerStreaming.global.externalapi.ExternalApiException;
import com.son.soccerStreaming.global.externalapi.ExternalApiProvider;
import lombok.Getter;

@Getter
public class ApiFootballInjuryReferenceSyncException extends ExternalApiException {

    private final int missingFixtureCount;
    private final int missingTeamCount;
    private final int missingPlayerCount;

    public ApiFootballInjuryReferenceSyncException(
            int missingFixtureCount,
            int missingTeamCount,
            int missingPlayerCount
    ) {
        super(
                ExternalApiProvider.API_FOOTBALL,
                "syncInjuries",
                ExternalApiErrorCategory.NOT_FOUND,
                null,
                false,
                null,
                "API-Football injury sync skipped records because referenced data was missing. "
                        + "missingFixtures=" + missingFixtureCount
                        + "; missingTeams=" + missingTeamCount
                        + "; missingPlayers=" + missingPlayerCount,
                null
        );
        this.missingFixtureCount = missingFixtureCount;
        this.missingTeamCount = missingTeamCount;
        this.missingPlayerCount = missingPlayerCount;
    }
}

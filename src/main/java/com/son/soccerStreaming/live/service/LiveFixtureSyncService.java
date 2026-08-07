package com.son.soccerStreaming.live.service;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveFixtureSyncService {

    private final ApiFootballFixtureDetailSyncService apiFootballFixtureDetailSyncService;
    private final LiveFixtureBroadcastService liveFixtureBroadcastService;

    public void syncFixture(Long fixtureId) {
        ApiFootballFixtureDetailSyncService.FixtureDetailSyncResult result =
                apiFootballFixtureDetailSyncService.syncFixtureDetail(fixtureId, true);
        liveFixtureBroadcastService.broadcastFixture(fixtureId, result.latestEvent());
    }
}

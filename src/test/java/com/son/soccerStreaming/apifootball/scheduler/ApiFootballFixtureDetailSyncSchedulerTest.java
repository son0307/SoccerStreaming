package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballFixtureDetailSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.live.service.LiveFixtureBroadcastService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ApiFootballFixtureDetailSyncSchedulerTest {

    @Test
    void syncsAndBroadcastsEveryLiveFixtureWithoutDependingOnSubscribers() {
        ApiFootballFixtureDetailSyncService detailSyncService = mock(ApiFootballFixtureDetailSyncService.class);
        LiveFixtureBroadcastService broadcastService = mock(LiveFixtureBroadcastService.class);
        FixtureRepository fixtureRepository = mock(FixtureRepository.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        ApiFootballFixtureDetailSyncScheduler scheduler = new ApiFootballFixtureDetailSyncScheduler(
                detailSyncService,
                broadcastService,
                fixtureRepository,
                retryScheduler,
                new ApiFootballSyncExecutionGuard()
        );
        Fixture fixture = Fixture.builder().fixtureId(100L).fixtureStatus("LIVE").build();
        var result = new ApiFootballFixtureDetailSyncService.FixtureDetailSyncResult(100L, null, 0, 0, 0);
        when(fixtureRepository.findAllByFixtureStatus("LIVE")).thenReturn(List.of(fixture));
        when(detailSyncService.syncFixtureDetailsWithResults(List.of(fixture), true)).thenReturn(List.of(result));

        scheduler.syncLiveFixtureDetails();

        verify(detailSyncService).syncFixtureDetailsWithResults(List.of(fixture), true);
        verify(broadcastService).broadcastFixture(100L, null);
    }

    @Test
    void skipsDetailSyncWhenThereAreNoLiveFixtures() {
        ApiFootballFixtureDetailSyncService detailSyncService = mock(ApiFootballFixtureDetailSyncService.class);
        LiveFixtureBroadcastService broadcastService = mock(LiveFixtureBroadcastService.class);
        FixtureRepository fixtureRepository = mock(FixtureRepository.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        ApiFootballFixtureDetailSyncScheduler scheduler = new ApiFootballFixtureDetailSyncScheduler(
                detailSyncService,
                broadcastService,
                fixtureRepository,
                retryScheduler,
                new ApiFootballSyncExecutionGuard()
        );
        when(fixtureRepository.findAllByFixtureStatus("LIVE")).thenReturn(List.of());

        scheduler.syncLiveFixtureDetails();

        verifyNoInteractions(detailSyncService, broadcastService, retryScheduler);
    }

    @Test
    void skipsDetailSyncWhenTheSameJobIsAlreadyReserved() {
        ApiFootballFixtureDetailSyncService detailSyncService = mock(ApiFootballFixtureDetailSyncService.class);
        LiveFixtureBroadcastService broadcastService = mock(LiveFixtureBroadcastService.class);
        FixtureRepository fixtureRepository = mock(FixtureRepository.class);
        ApiFootballSyncFailureRetryScheduler retryScheduler = mock(ApiFootballSyncFailureRetryScheduler.class);
        ApiFootballSyncExecutionGuard guard = new ApiFootballSyncExecutionGuard();
        guard.acquire(ApiFootballSyncExecutionGuard.key("fixture-details-live", "live"));
        ApiFootballFixtureDetailSyncScheduler scheduler = new ApiFootballFixtureDetailSyncScheduler(
                detailSyncService,
                broadcastService,
                fixtureRepository,
                retryScheduler,
                guard
        );

        scheduler.syncLiveFixtureDetails();

        verifyNoInteractions(detailSyncService, broadcastService, fixtureRepository, retryScheduler);
    }
}

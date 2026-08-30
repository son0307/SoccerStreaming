package com.son.soccerStreaming.apifootball.scheduler;

import com.son.soccerStreaming.apifootball.service.ApiFootballStandingLocalUpdateService;
import com.son.soccerStreaming.apifootball.service.ApiFootballStandingSyncService;
import com.son.soccerStreaming.apifootball.service.ApiFootballSyncExecutionGuard;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiFootballStandingSyncSchedulerTest {

    @Mock private ApiFootballStandingSyncService standingSyncService;
    @Mock private ApiFootballStandingLocalUpdateService localUpdateService;
    @Mock private FixtureRepository fixtureRepository;
    @Mock private ApiFootballSyncFailureRetryScheduler failureRetryScheduler;
    @Mock private ApiFootballSyncExecutionGuard executionGuard;

    private ApiFootballStandingSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ApiFootballStandingSyncScheduler(
                standingSyncService,
                localUpdateService,
                fixtureRepository,
                failureRetryScheduler,
                executionGuard
        );
        ReflectionTestUtils.setField(scheduler, "league", 39);
        ReflectionTestUtils.setField(scheduler, "season", 2025);
    }

    @Test
    void hourlySyncRunsWhenFinishedImpactRemainsWithoutLiveFixture() {
        when(fixtureRepository.existsByFixtureStatus("LIVE")).thenReturn(false);
        when(localUpdateService.hasFinishedImpacts(2025)).thenReturn(true);
        when(executionGuard.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });

        scheduler.syncStandingsHourlyWhenLive();

        verify(standingSyncService).syncStandings(39, 2025);
    }

    @Test
    void hourlySyncRunsWhenLiveFixtureExists() {
        when(fixtureRepository.existsByFixtureStatus("LIVE")).thenReturn(true);
        when(localUpdateService.hasFinishedImpacts(2025)).thenReturn(false);
        when(executionGuard.executeIfAvailable(anyString(), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });

        scheduler.syncStandingsHourlyWhenLive();

        verify(standingSyncService).syncStandings(39, 2025);
    }

    @Test
    void hourlySyncSkipsWhenThereIsNoLiveFixtureOrFinishedImpact() {
        when(fixtureRepository.existsByFixtureStatus("LIVE")).thenReturn(false);
        when(localUpdateService.hasFinishedImpacts(2025)).thenReturn(false);

        scheduler.syncStandingsHourlyWhenLive();

        verify(executionGuard, never()).executeIfAvailable(anyString(), any(Runnable.class));
        verify(standingSyncService, never()).syncStandings(39, 2025);
    }
}

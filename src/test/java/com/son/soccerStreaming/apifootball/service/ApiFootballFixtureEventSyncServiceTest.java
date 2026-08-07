package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureEventRepository;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiFootballFixtureEventSyncServiceTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private FixtureRepository fixtureRepository;
    @Mock private FixtureEventRepository fixtureEventRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ApiFootballPlayerSyncService playerSyncService;
    @Mock private AdminOverrideService adminOverrideService;

    @Test
    void locksFixtureBeforeCheckingOverrideAndSkipsWholeEventSync() {
        ApiFootballFixtureEventSyncService service = new ApiFootballFixtureEventSyncService(
                apiFootballClient,
                fixtureRepository,
                fixtureEventRepository,
                teamRepository,
                playerSyncService,
                adminOverrideService
        );
        Fixture fixture = Fixture.builder().fixtureId(1000L).build();
        when(fixtureRepository.findByFixtureIdForEventUpdate(1000L)).thenReturn(Optional.of(fixture));
        when(adminOverrideService.isOverriddenForEventSync(
                AdminOverrideTargetType.FIXTURE_EVENT, 1000L, "events"))
                .thenReturn(true);

        var result = service.syncEvents(fixture, List.of());

        assertThat(result).isNull();
        InOrder order = inOrder(fixtureRepository, adminOverrideService);
        order.verify(fixtureRepository).findByFixtureIdForEventUpdate(1000L);
        order.verify(adminOverrideService).isOverriddenForEventSync(
                AdminOverrideTargetType.FIXTURE_EVENT, 1000L, "events");
        verifyNoInteractions(fixtureEventRepository, teamRepository, playerSyncService);
    }
}

package com.son.soccerStreaming.apifootball.service;

import com.son.soccerStreaming.admin.service.AdminOverrideService;
import com.son.soccerStreaming.apifootball.client.ApiFootballClient;
import com.son.soccerStreaming.apifootball.dto.ApiFootballLiveDto;
import com.son.soccerStreaming.fixture.entity.Fixture;
import com.son.soccerStreaming.fixture.repository.FixtureRepository;
import com.son.soccerStreaming.team.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class ApiFootballFixtureSyncServiceTest {

    @Mock private ApiFootballClient apiFootballClient;
    @Mock private FixtureRepository fixtureRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private ApiFootballStandingLocalUpdateService apiFootballStandingLocalUpdateService;
    @Mock private ApiFootballSyncStatusService apiFootballSyncStatusService;
    @Mock private AdminOverrideService adminOverrideService;
    @Mock private OptimisticLockRetryExecutor optimisticLockRetryExecutor;

    @InjectMocks
    private ApiFootballFixtureSyncService service;

    @Test
    void fixtureSyncKeepsAdminOverriddenMetadataFields() {
        LocalDateTime adminDate = LocalDateTime.of(2026, 8, 7, 10, 0);
        Fixture fixture = Fixture.builder()
                .fixtureId(1000L)
                .fixtureDate(adminDate)
                .referee("Admin referee")
                .venueId(1L)
                .venueName("Admin stadium")
                .venueCity("Old city")
                .build();
        ApiFootballLiveDto.FixtureInfo fixtureInfo = new ApiFootballLiveDto.FixtureInfo();
        ReflectionTestUtils.setField(fixtureInfo, "date", "2026-08-08T12:00:00Z");
        ReflectionTestUtils.setField(fixtureInfo, "referee", "API referee");
        ApiFootballLiveDto.Venue venue = new ApiFootballLiveDto.Venue();
        ReflectionTestUtils.setField(venue, "id", 2L);
        ReflectionTestUtils.setField(venue, "name", "API stadium");
        ReflectionTestUtils.setField(venue, "city", "New city");
        ReflectionTestUtils.setField(fixtureInfo, "venue", venue);
        ApiFootballLiveDto.FixtureResponse response = new ApiFootballLiveDto.FixtureResponse();
        ReflectionTestUtils.setField(response, "fixture", fixtureInfo);
        keepCurrentValueWhenOverridden();

        ReflectionTestUtils.invokeMethod(
                service,
                "updateFixture",
                fixture,
                response,
                Set.of("fixtureDate", "venueName")
        );

        assertThat(fixture.getFixtureDate()).isEqualTo(adminDate);
        assertThat(fixture.getVenueName()).isEqualTo("Admin stadium");
        assertThat(fixture.getReferee()).isEqualTo("API referee");
        assertThat(fixture.getVenueId()).isEqualTo(2L);
        assertThat(fixture.getVenueCity()).isEqualTo("New city");
    }

    private void keepCurrentValueWhenOverridden() {
        doAnswer(invocation -> {
            Set<?> overrides = invocation.getArgument(0);
            String fieldName = invocation.getArgument(1);
            return overrides.contains(fieldName) ? invocation.getArgument(2) : invocation.getArgument(3);
        }).when(adminOverrideService).apiValueUnlessOverridden(any(), anyString(), any(), any());
    }
}

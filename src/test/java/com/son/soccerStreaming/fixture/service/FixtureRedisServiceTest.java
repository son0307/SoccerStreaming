package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.live.dto.LiveFixtureSnapshotDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixtureRedisServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private ObjectMapper objectMapper;

    private FixtureRedisService service;

    @BeforeEach
    void setUp() {
        service = new FixtureRedisService(redisTemplate, objectMapper);
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void treatsLiveSnapshotRedisFailureAsCacheMiss() {
        when(valueOperations.get("fixture:100:live_snapshot"))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertThat(service.getLiveSnapshot(100L)).isEmpty();
        verifyNoInteractions(objectMapper);
    }

    @Test
    void treatsPlayerStatsRedisFailureAsCacheMiss() {
        when(valueOperations.get("fixture:100:player_stats"))
                .thenThrow(new DataAccessResourceFailureException("Redis unavailable"));

        assertThat(service.getPlayerStats(100L)).isEmpty();
        verifyNoInteractions(objectMapper);
    }

    @Test
    void continuesWhenLiveSnapshotCannotBeStored() throws Exception {
        LiveFixtureSnapshotDto snapshot = LiveFixtureSnapshotDto.builder().fixtureId(100L).build();
        when(objectMapper.writeValueAsString(snapshot)).thenReturn("{}");
        doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(valueOperations).set("fixture:100:live_snapshot", "{}", Duration.ofMinutes(10));

        assertThatCode(() -> service.saveLiveSnapshot(snapshot)).doesNotThrowAnyException();
    }

    @Test
    void attemptsToDeleteBothFixtureKeysWhenRedisIsUnavailable() {
        doThrow(new DataAccessResourceFailureException("Redis unavailable"))
                .when(redisTemplate).delete(anyString());

        assertThatCode(() -> service.evictFixtureCaches(100L)).doesNotThrowAnyException();

        verify(redisTemplate).delete("fixture:100:live_snapshot");
        verify(redisTemplate).delete("fixture:100:player_stats");
    }
}

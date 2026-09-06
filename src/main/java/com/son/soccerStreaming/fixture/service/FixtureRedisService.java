package com.son.soccerStreaming.fixture.service;

import com.son.soccerStreaming.live.dto.LiveFixtureSnapshotDto;
import com.son.soccerStreaming.fixture.dto.FixturePlayerStatResponseDto;
import com.son.soccerStreaming.fixture.dto.FixtureStatResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixtureRedisService {

    private static final Duration LIVE_CACHE_TTL = Duration.ofMinutes(10);
    private static final String LIVE_SNAPSHOT_KEY = "fixture:%d:live_snapshot";
    private static final String PLAYER_STATS_KEY = "fixture:%d:player_stats";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public void saveLiveSnapshot(LiveFixtureSnapshotDto snapshot) {
        if (snapshot.getFixtureId() == null) {
            log.warn("fixtureId가 없는 live snapshot은 Redis에 저장하지 않습니다.");
            return;
        }

        try {
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(liveSnapshotKey(snapshot.getFixtureId()), snapshotJson, LIVE_CACHE_TTL);
        } catch (JacksonException e) {
            log.error("Redis live snapshot 저장 중 JSON 변환 오류", e);
        } catch (DataAccessException e) {
            log.warn("Redis live snapshot 저장 실패; DB 동기화와 SSE 처리를 계속합니다. fixtureId={}",
                    snapshot.getFixtureId(), e);
        }
    }

    public Optional<LiveFixtureSnapshotDto> getLiveSnapshot(Long fixtureId) {
        String snapshotJson;
        try {
            snapshotJson = redisTemplate.opsForValue().get(liveSnapshotKey(fixtureId));
        } catch (DataAccessException e) {
            log.warn("Redis live snapshot 조회 실패; DB fallback을 사용합니다. fixtureId={}", fixtureId, e);
            return Optional.empty();
        }
        if (snapshotJson == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(snapshotJson, LiveFixtureSnapshotDto.class));
        } catch (JacksonException e) {
            log.error("Redis live snapshot 조회 중 JSON 변환 오류. fixtureId={}", fixtureId, e);
            return Optional.empty();
        }
    }

    public void savePlayerStats(FixturePlayerStatResponseDto playerStats) {
        if (playerStats.getFixtureId() == null) {
            log.warn("fixtureId가 없는 player stats는 Redis에 저장하지 않습니다.");
            return;
        }

        try {
            String playerStatsJson = objectMapper.writeValueAsString(playerStats);
            redisTemplate.opsForValue().set(playerStatsKey(playerStats.getFixtureId()), playerStatsJson, LIVE_CACHE_TTL);
        } catch (JacksonException e) {
            log.error("Redis player stats 저장 중 JSON 변환 오류", e);
        } catch (DataAccessException e) {
            log.warn("Redis player stats 저장 실패; DB 동기화와 SSE 처리를 계속합니다. fixtureId={}",
                    playerStats.getFixtureId(), e);
        }
    }

    public Optional<FixturePlayerStatResponseDto> getPlayerStats(Long fixtureId) {
        String playerStatsJson;
        try {
            playerStatsJson = redisTemplate.opsForValue().get(playerStatsKey(fixtureId));
        } catch (DataAccessException e) {
            log.warn("Redis player stats 조회 실패; DB fallback을 사용합니다. fixtureId={}", fixtureId, e);
            return Optional.empty();
        }
        if (playerStatsJson == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(playerStatsJson, FixturePlayerStatResponseDto.class));
        } catch (JacksonException e) {
            log.error("Redis player stats 조회 중 JSON 변환 오류. fixtureId={}", fixtureId, e);
            return Optional.empty();
        }
    }

    public FixtureStatResponseDto.TeamStatSummary getTeamStatSummary(Long fixtureId, Long teamId) {
        return getLiveSnapshot(fixtureId)
                .map(snapshot -> findTeamStat(snapshot, teamId))
                .orElseGet(() -> emptyTeamStat(teamId));
    }

    public void evictFixtureCaches(Long fixtureId) {
        deleteSafely(liveSnapshotKey(fixtureId), fixtureId);
        deleteSafely(playerStatsKey(fixtureId), fixtureId);
    }

    private FixtureStatResponseDto.TeamStatSummary findTeamStat(LiveFixtureSnapshotDto snapshot, Long teamId) {
        if (snapshot.getHomeTeamStat() != null && teamId.equals(snapshot.getHomeTeamStat().getTeamId())) {
            return snapshot.getHomeTeamStat();
        }
        if (snapshot.getAwayTeamStat() != null && teamId.equals(snapshot.getAwayTeamStat().getTeamId())) {
            return snapshot.getAwayTeamStat();
        }
        return emptyTeamStat(teamId);
    }

    private FixtureStatResponseDto.TeamStatSummary emptyTeamStat(Long teamId) {
        return FixtureStatResponseDto.TeamStatSummary.builder()
                .teamId(teamId)
                .build();
    }

    private String liveSnapshotKey(Long fixtureId) {
        return LIVE_SNAPSHOT_KEY.formatted(fixtureId);
    }

    private String playerStatsKey(Long fixtureId) {
        return PLAYER_STATS_KEY.formatted(fixtureId);
    }

    private void deleteSafely(String key, Long fixtureId) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException e) {
            log.warn("Redis fixture cache 삭제 실패; 원본 데이터 처리를 계속합니다. fixtureId={}, key={}",
                    fixtureId, key, e);
        }
    }
}

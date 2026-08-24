package com.son.soccerStreaming.live.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseService {

    private final Map<Long, Set<SseEmitter>> fixtureEmitters = new ConcurrentHashMap<>();

    @Value("${live.sse.timeout-ms:1800000}")
    private long emitterTimeoutMs = 1_800_000L;

    public SseEmitter subscribe(Long fixtureId) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);

        // 해당 fixtureId의 방이 있으면 합류, 없으면 새로 생성
        Set<SseEmitter> room = fixtureEmitters.computeIfAbsent(fixtureId, key -> ConcurrentHashMap.newKeySet());
        room.add(emitter);
        log.info("SSE fixture subscriber connected. fixtureId={}, fixtureSubscribers={}, totalSubscribers={}",
                fixtureId, room.size(), getTotalSubscriberCount());

        // 연결 종료, 타임 아웃 -> 목록에서 제거
        emitter.onCompletion(() -> removeEmitter(fixtureId, room, emitter));
        emitter.onTimeout(() -> removeEmitter(fixtureId, room, emitter));
        emitter.onError(error -> removeEmitter(fixtureId, room, emitter));

        try {
            // 첫 연결 시 더미 데이터 전송 (503 에러 방지)
            emitter.send(SseEmitter.event().name("CONNECT").data(fixtureId + ": Successfully connected!"));
        } catch (IOException e) {
            removeEmitter(fixtureId, room, emitter);
        }

        return emitter;
    }

    @Scheduled(fixedDelayString = "${live.sse.heartbeat-ms:25000}")
    public void sendHeartbeats() {
        fixtureEmitters.forEach((fixtureId, room) -> {
            for (SseEmitter emitter : room) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException e) {
                    log.debug("SSE heartbeat failed; removing subscriber. fixtureId={}", fixtureId);
                    removeEmitter(fixtureId, room, emitter);
                }
            }
        });
    }

    // Broadcast live fixture updates to connected clients.
    public void broadcastToFixture(Long fixtureId, String jsonMessage) {
        broadcastToFixture(fixtureId, "FIXTURE_EVENT", jsonMessage);
    }

    public void broadcastToFixture(Long fixtureId, String eventName, String jsonMessage) {
        Set<SseEmitter> room = fixtureEmitters.get(fixtureId);

        if (room == null || room.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : room) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(jsonMessage));
            } catch (IOException | IllegalStateException e) {
                // 전송 실패한 파이프는 죽은 클라이언트이므로 제거
                log.debug("SSE broadcast failed; removing subscriber. fixtureId={}, eventName={}", fixtureId, eventName);
                removeEmitter(fixtureId, room, emitter);
            }
        }
    }

    public int getTotalSubscriberCount() {
        return fixtureEmitters.values().stream().mapToInt(Set::size).sum();
    }

    private void removeEmitter(Long fixtureId, Set<SseEmitter> room, SseEmitter emitter) {
        boolean removed = room.remove(emitter);
        if (room.isEmpty()) {
            fixtureEmitters.remove(fixtureId, room);
        }
        if (removed) {
            log.info("SSE fixture subscriber disconnected. fixtureId={}, fixtureSubscribers={}, totalSubscribers={}",
                    fixtureId, room.size(), getTotalSubscriberCount());
        }
    }

}

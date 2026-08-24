package com.son.soccerStreaming.live.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SseServiceTest {

    @Test
    void subscribeStoresLongFixtureId() {
        SseService sseService = new SseService();

        sseService.subscribe(200L);

        assertThat(sseService.getTotalSubscriberCount()).isEqualTo(1);

        sseService.sendHeartbeats();

        assertThat(sseService.getTotalSubscriberCount()).isEqualTo(1);
    }
}

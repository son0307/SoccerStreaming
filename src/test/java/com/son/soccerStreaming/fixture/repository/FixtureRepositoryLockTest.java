package com.son.soccerStreaming.fixture.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureRepositoryLockTest {

    @Test
    void eventUpdateLookupUsesPessimisticWriteLock() throws NoSuchMethodException {
        Lock lock = FixtureRepository.class
                .getMethod("findByFixtureIdForEventUpdate", Long.class)
                .getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}

package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.repository.AdminFieldOverrideRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import static org.assertj.core.api.Assertions.assertThat;

class AdminFieldOverrideRepositoryLockTest {

    @Test
    void eventSyncOverrideLookupUsesCurrentLockingRead() throws NoSuchMethodException {
        Lock lock = AdminFieldOverrideRepository.class
                .getMethod(
                        "findForEventSync",
                        AdminOverrideTargetType.class,
                        Long.class,
                        String.class
                )
                .getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_READ);
    }
}

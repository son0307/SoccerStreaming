package com.son.soccerStreaming.admin.service;

import com.son.soccerStreaming.admin.entity.AdminOverrideTargetType;
import com.son.soccerStreaming.admin.repository.AdminFieldOverrideRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

import static org.assertj.core.api.Assertions.assertThat;

class AdminFieldOverrideRepositoryLockTest {

    @Test
    void eventSyncOverrideLookupDoesNotUseAnOverrideRowLock() throws NoSuchMethodException {
        Lock lock = AdminFieldOverrideRepository.class
                .getMethod(
                        "existsByTargetTypeAndTargetIdAndFieldName",
                        AdminOverrideTargetType.class,
                        Long.class,
                        String.class
                )
                .getAnnotation(Lock.class);

        assertThat(lock).isNull();
    }
}

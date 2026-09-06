package com.son.soccerStreaming.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisCacheErrorHandlerTest {

    private final RedisCacheErrorHandler errorHandler = new RedisCacheErrorHandler();
    private final Cache cache = mock(Cache.class);

    @Test
    void ignoresRedisDataAccessFailureSoRequestCanContinueWithoutCache() {
        when(cache.getName()).thenReturn("testCache");
        RuntimeException exception = new DataAccessResourceFailureException("Redis unavailable");

        assertThatCode(() -> errorHandler.handleCacheGetError(exception, cache, "key"))
                .doesNotThrowAnyException();
        assertThatCode(() -> errorHandler.handleCachePutError(exception, cache, "key", "value"))
                .doesNotThrowAnyException();
        assertThatCode(() -> errorHandler.handleCacheEvictError(exception, cache, "key"))
                .doesNotThrowAnyException();
        assertThatCode(() -> errorHandler.handleCacheClearError(exception, cache))
                .doesNotThrowAnyException();
    }

    @Test
    void rethrowsNonRedisProgrammingFailure() {
        RuntimeException exception = new IllegalStateException("unexpected failure");

        assertThatThrownBy(() -> errorHandler.handleCacheGetError(exception, cache, "key"))
                .isSameAs(exception);
    }
}

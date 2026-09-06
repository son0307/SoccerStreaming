package com.son.soccerStreaming.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.dao.DataAccessException;

@Slf4j
public class RedisCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        handleRedisFailure(exception, "read", cache, key);
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        handleRedisFailure(exception, "write", cache, key);
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        handleRedisFailure(exception, "evict", cache, key);
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        handleRedisFailure(exception, "clear", cache, null);
    }

    private void handleRedisFailure(RuntimeException exception, String operation, Cache cache, Object key) {
        if (!isRedisDataAccessFailure(exception)) {
            throw exception;
        }

        log.warn("Redis cache {} failed; continuing without cache. cacheName={}, key={}",
                operation, cache.getName(), key, exception);
    }

    private boolean isRedisDataAccessFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DataAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

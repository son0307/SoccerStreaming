package com.son.soccerStreaming.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import java.time.Duration;

@EnableCaching
@Configuration
public class RedisCacheConfig implements CachingConfigurer {

    public static final String TEAM_PLAYER_RANKINGS_CACHE = "teamPlayerRankings";
    public static final String LEAGUE_PLAYER_RANKINGS_CACHE = "leaguePlayerRankings";
    public static final String LEAGUE_TEAM_RANKINGS_CACHE = "leagueTeamRankings";
    public static final String FAVORITE_TEAM_CARD_CACHE = "favoriteTeamCard";
    public static final String FAVORITE_PLAYER_CARD_CACHE = "favoritePlayerCard";
    public static final String RANKINGS_CACHE_MANAGER = "rankingsCacheManager";

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new RedisCacheErrorHandler();
    }

    @Bean
    @Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheWriter cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
        RedisCacheConfiguration defaultConfig = defaultCacheConfiguration();

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaultConfig)
                .build();
    }

    @Bean(name = RANKINGS_CACHE_MANAGER)
    public RedisCacheManager rankingsCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheWriter cacheWriter = RedisCacheWriter.lockingRedisCacheWriter(connectionFactory);
        RedisCacheConfiguration defaultConfig = defaultCacheConfiguration();

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(defaultConfig)
                .build();
    }

    private RedisCacheConfiguration defaultCacheConfiguration() {
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        GenericJacksonJsonRedisSerializer serializer =
                GenericJacksonJsonRedisSerializer.builder()
                        .enableDefaultTyping(ptv)
                        .build();

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
    }
}


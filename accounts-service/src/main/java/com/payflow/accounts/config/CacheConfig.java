package com.payflow.accounts.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payflow.accounts.domain.Account;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Redis-backed caching for account reads. Values are stored as JSON bound to the
 * {@link Account} type, with a 5-minute TTL. Spring Boot picks up this
 * {@link RedisCacheConfiguration} bean as the default cache config.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Name of the cache holding account reads, keyed by account id. */
    public static final String ACCOUNTS = "accounts";

    @Bean
    RedisCacheConfiguration redisCacheConfiguration() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<Account> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, Account.class);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}

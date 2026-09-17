package com.payflow.ledger.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payflow.ledger.web.LedgerPage;
import java.time.Duration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

/**
 * Redis-backed caching for ledger reads. Both the "all" list and the per-account
 * list are cached as a {@link LedgerPage} (JSON), with a 5-minute TTL. Every new
 * entry written by the Kafka consumer evicts the affected keys, so a read never
 * misses a freshly recorded transaction.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache for {@code GET /ledger} (the full list). */
    public static final String LEDGER_ALL = "ledgerAll";

    /** Cache for {@code GET /ledger/account/{id}}, keyed by account id. */
    public static final String LEDGER_BY_ACCOUNT = "ledgerByAccount";

    @Bean
    RedisCacheConfiguration redisCacheConfiguration() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<LedgerPage> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, LedgerPage.class);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }
}

package com.social.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import static org.mockito.Mockito.mock;

/**
 * Configurazione Redis per il profilo "test".
 * Attiva solo quando spring.profiles.active=test.
 * RedisConfig ha @Profile("!test") quindi non entra in conflitto.
 * Fornisce bean mock che non aprono socket reali verso Redis,
 * risolvendo "Unable to connect to Redis" negli integration test.
 */
@Configuration
@Profile("test")
public class TestRedisConfig {

    /**
     * Factory mock — non apre connessioni reali.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return mock(RedisConnectionFactory.class);
    }

    /**
     * Template mock — RedisPubSubService.publish() cattura già le eccezioni
     * e fa fallback STOMP locale, quindi il mock è sufficiente.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate() {
        return mock(StringRedisTemplate.class);
    }

    /**
     * Container mock — non apre connessioni reali né si avvia automaticamente.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        return mock(RedisMessageListenerContainer.class);
    }
}
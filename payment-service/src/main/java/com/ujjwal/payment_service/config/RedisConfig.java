package com.ujjwal.payment_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Supplies the RedisTemplate&lt;String, Object&gt; bean IdempotencyService
 * needs. This has to be defined explicitly: Spring Boot's own Redis
 * autoconfiguration only provides a RedisTemplate&lt;Object, Object&gt;
 * bean when none is user-defined — Spring resolves generics for autowiring,
 * so that bean's different type parameters mean it does NOT satisfy an
 * injection point asking for RedisTemplate&lt;String, Object&gt; (the same
 * class of generic-mismatch gap already hit with KafkaTemplate in
 * order-service).
 *
 * Unlike order-service's own RedisConfig, there's no
 * GenericJackson2JsonRedisSerializer/default-typing setup here:
 * IdempotencyService only ever stores and reads the literal string
 * "processed" as its value, never a DTO, so plain StringRedisSerializer for
 * both keys and values is sufficient for what this service actually needs
 * right now.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer serializer = new StringRedisSerializer();
        template.setKeySerializer(serializer);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(serializer);
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}

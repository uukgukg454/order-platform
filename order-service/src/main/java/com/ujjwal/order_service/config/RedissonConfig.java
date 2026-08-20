package com.ujjwal.order_service.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the RedissonClient bean by hand — a stopgap, not the intended
 * long-term design.
 *
 * Redisson's own Spring Boot starter auto-configuration
 * (RedissonAutoConfigurationV2, explicitly excluded via
 * spring.autoconfigure.exclude in application.yaml) is currently broken
 * against this project's Spring Boot 4.1.0: it references
 * org.springframework.boot.autoconfigure.data.redis.RedisProperties, a
 * class that no longer exists at that package path in this Boot version
 * (Boot 4 split autoconfigure into per-module packages — the same pattern
 * that already relocated Flyway's and Hibernate's autoconfiguration
 * elsewhere in this codebase). Left enabled, that auto-configuration
 * throws ClassNotFoundException the moment it runs, failing context
 * refresh for the entire application — not just anything Redisson-related.
 *
 * This is a known compatibility gap right after a major framework version
 * bump, not a fundamental incompatibility between Redisson and Spring — so
 * this manual bean is a workaround, built directly from the same
 * spring.data.redis.host/port properties the excluded auto-configuration
 * would have read itself. It should be revisited, and very likely removed
 * in favor of Redisson's own auto-configuration again, once Redisson ships
 * a release that actually targets Spring Boot 4.
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                          @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }
}

package com.ujjwal.order_service.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * RedisTemplate defaults every serializer (key, value, and their hash
     * counterparts) to JdkSerializationRedisSerializer, which is deliberately
     * NOT used here:
     *   - it writes Java's binary serialization format, so keys and values
     *     show up in redis-cli as unreadable byte soup instead of something
     *     you can eyeball while debugging;
     *   - it embeds the exact class's serialVersionUID, so a value written
     *     by one deploy can fail to deserialize after the next deploy if the
     *     class shape changed at all — JSON has no such coupling to the
     *     exact bytecode that wrote it.
     * StringRedisSerializer + GenericJackson2JsonRedisSerializer trade that
     * for plain, inspectable JSON at a small serialization-size cost.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Keys as plain UTF-8 strings — e.g. "order:<id>" instead of a
        // serialized byte blob — so GET/KEYS/SCAN in redis-cli stay legible.
        StringRedisSerializer keySerializer = new StringRedisSerializer();
        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);

        GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Backs the declarative @Cacheable / @CacheEvict annotations on
     * OrderService (getOrder / updateOrderStatus) with the "orders" cache.
     * This is a separate path from the RedisTemplate bean above — that one
     * stays in place untouched for future imperative Redis use (idempotency
     * keys, distributed locks), while this one is what Spring's caching
     * abstraction actually goes through.
     *
     * The bean is a TieredCacheManager, not the RedisCacheManager built by
     * buildRedisCacheManager below directly — every cache it hands out has
     * a local Caffeine layer in front of Redis (see TieredCache /
     * TieredCacheManager). OrderService's @Cacheable("orders") /
     * @CacheEvict("orders") don't change at all for this: they only name a
     * cache, not a manager implementation, so this swap is invisible to them.
     *
     * Spring's default Redis key format for @Cacheable is
     * "&lt;cacheName&gt;::&lt;key&gt;" (double colon) — e.g. "orders::&lt;uuid&gt;" —
     * which is different from the "order:" + id single-colon scheme the old
     * hand-rolled cache-aside code in getOrder used before that method was
     * switched to @Cacheable. The two will never collide, but don't expect
     * to find prior manually-cached entries under this key shape.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                      @Value("${app.cache.order-ttl-seconds}") long orderCacheTtlSeconds,
                                      @Value("${app.cache.order-local-ttl-seconds}") long orderLocalCacheTtlSeconds,
                                      @Value("${app.cache.order-local-max-size}") long orderLocalCacheMaxSize,
                                      MeterRegistry meterRegistry) {
        RedisCacheManager redisCacheManager = buildRedisCacheManager(connectionFactory, orderCacheTtlSeconds);
        return new TieredCacheManager(
                redisCacheManager,
                Duration.ofSeconds(orderLocalCacheTtlSeconds),
                orderLocalCacheMaxSize,
                meterRegistry
        );
    }

    // The L2 layer only: a plain Redis-backed RedisCacheManager, exactly as
    // this class was built when it was itself the @Bean. No longer exposed
    // to Spring directly — cacheManager() above wraps it in a
    // TieredCacheManager before that's what actually gets registered as
    // the CacheManager bean.
    private RedisCacheManager buildRedisCacheManager(RedisConnectionFactory connectionFactory, long orderCacheTtlSeconds) {
        GenericJackson2JsonRedisSerializer cacheValueSerializer = new GenericJackson2JsonRedisSerializer(buildRedisObjectMapper());

        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(orderCacheTtlSeconds))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(cacheValueSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfiguration)
                .build();
    }

    /**
     * Shared by both beans above so the RedisTemplate path (manual
     * get/set — idempotency keys, distributed locks) and the @Cacheable /
     * @CacheEvict path (via cacheManager) serialize to and from Redis
     * identically, rather than two independently-maintained copies of this
     * setup silently drifting apart.
     *
     * JavaTimeModule + disabling WRITE_DATES_AS_TIMESTAMPS: our DTOs
     * (OrderResponse.createdAt/updatedAt) are java.time.Instant, which
     * Jackson can't serialize out of the box without JavaTimeModule, and
     * without disabling WRITE_DATES_AS_TIMESTAMPS it would still write
     * Instant as a numeric epoch array — valid JSON, but exactly the kind
     * of value that's unreadable at a glance in redis-cli.
     *
     * activateDefaultTyping(..., JsonTypeInfo.As.PROPERTY): RedisTemplate/
     * RedisCacheManager both erase the specific type at compile time — by
     * the time a value comes back out of Redis, all Jackson knows is
     * "deserialize this JSON into an Object". Without type info embedded in
     * the payload, it can't tell whether a blob was originally an
     * OrderResponse, some future ProductResponse, or anything else, so it
     * falls back to a generic LinkedHashMap — which then blows up with a
     * ClassCastException at the call site's cast to the real DTO type, not
     * here. The three-argument overload matters: the two-argument
     * activateDefaultTyping(validator, typing) implicitly defaults to
     * JsonTypeInfo.As.WRAPPER_ARRAY, which wraps every value as
     * ["fully.qualified.ClassName", {...fields...}] — functional as long as
     * every reader and writer agree, but opaque to eyeball in redis-cli and
     * fragile the moment two differently-configured serializers touch the
     * same data (exactly the drift this shared method exists to prevent).
     * As.PROPERTY instead embeds a "@class" field directly inside the JSON
     * object, so a cached value still reads as plain JSON with one extra
     * field, and this class of mismatch is structurally harder to hit again.
     *
     * DefaultTyping.EVERYTHING, not NON_FINAL: our DTOs (OrderResponse,
     * OrderItemResponse) are Java records, which are implicitly final.
     * NON_FINAL skips writing type info for final classes on the assumption
     * that finality alone makes the target type unambiguous — but that
     * assumption only holds when the deserialization call site still has
     * some static type context to fall back on. Here it doesn't:
     * RedisTemplate<String, Object> and Spring's cache abstraction erase
     * everything to Object, so there is no fallback context at all. Under
     * NON_FINAL a cached record would be written with no @class field,
     * making it indistinguishable from a LinkedHashMap on the way back out
     * — the exact failure this whole method exists to prevent, just
     * reintroduced specifically for records. EVERYTHING removes the
     * finality exemption entirely, which is what a fully type-erased
     * container like this actually requires.
     * LaissezFaireSubTypeValidator.instance (rather than a safelist of
     * allowed types) is fine here because this is trusted, first-party data
     * this service itself wrote moments to minutes ago — not untrusted
     * external input, which is the actual scenario that class of validator
     * would otherwise be unsafe for.
     *
     * Trade-off: embedding @class couples every cached entry to the DTO's
     * fully-qualified class name. Rename OrderResponse or move it to a
     * different package, and any entry cached under the old name becomes
     * undeserializable on the next read. In practice this self-heals —
     * every entry carries a TTL (see app.cache.order-ttl-seconds), so a
     * stale-shaped entry ages out on its own rather than lingering — but
     * it's worth knowing before renaming a cached DTO.
     */
    private ObjectMapper buildRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}

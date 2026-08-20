package com.ujjwal.order_service.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CacheManager that hands out TieredCache instances rather than plain Redis
 * caches: every cache name Spring asks for gets its own local Caffeine
 * layer (L1) in front of the corresponding cache from the delegate
 * RedisCacheManager (L2).
 */
public class TieredCacheManager implements CacheManager {

    private final RedisCacheManager redisCacheManager;
    private final Duration localTtl;
    private final long localMaxSize;
    private final MeterRegistry meterRegistry;

    private final ConcurrentHashMap<String, Cache> caches = new ConcurrentHashMap<>();

    public TieredCacheManager(RedisCacheManager redisCacheManager, Duration localTtl, long localMaxSize,
                               MeterRegistry meterRegistry) {
        this.redisCacheManager = redisCacheManager;
        this.localTtl = localTtl;
        this.localMaxSize = localMaxSize;
        this.meterRegistry = meterRegistry;
    }

    // computeIfAbsent (build once, reuse forever) isn't just a
    // micro-optimization here — it's load-bearing. Spring calls
    // getCache(name) on every single annotated method invocation (every
    // @Cacheable / @CacheEvict call), not only once at startup. If a fresh
    // local Caffeine cache were built on every call, the L1 this method
    // hands back would always be brand new and empty: nothing would ever
    // survive from one call to the next, every read would fall straight
    // through to L2, and TieredCache's whole reason for existing — serving
    // repeat reads out of JVM memory — would silently do nothing while
    // still paying the cost of constructing a Caffeine cache on every call.
    @Override
    public Cache getCache(String name) {
        return caches.computeIfAbsent(name, cacheName -> {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> localCache = Caffeine.newBuilder()
                    .maximumSize(localMaxSize)
                    .expireAfterWrite(localTtl)
                    .build();
            Cache redisCache = redisCacheManager.getCache(cacheName);
            return new TieredCache(localCache, redisCache, meterRegistry);
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return redisCacheManager.getCacheNames();
    }
}

package com.ujjwal.order_service.config;

import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.concurrent.Callable;

/**
 * A two-layer org.springframework.cache.Cache: an in-JVM Caffeine cache
 * (L1) in front of a delegate cache (L2 — a Redis-backed cache obtained
 * from RedisCacheManager in practice).
 *
 * The point is hot-key mitigation. Without an L1, every single request for
 * a popular key — the one order everyone keeps polling, the one product
 * page that's trending — round-trips to Redis over the network, every
 * time, no matter how many times that exact value was already read a
 * moment ago. With an L1, only the first read after a local miss pays that
 * network hop; every read after that for the same key on this instance is
 * served straight out of JVM memory. Redis remains the source of truth
 * (and the mechanism cross-instance eviction and TTL expiry both still run
 * through) — this class only ever adds a faster, smaller, shorter-lived
 * copy in front of it.
 */
public class TieredCache implements Cache {

    private final com.github.benmanes.caffeine.cache.Cache<Object, Object> localCache;
    private final Cache delegate;

    public TieredCache(com.github.benmanes.caffeine.cache.Cache<Object, Object> localCache, Cache delegate) {
        this.localCache = localCache;
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            // Local hit: return directly, no call to the L2 delegate at all
            // — this is the entire point of having an L1 in front of Redis.
            return new SimpleValueWrapper(localValue);
        }

        ValueWrapper remoteValue = delegate.get(key);
        if (remoteValue != null) {
            // Local miss, remote hit: backfill L1 so the next read for this
            // key on this instance is a pure local hit instead of another
            // round trip to Redis.
            localCache.put(key, remoteValue.get());
        }
        return remoteValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper wrapper = get(key);
        return wrapper != null ? (T) wrapper.get() : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        Object localValue = localCache.getIfPresent(key);
        if (localValue != null) {
            return (T) localValue;
        }

        // Compute-if-absent semantics (and the locking that guarantees
        // valueLoader runs once per key under concurrent access) come from
        // the L2 delegate rather than being reimplemented here.
        T value = delegate.get(key, valueLoader);
        if (value != null) {
            localCache.put(key, value);
        }
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        localCache.put(key, value);
        delegate.put(key, value);
    }

    @Override
    public void evict(Object key) {
        // Cache-coherency trade-off: this only clears THIS instance's local
        // copy. In a horizontally-scaled deployment, every other instance
        // still has its own L1 entry for this key and keeps serving it —
        // stale — until that entry's own short local TTL expires on its
        // own. There is no cross-instance broadcast here (no pub/sub
        // invalidation message, nothing telling sibling instances to drop
        // their copy too), so evict() is only ever locally-consistent, not
        // cluster-consistent. Keeping the L1 TTL short is what bounds how
        // long that staleness window can last.
        localCache.invalidate(key);
        delegate.evict(key);
    }

    @Override
    public void clear() {
        localCache.invalidateAll();
        delegate.clear();
    }
}

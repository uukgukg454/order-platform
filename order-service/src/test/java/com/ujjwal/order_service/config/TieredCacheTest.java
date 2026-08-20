package com.ujjwal.order_service.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: the local/delegate caches are Mockito doubles, so these
 * only prove what TieredCache itself does with the two caches it wraps —
 * not that Caffeine or Redis behave correctly, which is out of scope for a
 * test at this level.
 *
 * MeterRegistry is a real SimpleMeterRegistry, not a mock: Counter.builder
 * (...).register(meterRegistry) needs to return an actual working Counter,
 * since TieredCache calls .increment() on it during get() — a mocked
 * MeterRegistry.register(...) would just return null, NPEing the instant
 * any test exercised a hit or miss.
 */
@ExtendWith(MockitoExtension.class)
class TieredCacheTest {

    @Mock
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> localCache;

    @Mock
    private Cache delegate;

    private SimpleMeterRegistry meterRegistry;
    private TieredCache tieredCache;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // TieredCache reads delegate.getName() in its constructor to tag
        // the counters — needs a real value, not Mockito's default null,
        // or Counter.builder(...).tag("cache", null) blows up.
        when(delegate.getName()).thenReturn("orders");
        tieredCache = new TieredCache(localCache, delegate, meterRegistry);
        // The constructor call above just touched delegate (getName(), for
        // the metric tag) — that's real, expected interaction, but it's
        // setup noise, not part of any test's actual get()/put()/evict()
        // call. Cleared here so verifyNoInteractions(delegate) below
        // means what it says: nothing beyond construction touched delegate
        // during the test body itself.
        clearInvocations(localCache, delegate);
    }

    @Test
    void get_returnsLocalValueDirectly_whenLocalCacheHasIt_withNoDelegateInteraction() {
        when(localCache.getIfPresent("key1")).thenReturn("cached-value");

        Cache.ValueWrapper result = tieredCache.get("key1");

        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("cached-value");
        // The entire point of the L1 layer: a local hit must never even
        // touch L2, not just "happen not to use its result".
        verifyNoInteractions(delegate);
        assertThat(meterRegistry.get("app.cache.hits").tag("cache", "orders").tag("tier", "L1").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void get_onLocalMiss_returnsDelegateValue_andBackfillsLocalCache() {
        when(localCache.getIfPresent("key1")).thenReturn(null);
        when(delegate.get("key1")).thenReturn(new SimpleValueWrapper("remote-value"));

        Cache.ValueWrapper result = tieredCache.get("key1");

        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("remote-value");
        // Proves the L2 hit gets written into L1 — the next read for this
        // key should be a pure local hit instead of another round trip.
        verify(localCache).put("key1", "remote-value");
        assertThat(meterRegistry.get("app.cache.hits").tag("cache", "orders").tag("tier", "L2").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void get_onFullMiss_incrementsMissCounter() {
        when(localCache.getIfPresent("key1")).thenReturn(null);
        when(delegate.get("key1")).thenReturn(null);

        Cache.ValueWrapper result = tieredCache.get("key1");

        assertThat(result).isNull();
        assertThat(meterRegistry.get("app.cache.misses").tag("cache", "orders").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void put_writesToBothLocalCacheAndDelegate() {
        tieredCache.put("key1", "value1");

        verify(localCache).put("key1", "value1");
        verify(delegate).put("key1", "value1");
    }

    @Test
    void evict_evictsFromBothLocalCacheAndDelegate() {
        tieredCache.evict("key1");

        verify(localCache).invalidate("key1");
        verify(delegate).evict("key1");
    }
}

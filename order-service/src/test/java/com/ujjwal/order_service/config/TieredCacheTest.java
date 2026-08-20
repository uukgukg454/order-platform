package com.ujjwal.order_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: both layers are Mockito doubles, so these only prove
 * what TieredCache itself does with the two caches it wraps — not that
 * Caffeine or Redis behave correctly, which is out of scope for a test at
 * this level.
 */
@ExtendWith(MockitoExtension.class)
class TieredCacheTest {

    @Mock
    private com.github.benmanes.caffeine.cache.Cache<Object, Object> localCache;

    @Mock
    private Cache delegate;

    @InjectMocks
    private TieredCache tieredCache;

    @Test
    void get_returnsLocalValueDirectly_whenLocalCacheHasIt_withNoDelegateInteraction() {
        when(localCache.getIfPresent("key1")).thenReturn("cached-value");

        Cache.ValueWrapper result = tieredCache.get("key1");

        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo("cached-value");
        // The entire point of the L1 layer: a local hit must never even
        // touch L2, not just "happen not to use its result".
        verifyNoInteractions(delegate);
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

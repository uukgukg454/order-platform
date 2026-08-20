package com.ujjwal.order_service.idempotency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests: RedisTemplate/ValueOperations are Mockito doubles, so
 * these only prove what IdempotencyService itself does with whatever Redis
 * hands back — not that a real Redis SET ... NX behaves atomically (that's
 * Redis's own guarantee, not something a mock can meaningfully verify).
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Test
    void tryMarkProcessed_returnsTrue_whenSetIfAbsentReturnsTrue() {
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, 600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        boolean result = idempotencyService.tryMarkProcessed("order-created-abc123");

        assertThat(result).isTrue();
    }

    @Test
    void tryMarkProcessed_returnsFalse_whenSetIfAbsentReturnsFalse() {
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, 600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        boolean result = idempotencyService.tryMarkProcessed("order-created-abc123");

        assertThat(result).isFalse();
    }

    @Test
    void tryMarkProcessed_prefixesKeyWithIdempotencyNamespace() {
        IdempotencyService idempotencyService = new IdempotencyService(redisTemplate, 600L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        idempotencyService.tryMarkProcessed("order-created-abc123");

        // Confirms the Redis key actually written is "idempotency:" + the
        // raw key handed in, not the raw key alone — keeps this service's
        // keys in their own namespace, distinct from the "orders::<uuid>"
        // cache keys and any future feature's own key scheme in the same
        // Redis instance.
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(keyCaptor.capture(), any(), any(Duration.class));
        assertThat(keyCaptor.getValue()).isEqualTo("idempotency:order-created-abc123");
    }
}

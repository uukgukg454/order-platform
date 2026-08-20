package com.ujjwal.order_service.idempotency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Guards against processing the same message or request twice. Lives in its
 * own package, not service or config, because it isn't tied to orders
 * specifically — Phase 3's Kafka consumers will reuse this directly, where
 * the idempotency key might be a record's partition+offset or a
 * business-level event id, not an order id.
 *
 * tryMarkProcessed uses Redis's atomic SET ... NX (setIfAbsent) rather than
 * a separate exists() check followed by a set() call. The two-step version
 * has a race condition: two concurrent callers can both call exists(),
 * both see "not present" (neither has written anything yet), and both
 * proceed to process the same message — which defeats the entire purpose
 * of an idempotency guard. setIfAbsent performs the "is it already there /
 * mark it mine" check-and-write as one atomic operation on the Redis
 * server itself, so there is no window between check and write for a
 * second caller to slip through — only one of any number of concurrent
 * callers can ever get true back for the same key.
 */
@Service
public class IdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final long ttlSeconds;

    // 600s (10 minutes) for now — short enough to verify locally without
    // waiting around. A real production system would likely use something
    // closer to 24 hours, sized to comfortably exceed the worst-case
    // redelivery window of whatever message broker is feeding this (e.g. a
    // Kafka consumer group rebalance, or a DLQ replay hours later) — the
    // TTL only does its job if it outlives every way a duplicate could
    // still legitimately arrive.
    public IdempotencyService(RedisTemplate<String, Object> redisTemplate,
                               @Value("${app.idempotency.ttl-seconds:600}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * @return true if this is the first time idempotencyKey has been seen
     *         within the TTL window — safe to process. false means it's a
     *         duplicate; the caller should skip processing.
     */
    public boolean tryMarkProcessed(String idempotencyKey) {
        String key = "idempotency:" + idempotencyKey;
        Boolean firstTime = redisTemplate.opsForValue().setIfAbsent(key, "processed", Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(firstTime);
    }
}

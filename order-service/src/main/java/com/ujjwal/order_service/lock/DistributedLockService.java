package com.ujjwal.order_service.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Runs an action while holding a Redis-backed distributed lock keyed by
 * lockKey, so concurrent callers — including ones on different instances
 * in a horizontally-scaled deployment — can't run that action at the same
 * time for the same key.
 */
@Service
public class DistributedLockService {

    private final RedissonClient redissonClient;

    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    // leaseTime is passed explicitly to tryLock rather than relying on
    // Redisson's watchdog (the mechanism that keeps auto-extending a lock's
    // expiry for as long as the holding thread/process is alive). For a
    // short, bounded operation like a stock decrement, an explicit lease is
    // simpler to reason about: the lock is guaranteed to release itself
    // after leaseTime no matter what happens to this process — crash, GC
    // pause, lost network connection — with no heartbeat needed to bound
    // the damage. The cost is that leaseTime has to be sized comfortably
    // longer than the action could realistically take, or the lock could
    // expire — and get handed to another caller — while this one is still
    // legitimately running.
    public <T> T executeWithLock(String lockKey, Duration waitTime, Duration leaseTime, Supplier<T> action) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired;
        try {
            acquired = lock.tryLock(waitTime.toMillis(), leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("Interrupted while waiting for lock: " + lockKey, e);
        }

        if (!acquired) {
            throw new LockAcquisitionException("Could not acquire lock within " + waitTime + ": " + lockKey);
        }

        try {
            return action.get();
        } finally {
            // isHeldByCurrentThread guards against unlocking a lease that
            // already expired (leaseTime elapsed while action.get() was
            // still running) and was since reacquired by a different
            // caller. Calling unlock() unconditionally here could release a
            // lock this thread no longer actually owns, letting a third
            // caller in while the second caller still believes it holds
            // exclusive access.
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}

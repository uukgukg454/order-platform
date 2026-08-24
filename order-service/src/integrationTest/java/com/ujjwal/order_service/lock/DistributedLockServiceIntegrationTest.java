package com.ujjwal.order_service.lock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs DistributedLockService against a real Redis with real concurrent
 * threads, to prove the lock actually serializes access — not just that
 * executeWithLock returns without throwing, which a single-threaded test
 * couldn't tell apart from a lock that does nothing at all.
 *
 * The RedissonClient here is built directly with Config/Redisson.create,
 * pointed at the Testcontainers-mapped host/port, rather than the
 * Spring-Boot-autoconfigured bean (spring.data.redis.*). That keeps this
 * test to plain JUnit5 + Testcontainers with no ApplicationContext (and no
 * Postgres container along for the ride just to satisfy the rest of the
 * app's autoconfiguration) — it only needs to prove the locking logic
 * itself is correct, not that Spring wires a RedissonClient bean.
 */
@Testcontainers
class DistributedLockServiceIntegrationTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static RedissonClient redissonClient;

    @BeforeAll
    static void startRedissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
        redissonClient = Redisson.create(config);
    }

    @AfterAll
    static void stopRedissonClient() {
        redissonClient.shutdown();
    }

    @Test
    void executeWithLock_serializesConcurrentReadModifyWrite_noLostUpdates() throws InterruptedException {
        DistributedLockService lockService = new DistributedLockService(redissonClient);
        AtomicInteger counter = new AtomicInteger(0);
        String lockKey = "test-lock:" + System.nanoTime();
        int taskCount = 50;

        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        CountDownLatch allTasksDone = new CountDownLatch(taskCount);
        for (int i = 0; i < taskCount; i++) {
            executor.submit(() -> {
                try {
                    lockService.executeWithLock(lockKey, Duration.ofSeconds(10), Duration.ofSeconds(10), () -> {
                        // Classic read-modify-write: without the lock
                        // actually serializing these 50 tasks, two threads
                        // could both read the same "current" value here and
                        // both write back current + 1, silently losing an
                        // update — the sleep widens that window so an
                        // unprotected version would reliably lose updates
                        // instead of getting lucky under real concurrency.
                        int current = counter.get();
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        counter.set(current + 1);
                        return null;
                    });
                } finally {
                    allTasksDone.countDown();
                }
            });
        }

        boolean completedInTime = allTasksDone.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completedInTime).isTrue();
        assertThat(counter.get()).isEqualTo(taskCount);
    }
}

package com.ujjwal.order_service.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises IdempotencyService against a real Redis, not a mock — proving
 * the actual atomic SET ... NX round trip works end to end, which
 * IdempotencyServiceTest's mocked ValueOperations can't: a mock can be told
 * to return true then false, but that doesn't prove Redis itself enforces
 * "only the first caller for a key gets true".
 */
@Testcontainers
@SpringBootTest
class IdempotencyServiceIntegrationTest {

    // Postgres is present here even though this test never touches an
    // order — it's not optional. This is a full @SpringBootTest, so the
    // whole ApplicationContext boots, and DataSource/Flyway/JPA autoconfig
    // in this app aren't gated behind any profile or conditional that would
    // let the context start without a reachable database. Same reason
    // OrderServiceApplicationTests needs its own Postgres container despite
    // only checking that the context loads.
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // Same Redis container + wiring as OrderControllerIntegrationTest: no
    // @ServiceConnection support for Redis in this Boot version's
    // spring-boot-testcontainers, so the connection is wired by hand via
    // @DynamicPropertySource instead.
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private IdempotencyService idempotencyService;

    @Test
    void tryMarkProcessed_returnsTrueOnce_thenFalseForTheSameKey() {
        String idempotencyKey = "order-created-" + System.nanoTime();

        boolean firstAttempt = idempotencyService.tryMarkProcessed(idempotencyKey);
        boolean secondAttempt = idempotencyService.tryMarkProcessed(idempotencyKey);

        assertThat(firstAttempt).isTrue();
        assertThat(secondAttempt).isFalse();
    }
}

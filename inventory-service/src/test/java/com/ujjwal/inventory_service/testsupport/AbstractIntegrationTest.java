package com.ujjwal.inventory_service.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for every @SpringBootTest integration test in this module —
 * mirrors order-service's AbstractIntegrationTest of the same name. No test
 * currently extends this (this module has no tests yet), but it's in place
 * now so the first one — including the first @KafkaListener test for
 * OrderCreatedEventListener — gets full container isolation by default
 * instead of falling through to application.yml's dev defaults
 * (localhost:5433 Postgres, localhost:6379 Redis, localhost:9092 Kafka).
 *
 * Deliberately NOT using @Testcontainers/@Container — see order-service's
 * AbstractIntegrationTest for the full explanation: that combination stops
 * a shared container after the first test class's afterAll, breaking it
 * for every class that runs after. This uses Testcontainers' documented
 * singleton-container pattern instead: plain static fields, started once
 * via the static initializer below, never explicitly stopped.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    // Matches the "postgres:16" image the dev container for this service
    // (inventory-postgres on localhost:5433) actually runs.
    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // No @ServiceConnection support for Redis in this Boot version, wired
    // by hand below. Matches the "redis:7-alpine" image order-redis runs —
    // this module reuses that same local Redis for real (namespaced) use,
    // see application.yml, so the test container should match it too.
    protected static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // Same story as Redis: no @ServiceConnection support for Kafka either.
    //
    // Deliberately the OLD org.testcontainers.containers.KafkaContainer
    // (Confluent-image-based), not the new org.testcontainers.kafka one
    // Testcontainers 2.x introduced: the new class's default "apache/kafka"
    // native-KRaft image fails to start under this machine's Windows
    // Docker Desktop (npipe) setup with "advertised.listeners cannot use
    // the nonroutable meta-address 0.0.0.0" — see order-service's
    // AbstractIntegrationTest, where this was actually hit and diagnosed.
    // The deprecated class is long-established and known-reliable across
    // platforms for exactly this job, so it's the pragmatic choice here
    // despite the deprecation warning it now compiles with.
    protected static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    static {
        postgres.start();
        redis.start();
        kafka.start();
    }

    @DynamicPropertySource
    protected static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}

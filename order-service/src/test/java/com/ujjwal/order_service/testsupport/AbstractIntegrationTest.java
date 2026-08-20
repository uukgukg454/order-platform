package com.ujjwal.order_service.testsupport;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared base for every @SpringBootTest integration test in this module.
 *
 * Every container here is a throwaway Testcontainers instance, never the
 * real dev containers this app's application.yaml defaults would otherwise
 * reach (order-postgres on localhost:5432, order-redis on localhost:6379,
 * whatever's on localhost:9092 for Kafka) — the whole point is that this
 * test suite can run in CI, or on any machine with nothing but Docker, with
 * zero dependency on that dev environment being up or in any particular
 * state.
 *
 * Deliberately NOT using @Testcontainers/@Container here. That combination
 * ties a container's lifecycle to the ONE test class that declares it —
 * started in that class's beforeAll, stopped in its afterAll — which is
 * exactly right for a container used by a single test class, but breaks
 * the moment several different test classes share a container through a
 * common superclass like this one: the first class to finish stops the
 * container in its own afterAll, and every class that runs after that gets
 * a dead container (hit directly here — IdempotencyServiceIntegrationTest
 * failed with RedisConnectionException once OrderControllerIntegrationTest,
 * running earlier, had already stopped the shared Redis container it no
 * longer "knew" was still needed).
 *
 * This uses Testcontainers' documented singleton-container pattern
 * instead: plain static fields, started once via the static initializer
 * below the first time this class is loaded, and never explicitly
 * stopped — Testcontainers' own Ryuk reaper container tears them down when
 * the JVM exits, regardless of which test class happened to run last.
 * @ServiceConnection still works without @Container/@Testcontainers: it's
 * a separate Spring-side mechanism (a ContextCustomizerFactory reading
 * connection details off an already-running container), independent of
 * which code actually started that container.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    // @ServiceConnection wires this container's JDBC URL/username/password
    // into the Spring context as if they were spring.datasource.*
    // properties automatically. Matches the "postgres:16" image the dev
    // container (order-postgres) actually runs.
    @ServiceConnection
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // No @ServiceConnection here: unlike Postgres, spring-boot-testcontainers
    // in this Boot version ships no built-in connection-details factory for
    // Redis — so the connection is wired by hand below. Matches the
    // "redis:7-alpine" image the dev container (order-redis) actually runs.
    protected static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // Same story as Redis: no @ServiceConnection support for Kafka in this
    // Boot version either, wired by hand via @DynamicPropertySource below.
    //
    // Deliberately the OLD org.testcontainers.containers.KafkaContainer
    // (Confluent-image-based), not the new org.testcontainers.kafka one
    // Testcontainers 2.x introduced: the new class's default "apache/kafka"
    // native-KRaft image failed to start here with "advertised.listeners
    // cannot use the nonroutable meta-address 0.0.0.0" — its automatic
    // advertised-listener resolution breaking under this machine's Windows
    // Docker Desktop (npipe) setup specifically, not something fixable by
    // just picking a different image tag. The deprecated class is
    // long-established and known-reliable across platforms for exactly
    // this "expose a bootstrap-servers address the host JVM can reach"
    // job, so it's the pragmatic choice here despite the deprecation
    // warning it now compiles with.
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

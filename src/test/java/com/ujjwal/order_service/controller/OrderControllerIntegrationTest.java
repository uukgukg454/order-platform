package com.ujjwal.order_service.controller;

import com.ujjwal.order_service.dto.request.CreateOrderRequest;
import com.ujjwal.order_service.dto.request.OrderItemRequest;
import com.ujjwal.order_service.dto.request.UpdateOrderStatusRequest;
import com.ujjwal.order_service.dto.response.OrderResponse;
import com.ujjwal.order_service.entity.OrderStatus;
import com.ujjwal.order_service.exception.ErrorResponse;
import com.ujjwal.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * End-to-end test: real HTTP calls through the full stack (controller,
 * service, JPA/Hibernate, Flyway migrations) against a real PostgreSQL
 * instance.
 *
 * A throwaway Testcontainers Postgres is used instead of the dev database
 * (the one application.yaml points at on localhost:5432) so that:
 *   - tests never depend on that database being up, reachable, or in a
 *     particular state (no shared, mutable fixture to get out of sync);
 *   - tests can run in CI or on any machine with just Docker, with no
 *     manual "start Postgres first" step;
 *   - each test run starts from a clean schema (Flyway migrations applied
 *     fresh by this container) instead of accumulating leftover rows from
 *     previous runs or other developers.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// Spring Boot 4 split TestRestTemplate's autoconfiguration into its own
// module (spring-boot-resttestclient) that RANDOM_PORT no longer wires in
// implicitly — this opts the test into it explicitly.
@AutoConfigureTestRestTemplate
class OrderControllerIntegrationTest {

    // @ServiceConnection wires this container's JDBC URL/username/password
    // into the Spring context as if they were spring.datasource.* properties
    // — Spring Boot detects the container type (Postgres) and configures the
    // DataSource (and Flyway, which reuses the same connection info)
    // automatically. Without it, we'd have to wire each property by hand via
    // @DynamicPropertySource; @ServiceConnection replaces that boilerplate.
    @Container
    @ServiceConnection
    // Matches the "postgres:16" image the dev container (order-postgres)
    // actually runs, so tests validate against the same major version and
    // base image the app is really deployed/run against.
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    // getOrder is now @Cacheable (see OrderService), so a live Redis is
    // required for this context to start at all, same as Postgres above —
    // matches the "redis:7-alpine" image the dev container (order-redis)
    // actually runs.
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    // No @ServiceConnection here: unlike Postgres, spring-boot-testcontainers
    // in this Boot version ships no built-in connection-details factory for
    // Redis — there's no auto-detecting recognition of a Redis container the
    // way there is for Postgres, MySQL, Kafka, etc. So the connection has to
    // be wired by hand: register spring.data.redis.host/port to point at
    // wherever Testcontainers actually mapped the container's 6379 to on the
    // host, before the Spring context reads those properties to build its
    // RedisConnectionFactory.
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    // Spring Boot auto-configures this bean, pre-bound to the random port
    // the test server started on, so requests below use relative paths.
    @Autowired
    private TestRestTemplate restTemplate;

    // Wraps the REAL OrderRepository bean in a Mockito spy — calls still go
    // through to the actual Postgres container, but every invocation is
    // also recorded so tests below can assert on how many times (or
    // whether at all) the repository was actually touched, to prove
    // @Cacheable / @CacheEvict are doing what they claim rather than just
    // asserting on response bodies. MockitoSpyBean (spring-test, part of
    // Spring Framework itself) is this Spring Boot version's current bean
    // override mechanism for this — the older
    // org.springframework.boot.test.mock.mockito.SpyBean it replaced no
    // longer exists in this Boot/Framework version.
    @MockitoSpyBean
    private OrderRepository orderRepository;

    @Test
    void createOrder_returnsCreatedWithLocationAndServerComputedTotal() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(
                        new OrderItemRequest(UUID.randomUUID(), "Widget", new BigDecimal("19.99"), 2),
                        new OrderItemRequest(UUID.randomUUID(), "Gadget", new BigDecimal("5.00"), 3)
                )
        );

        ResponseEntity<OrderResponse> response = restTemplate.postForEntity("/orders", request, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).contains("/orders/");

        // 19.99 * 2 + 5.00 * 3 = 39.98 + 15.00 = 54.98 — computed by
        // OrderService from the item lines, not taken from the request
        // (CreateOrderRequest has no totalAmount field at all).
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo("54.98");
    }

    @Test
    void getOrder_afterCreation_returnsFullOrderWithItems() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(new OrderItemRequest(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 1))
        );
        ResponseEntity<OrderResponse> created = restTemplate.postForEntity("/orders", request, OrderResponse.class);
        UUID orderId = created.getBody().id();

        ResponseEntity<OrderResponse> response = restTemplate.getForEntity("/orders/{id}", OrderResponse.class, orderId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        OrderResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isEqualTo(orderId);
        assertThat(body.customerId()).isEqualTo(request.customerId());
        assertThat(body.items()).hasSize(1);
        assertThat(body.items().get(0).productName()).isEqualTo("Widget");
    }

    @Test
    void getOrder_withUnknownId_returns404WithStructuredError() {
        UUID unknownId = UUID.randomUUID();

        ResponseEntity<ErrorResponse> response = restTemplate.getForEntity("/orders/{id}", ErrorResponse.class, unknownId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.message()).contains(unknownId.toString());
        assertThat(body.path()).isEqualTo("/orders/" + unknownId);
    }

    @Test
    void createOrder_missingCustomerId_returns400WithFieldValidationError() {
        // Built directly rather than via the constructor's @NotNull enforcing
        // anything at construction time — the record itself doesn't validate;
        // @Valid on the controller parameter is what triggers the failure.
        CreateOrderRequest request = new CreateOrderRequest(
                null,
                "USD",
                List.of(new OrderItemRequest(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 1))
        );

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/orders", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.message()).contains("customerId");
    }

    @Test
    void getOrder_secondCallForSameId_isServedFromCache_repositoryInvokedOnce() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(new OrderItemRequest(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 1))
        );
        ResponseEntity<OrderResponse> created = restTemplate.postForEntity("/orders", request, OrderResponse.class);
        UUID orderId = created.getBody().id();

        ResponseEntity<OrderResponse> firstGet = restTemplate.getForEntity("/orders/{id}", OrderResponse.class, orderId);
        ResponseEntity<OrderResponse> secondGet = restTemplate.getForEntity("/orders/{id}", OrderResponse.class, orderId);

        assertThat(firstGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(secondGet.getBody()).isEqualTo(firstGet.getBody());

        // @Cacheable("orders") means only the first getOrder call should
        // ever reach the repository — the second is served straight from
        // TieredCache (Caffeine L1, or Redis L2 on an L1 miss) without
        // touching Postgres at all.
        verify(orderRepository, times(1)).findByIdWithItems(orderId);
    }

    @Test
    void getOrder_afterStatusUpdate_cacheEvicted_repositoryReflectsRealRefetch() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(new OrderItemRequest(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 1))
        );
        ResponseEntity<OrderResponse> created = restTemplate.postForEntity("/orders", request, OrderResponse.class);
        UUID orderId = created.getBody().id();

        // Populates the cache — first repository hit.
        restTemplate.getForEntity("/orders/{id}", OrderResponse.class, orderId);
        verify(orderRepository, times(1)).findByIdWithItems(orderId);

        // updateOrderStatus loads the entity via this same findByIdWithItems
        // query too (see OrderService — it needs the managed entity to
        // mutate, independently of caching), so this PATCH is itself a
        // second invocation, on top of whatever @CacheEvict does. That's
        // why the running total below goes to 2 here, not because eviction
        // caused a second fetch — eviction happens AFTER this method
        // returns and doesn't touch the repository at all.
        UpdateOrderStatusRequest statusUpdate = new UpdateOrderStatusRequest(OrderStatus.PAID);
        ResponseEntity<OrderResponse> patchResponse = restTemplate.exchange(
                "/orders/{id}/status", HttpMethod.PATCH, new HttpEntity<>(statusUpdate), OrderResponse.class, orderId);
        assertThat(patchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patchResponse.getBody().status()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, times(2)).findByIdWithItems(orderId);

        // The real proof of eviction: if the "orders" cache entry for this
        // id were still populated from the first GET, this call would be
        // served from cache and the invocation count below would stay at
        // 2. Instead @CacheEvict cleared it when updateOrderStatus
        // committed, so this GET has to reach the repository again — the
        // count advances to 3, and the returned status reflects the write.
        ResponseEntity<OrderResponse> afterUpdateGet = restTemplate.getForEntity("/orders/{id}", OrderResponse.class, orderId);
        assertThat(afterUpdateGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(afterUpdateGet.getBody().status()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository, times(3)).findByIdWithItems(orderId);
    }
}

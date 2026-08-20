package com.ujjwal.order_service.service;

import com.ujjwal.order_service.dto.request.CreateOrderRequest;
import com.ujjwal.order_service.dto.request.OrderItemRequest;
import com.ujjwal.order_service.dto.response.OrderResponse;
import com.ujjwal.order_service.entity.Order;
import com.ujjwal.order_service.entity.OrderItem;
import com.ujjwal.order_service.entity.OrderStatus;
import com.ujjwal.order_service.exception.OrderNotFoundException;
import com.ujjwal.order_service.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for OrderService's business logic (total computation,
 * status assignment, not-found translation) with OrderRepository mocked out.
 *
 * Unlike OrderControllerIntegrationTest, there is no @SpringBootTest here, no
 * ApplicationContext, no Testcontainers, no Postgres — OrderRepository is a
 * Mockito double that returns exactly what each test tells it to. That's
 * deliberate: these tests are only about what OrderService itself computes
 * and decides (the totalAmount math, the initial status, the not-found
 * translation), none of which requires Hibernate, Flyway, or a real
 * connection to prove — going through a database would only make the tests
 * slower and couple them to schema/container concerns they don't care about.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_computesTotalAmountBySummingUnitPriceTimesQuantityAcrossItems() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(
                        new OrderItemRequest(UUID.randomUUID(), "Widget", new BigDecimal("10.00"), 3),
                        new OrderItemRequest(UUID.randomUUID(), "Gadget", new BigDecimal("2.50"), 4)
                )
        );
        // save() would normally hand back the Hibernate-managed instance with
        // its generated id populated; echoing the same instance we were given
        // is enough here since these tests only check the values OrderService
        // computed before calling save(), not anything persistence assigns.
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request);

        // 10.00 * 3 + 2.50 * 4 = 30.00 + 10.00 = 40.00 — proves the sum comes
        // from OrderService's own reduction over unitPrice*quantity, not from
        // some client-supplied figure (CreateOrderRequest has no totalAmount
        // field for one to even supply).
        assertThat(response.totalAmount()).isEqualByComparingTo("40.00");
    }

    @Test
    void createOrder_setsStatusToCreated() {
        CreateOrderRequest request = new CreateOrderRequest(
                UUID.randomUUID(),
                "USD",
                List.of(new OrderItemRequest(UUID.randomUUID(), "Widget", new BigDecimal("5.00"), 1))
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void getOrder_returnsMappedResponse_whenRepositoryFindsTheOrder() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Order order = new Order(customerId, new BigDecimal("15.00"), "USD");
        // The real id would normally come from @GeneratedValue on insert;
        // since nothing here ever hits a database, it's set directly so the
        // response can be asserted against a known value.
        ReflectionTestUtils.setField(order, "id", orderId);
        order.addItem(new OrderItem(UUID.randomUUID(), "Widget", new BigDecimal("15.00"), 1));

        // findByIdWithItems is the JOIN FETCH query OrderService relies on to
        // avoid a LazyInitializationException later — mocking it directly
        // means this test never needs a real lazy-loading Order.items
        // collection to prove OrderService maps it correctly.
        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(orderId);

        assertThat(response.id()).isEqualTo(orderId);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).productName()).isEqualTo("Widget");
    }

    @Test
    void listOrdersByCustomer_mapsEachOrderToASummaryWithoutItemDetail() {
        UUID customerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        UUID firstId = UUID.randomUUID();
        Order first = new Order(customerId, new BigDecimal("40.00"), "USD");
        ReflectionTestUtils.setField(first, "id", firstId);
        first.setStatus(OrderStatus.PAID);
        // Item added on purpose: first has a real, non-empty items collection,
        // so the assertion below on response.items() being empty proves
        // toSummaryResponse deliberately never touches it — not that there
        // was simply nothing to map.
        first.addItem(new OrderItem(UUID.randomUUID(), "Widget", new BigDecimal("40.00"), 1));

        UUID secondId = UUID.randomUUID();
        Order second = new Order(customerId, new BigDecimal("15.50"), "EUR");
        ReflectionTestUtils.setField(second, "id", secondId);
        second.setStatus(OrderStatus.CANCELLED);

        Page<Order> page = new PageImpl<>(List.of(first, second), pageable, 2);
        // findByCustomerId is the plain (non-JOIN-FETCH) query — mocking it
        // directly means this test never needs a real lazy Order.items
        // collection either, same as the getOrder tests above.
        when(orderRepository.findByCustomerId(customerId, pageable)).thenReturn(page);

        Page<OrderResponse> result = orderService.listOrdersByCustomer(customerId, pageable);

        assertThat(result.getContent()).hasSize(2);

        OrderResponse firstResponse = result.getContent().get(0);
        assertThat(firstResponse.id()).isEqualTo(firstId);
        assertThat(firstResponse.status()).isEqualTo(OrderStatus.PAID);
        assertThat(firstResponse.totalAmount()).isEqualByComparingTo("40.00");
        assertThat(firstResponse.currency()).isEqualTo("USD");
        // The behavior under test: toSummaryResponse (used here) always
        // returns an empty items list, unlike toResponse (used by getOrder,
        // see above) which maps the order's actual OrderItem rows. This is
        // intentional — see OrderService.toSummaryResponse's own comment —
        // not a gap in the mapping.
        assertThat(firstResponse.items()).isEmpty();

        OrderResponse secondResponse = result.getContent().get(1);
        assertThat(secondResponse.id()).isEqualTo(secondId);
        assertThat(secondResponse.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(secondResponse.totalAmount()).isEqualByComparingTo("15.50");
        assertThat(secondResponse.currency()).isEqualTo("EUR");
        assertThat(secondResponse.items()).isEmpty();
    }

    @Test
    void getOrder_throwsOrderNotFoundException_whenRepositoryReturnsEmpty() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findByIdWithItems(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(unknownId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }
}

package com.ujjwal.order_service.service;

import com.ujjwal.order_service.dto.request.CreateOrderRequest;
import com.ujjwal.order_service.dto.response.OrderItemResponse;
import com.ujjwal.order_service.dto.response.OrderResponse;
import com.ujjwal.order_service.entity.Order;
import com.ujjwal.order_service.entity.OrderItem;
import com.ujjwal.order_service.entity.OrderStatus;
import com.ujjwal.order_service.event.OrderCreatedEvent;
import com.ujjwal.order_service.exception.OrderNotFoundException;
import com.ujjwal.order_service.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String ordersCreatedTopic;

    public OrderService(OrderRepository orderRepository,
                         KafkaTemplate<String, Object> kafkaTemplate,
                         @Value("${app.kafka.topics.orders-created}") String ordersCreatedTopic) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.ordersCreatedTopic = ordersCreatedTopic;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        // totalAmount is computed here from the item lines, never taken from
        // the request: a client-submitted total could be wrong by accident
        // (stale price on the client) or manipulated on purpose, and this is
        // the one place that can be trusted to recompute it from the same
        // unitPrice/quantity that get persisted.
        BigDecimal totalAmount = request.items().stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = new Order(request.customerId(), totalAmount, request.currency());

        // addItem (not items().add(...) or "new OrderItem(...).setOrder(order)")
        // because the association is bidirectional: OrderItem.order is the
        // owning side that Hibernate reads to populate the order_id FK, while
        // Order.items is what orphanRemoval watches. addItem sets both in one
        // call so neither side can drift out of sync with the other.
        request.items().forEach(itemRequest -> order.addItem(new OrderItem(
                itemRequest.productId(),
                itemRequest.productName(),
                itemRequest.unitPrice(),
                itemRequest.quantity()
        )));

        // cascade = ALL on Order.items means saving the Order also inserts
        // its items — no separate save on an OrderItem repository needed.
        Order saved = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                saved.getId(),
                saved.getCustomerId(),
                saved.getTotalAmount(),
                saved.getCurrency(),
                saved.getItems().stream()
                        .map(item -> new OrderCreatedEvent.Item(item.getProductId(), item.getQuantity()))
                        .toList()
        );
        // Keyed by orderId, not customerId: Kafka routes same-key messages
        // to the same partition, so keying by customerId would concentrate
        // every order from one high-volume customer onto a single
        // partition — a hot partition, and a ceiling on how much of that
        // customer's traffic can ever be processed in parallel. Nothing
        // downstream needs ordering ACROSS a customer's different orders
        // (inventory-service/payment-service handle each order
        // independently), only — if anything — ordering of events for the
        // SAME order, so orderId is both a finer-grained and entirely
        // sufficient partitioning key.
        //
        // This is a plain save-then-publish, not a transactional outbox:
        // if this process crashes after save() commits but before send()
        // reaches the broker, Postgres has the order and Kafka never gets
        // the event — a silent gap no downstream consumer would know to
        // look for. (The reverse gap exists too, since send() runs before
        // this @Transactional method's own commit at return: a fast
        // consumer could react to this event before the order row is even
        // durably visible.) Closing either gap for real means a
        // transactional outbox table written in the same DB transaction as
        // the order, plus a separate process that reads the outbox and
        // publishes to Kafka.
        kafkaTemplate.send(ordersCreatedTopic, saved.getId().toString(), event);

        return toResponse(saved);
    }

    // Replaces the manual cache GET-check / DB-fallback / SET block that
    // used to live directly in this method: Spring's caching abstraction
    // now checks the "orders" cache for key #id first and returns that on a
    // hit, only running the method body (and caching its return value) on a
    // miss — same read-through behavior, declared instead of hand-written.
    @Cacheable(value = "orders", key = "#id")
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrdersByCustomer(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerId(customerId, pageable)
                .map(this::toSummaryResponse);
    }

    // beforeInvocation defaults to false, so Spring evicts the "orders"
    // cache entry for this id only after this method — and the save()
    // inside it — completes successfully. Evicting up front (beforeInvocation
    // = true) would open a window where a concurrent getOrder(id) repopulates
    // the cache with the still-old status before this write actually
    // commits, leaving stale data cached even though the eviction already
    // "happened".
    @CacheEvict(value = "orders", key = "#id")
    @Transactional
    public OrderResponse updateOrderStatus(UUID id, OrderStatus newStatus) {
        Order order = orderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        order.setStatus(newStatus);
        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getItems().stream()
                        .map(item -> new OrderItemResponse(
                                item.getId(),
                                item.getProductId(),
                                item.getProductName(),
                                item.getUnitPrice(),
                                item.getQuantity()
                        ))
                        .toList()
        );
    }

    // Deliberately does not call order.getItems(): findByCustomerId does not
    // JOIN FETCH items, so touching that collection here would trigger one
    // extra lazy-load query per order on the page (N+1) purely to populate a
    // field a list view has no use for.
    private OrderResponse toSummaryResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                List.of()
        );
    }
}

package com.ujjwal.order_service.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Published to Kafka after an order is created.
 *
 * Deliberately a smaller shape than OrderResponse — only what downstream
 * consumers (inventory-service reserving stock, payment-service charging a
 * payment method) actually need to do their jobs. No status, no
 * created/updated timestamps, no per-item pricing or product name: those
 * are order-service's own API-response concerns, not things a consumer
 * reacting to "an order was created" needs to know.
 */
public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        List<Item> items
) {
    public record Item(UUID productId, int quantity) {
    }
}

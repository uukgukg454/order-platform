package com.ujjwal.payment_service.event;

import java.util.List;
import java.util.UUID;

/**
 * Published after a payment is declined, so inventory-service can
 * compensate — release the stock it already decremented for this order.
 * Carries items (same shape as OrderCreatedEvent.Item) rather than just the
 * orderId because inventory-service needs to know exactly what to release,
 * not just that something failed; it has no other way to look that up
 * without a schema module or a call back into order-service.
 */
public record PaymentFailedEvent(UUID orderId, List<Item> items, String reason) {
    public record Item(UUID productId, int quantity) {
    }
}

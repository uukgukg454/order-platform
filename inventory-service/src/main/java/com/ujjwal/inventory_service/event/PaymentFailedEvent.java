package com.ujjwal.inventory_service.event;

import java.util.List;
import java.util.UUID;

/**
 * Local copy of payment-service's PaymentFailedEvent shape — same
 * duplication rationale as OrderCreatedEvent's own comment: no shared
 * schema module in this project's architecture, so this only has to stay
 * shape-compatible with the producer's event, not identical by package or
 * class identity.
 */
public record PaymentFailedEvent(UUID orderId, List<Item> items, String reason) {
    public record Item(UUID productId, int quantity) {
    }
}

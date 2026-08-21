package com.ujjwal.search_indexer_service.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Local copy of order-service's OrderCreatedEvent shape — same duplication
 * rationale as inventory-service's/payment-service's own copies: no shared
 * schema module in this project's architecture, so this only has to stay
 * shape-compatible with the producer's event, not identical by package or
 * class identity.
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

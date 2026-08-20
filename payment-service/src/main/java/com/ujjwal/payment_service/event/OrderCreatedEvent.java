package com.ujjwal.payment_service.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Local copy of order-service's OrderCreatedEvent shape.
 *
 * This is a deliberate duplication, not an oversight: there is no shared
 * schema module in this project's architecture, so each consuming service
 * owns its own copy of the event shapes it depends on rather than taking a
 * compile-time dependency on order-service's code. The consumer-side wiring
 * (spring.json.use.type.headers: false + spring.json.value.default.type in
 * application.yml) deserializes incoming messages directly into this local
 * class by field shape, regardless of the producer-side class name in
 * order-service — so this only has to stay shape-compatible with the
 * producer's event, not identical by package or class identity.
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

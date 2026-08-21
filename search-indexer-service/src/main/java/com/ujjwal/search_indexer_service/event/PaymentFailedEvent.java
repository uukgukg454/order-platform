package com.ujjwal.search_indexer_service.event;

import java.util.List;
import java.util.UUID;

/**
 * Local copy of payment-service's PaymentFailedEvent shape — same
 * duplication rationale as OrderCreatedEvent's own comment in this package.
 * items/reason are carried here for shape-compatibility with the producer
 * even though this service's own listener only reads orderId — see
 * PaymentFailedEventListener.
 */
public record PaymentFailedEvent(UUID orderId, List<Item> items, String reason) {
    public record Item(UUID productId, int quantity) {
    }
}

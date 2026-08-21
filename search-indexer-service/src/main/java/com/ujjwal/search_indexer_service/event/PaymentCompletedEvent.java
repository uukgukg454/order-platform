package com.ujjwal.search_indexer_service.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Local copy of payment-service's PaymentCompletedEvent shape — same
 * duplication rationale as OrderCreatedEvent's own comment in this package.
 */
public record PaymentCompletedEvent(UUID orderId, UUID paymentId, BigDecimal amount, String currency) {
}

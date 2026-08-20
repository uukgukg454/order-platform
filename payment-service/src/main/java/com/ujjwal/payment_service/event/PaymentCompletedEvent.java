package com.ujjwal.payment_service.event;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Published after a payment is approved and its Payment row saved as
 * COMPLETED. No downstream consumers react to this yet — publishing it now
 * is what completes the saga's success path and gives future consumers
 * (e.g. order-service marking the order PAID) something to listen for.
 */
public record PaymentCompletedEvent(UUID orderId, UUID paymentId, BigDecimal amount, String currency) {
}

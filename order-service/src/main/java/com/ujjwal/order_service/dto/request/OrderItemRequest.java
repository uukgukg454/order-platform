package com.ujjwal.order_service.dto.request;

import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line item within a CreateOrderRequest, as submitted by a client.
 */
public record OrderItemRequest(
        UUID productId,
        String productName,
        @Positive BigDecimal unitPrice,
        @Positive Integer quantity
) {
}

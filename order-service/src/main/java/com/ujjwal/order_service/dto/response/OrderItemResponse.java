package com.ujjwal.order_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line item as returned to a client.
 */
public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity
) {
}

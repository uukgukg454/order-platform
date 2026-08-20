package com.ujjwal.order_service.dto.request;

import com.ujjwal.order_service.entity.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for PATCH /orders/{id}/status.
 */
public record UpdateOrderStatusRequest(
        @NotNull OrderStatus status
) {
}

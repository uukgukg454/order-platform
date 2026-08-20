package com.ujjwal.order_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request payload for placing an order.
 *
 * There is no totalAmount field here on purpose — OrderService computes it
 * server-side from the item lines instead of trusting a client-submitted
 * figure. See OrderService.createOrder.
 */
public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotBlank String currency,
        // @Valid cascades validation into each OrderItemRequest (its
        // @Positive constraints) — without it, only "is the list non-empty"
        // would be checked, not what's inside the list.
        @NotEmpty @Valid List<OrderItemRequest> items
) {
}

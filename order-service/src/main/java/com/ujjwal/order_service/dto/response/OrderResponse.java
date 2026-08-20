package com.ujjwal.order_service.dto.response;

import com.ujjwal.order_service.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Order as returned to a client.
 *
 * Deliberately not the Order entity itself: returning the entity would leak
 * JPA/Hibernate concerns (lazy proxies that fail to serialize outside a
 * transaction, internal field shapes) straight into the API contract, and
 * would couple that contract to schema changes instead of letting it evolve
 * independently.
 *
 * For list results (see OrderService.listOrdersByCustomer), items is left
 * empty rather than populated — that path intentionally never fetches
 * items, so there is nothing to put there without defeating the point of
 * skipping the fetch.
 */
public record OrderResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {
}

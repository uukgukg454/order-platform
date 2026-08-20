package com.ujjwal.order_service.exception;

import java.util.UUID;

/**
 * Thrown when a requested Order does not exist. Unchecked, and deliberately
 * left unhandled here — mapping it to an HTTP response (e.g. 404) is the
 * controller layer's concern, added later.
 */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}

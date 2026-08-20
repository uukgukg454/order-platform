package com.ujjwal.order_service.entity;

/**
 * Lifecycle states for an {@link Order}.
 *
 * Stored as VARCHAR (see {@code @Enumerated(EnumType.STRING)} on Order.status),
 * not ORDINAL — ordinal storage breaks silently if this enum is ever reordered
 * or a new value is inserted in the middle, since the stored integer no longer
 * lines up with the constant it used to mean.
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    CANCELLED
}

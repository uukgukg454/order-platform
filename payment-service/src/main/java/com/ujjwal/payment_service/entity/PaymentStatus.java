package com.ujjwal.payment_service.entity;

/**
 * Lifecycle states for a {@link Payment}.
 *
 * Stored as VARCHAR (see {@code @Enumerated(EnumType.STRING)} on
 * Payment.status), not ORDINAL — ordinal storage breaks silently if this
 * enum is ever reordered or a new value is inserted in the middle, since
 * the stored integer no longer lines up with the constant it used to mean.
 */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED
}

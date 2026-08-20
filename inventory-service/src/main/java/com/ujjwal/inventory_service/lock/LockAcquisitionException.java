package com.ujjwal.inventory_service.lock;

/**
 * Thrown when a distributed lock couldn't be acquired within the requested
 * wait time.
 */
public class LockAcquisitionException extends RuntimeException {

    public LockAcquisitionException(String message) {
        super(message);
    }

    public LockAcquisitionException(String message, Throwable cause) {
        super(message, cause);
    }
}

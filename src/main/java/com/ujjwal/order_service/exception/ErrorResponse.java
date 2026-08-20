package com.ujjwal.order_service.exception;

import java.time.Instant;

/**
 * Structured error body returned by GlobalExceptionHandler for every
 * handled exception, so clients get one consistent shape regardless of
 * which failure occurred.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}

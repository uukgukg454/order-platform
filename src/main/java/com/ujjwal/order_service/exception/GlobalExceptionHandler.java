package com.ujjwal.order_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
        // 404: the requested Order doesn't exist. ex.getMessage() is safe to
        // return as-is — OrderNotFoundException only ever carries the
        // requested id, never anything internal.
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationFailure(MethodArgumentNotValidException ex,
                                                                   HttpServletRequest request) {
        // 400: the request body failed @Valid constraints (see
        // CreateOrderRequest / OrderItemRequest). Per-field messages tell
        // the client exactly what to fix instead of a generic "bad request".
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMalformedRequestBody(HttpMessageNotReadableException ex,
                                                                       HttpServletRequest request) {
        // 400: the request body couldn't even be turned into an object in
        // the first place — either genuinely malformed JSON, or, as hit
        // here, a JSON string value that doesn't match any constant of a
        // target enum (e.g. status: "SHIPPED" against OrderStatus, which
        // only has CREATED/PAYMENT_PENDING/PAID/PAYMENT_FAILED/CANCELLED).
        // Jackson raises its own InvalidFormatException for that, which
        // Spring's HttpMessageConverter layer wraps in this
        // HttpMessageNotReadableException before it ever reaches the
        // controller — @Valid never runs, because there's no bound object
        // yet to validate. Either way this is a client error, not a server
        // one: the client sent something that isn't valid input, so it
        // belongs at 400 alongside MethodArgumentNotValidException rather
        // than falling through to the generic 500 catch-all below.
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        // 500: catch-all for anything not explicitly handled above — by
        // definition unanticipated, so it's logged in full server-side for
        // diagnosis. ex.getMessage() is deliberately NOT put in the response:
        // it can contain stack traces, SQL fragments, or other internals
        // that would hand an attacker information about the system, so the
        // client only ever sees a fixed, generic message.
        log.error("Unhandled exception while processing {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}

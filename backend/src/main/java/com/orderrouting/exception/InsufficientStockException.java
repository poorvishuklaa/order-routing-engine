package com.orderrouting.exception;

/** Raised when a reservation finds less stock than the engine expected (stale data). */
public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) {
        super(message);
    }
}

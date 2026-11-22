package com.orderrouting.exception;

import java.util.List;

/** Thrown when input breaks a business rule. Carries one or more readable messages. */
public class BadRequestException extends RuntimeException {

    private final List<String> errors;

    public BadRequestException(String message) {
        this(List.of(message));
    }

    public BadRequestException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}

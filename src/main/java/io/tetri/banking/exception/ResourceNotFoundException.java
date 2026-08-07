package io.tetri.banking.exception;

import java.util.UUID;

/**
 * Thrown whenever a lookup by id finds nothing. Kept as one generic type (rather than
 * one subclass per entity) with named factory methods, so the exception hierarchy
 * doesn't grow with every new entity while error messages stay specific and consistent.
 */
public class ResourceNotFoundException extends RuntimeException {

    private ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException account(UUID id) {
        return new ResourceNotFoundException("Account not found: id=" + id);
    }

    public static ResourceNotFoundException user(UUID id) {
        return new ResourceNotFoundException("User not found: id=" + id);
    }
}

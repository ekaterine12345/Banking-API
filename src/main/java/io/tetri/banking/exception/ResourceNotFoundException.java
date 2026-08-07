package io.tetri.banking.exception;

import java.util.UUID;

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

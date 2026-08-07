package io.tetri.banking.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Uniform error body returned by {@link io.tetri.banking.exception.GlobalExceptionHandler}
 * for every failure case, so clients can rely on a single shape regardless of what went wrong.
 * {@code fieldErrors} is empty unless the failure came from request body validation.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldViolation> fieldErrors
) {
    public record FieldViolation(String field, String message) {
    }
}

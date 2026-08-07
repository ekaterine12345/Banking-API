package io.tetri.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(

        @NotNull(message = "ownerId is required")
        UUID ownerId,

        @NotNull(message = "initialBalance is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "initialBalance must not be negative")
        BigDecimal initialBalance,

        @NotNull(message = "currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a 3-letter ISO 4217 code, e.g. USD")
        String currency  // supported currencies or one currency?  GEL, USD, EUR -> ONE accountID
) {
}
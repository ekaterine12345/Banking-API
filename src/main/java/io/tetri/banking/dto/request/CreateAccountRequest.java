package io.tetri.banking.dto.request;

import io.tetri.banking.enums.Currency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateAccountRequest(

        @NotNull(message = "ownerId is required")
        UUID ownerId,

        @NotNull(message = "initialBalance is required")
        @DecimalMin(value = "0.0", inclusive = true, message = "initialBalance must not be negative")
        BigDecimal initialBalance,

        @NotNull(message = "currency is required")
        Currency currency
) {
}

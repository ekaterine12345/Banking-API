package io.tetri.banking.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(

        @NotNull(message = "fromAccountId is required")
        UUID fromAccountId,

        @NotNull(message = "toAccountId is required")
        UUID toAccountId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "amount must be greater than zero")
        BigDecimal amount
) {
}

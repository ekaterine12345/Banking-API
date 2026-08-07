package io.tetri.banking.dto.response;

import io.tetri.banking.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        AccountOwnerResponse owner,
        Instant createdAt
) {
}

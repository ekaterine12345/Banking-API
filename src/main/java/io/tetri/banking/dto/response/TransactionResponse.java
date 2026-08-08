package io.tetri.banking.dto.response;

import io.tetri.banking.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID fromAccountId,
        UUID toAccountId,
        BigDecimal amount,
        TransactionStatus status,
        String failureReason,
        Instant createdAt
) {
}

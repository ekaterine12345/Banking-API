package io.tetri.banking.dto.response;

import java.util.UUID;

public record TopAccountTransactionsResponse(
        UUID accountId,
        String accountNumber,
        Long transactionCount
) {
}

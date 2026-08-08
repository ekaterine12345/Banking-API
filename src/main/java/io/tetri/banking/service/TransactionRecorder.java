package io.tetri.banking.service;

import java.math.BigDecimal;
import java.util.UUID;

public interface TransactionRecorder {

    void recordFailure(UUID fromAccountId, UUID toAccountId, BigDecimal amount,
                       String idempotencyKey, String failureReason);
}

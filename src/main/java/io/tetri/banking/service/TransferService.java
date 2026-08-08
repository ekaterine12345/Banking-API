package io.tetri.banking.service;

import io.tetri.banking.dto.request.TransferRequest;
import io.tetri.banking.dto.response.TransactionResponse;

public interface TransferService {

    TransactionResponse transfer(TransferRequest request, String idempotencyKey);
}

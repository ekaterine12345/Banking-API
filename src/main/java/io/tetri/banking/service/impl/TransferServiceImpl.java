package io.tetri.banking.service.impl;

import io.tetri.banking.dto.request.TransferRequest;
import io.tetri.banking.dto.response.TransactionResponse;
import io.tetri.banking.exception.ConflictException;
import io.tetri.banking.exception.InvalidTransferException;
import io.tetri.banking.exception.ResourceNotFoundException;
import io.tetri.banking.exception.TransferNotAllowedException;
import io.tetri.banking.repository.AccountRepository;
import io.tetri.banking.repository.TransactionRepository;
import io.tetri.banking.service.TransactionRecorder;
import io.tetri.banking.service.TransferProcessor;
import io.tetri.banking.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransferProcessor transferProcessor;
    private final TransactionRecorder transactionRecorder;

    @Override
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw InvalidTransferException.sameAccount();
        }

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new ConflictException("Idempotency-Key has already been used for a transfer");
        }

        try {
            return transferProcessor.process(request, idempotencyKey);
        } catch (ResourceNotFoundException | TransferNotAllowedException ex) {
            recordFailure(request, idempotencyKey, ex.getMessage());
            throw ex;
        }
    }

    private void recordFailure(TransferRequest request, String idempotencyKey, String reason) {
        try {
            UUID fromId = accountRepository.existsById(request.fromAccountId()) ? request.fromAccountId() : null;
            UUID toId = accountRepository.existsById(request.toAccountId()) ? request.toAccountId() : null;

            transactionRecorder.recordFailure(fromId, toId, request.amount(), idempotencyKey, reason);
        } catch (RuntimeException recordingException) {
            log.error("Failed to record failed transfer attempt for idempotencyKey={}", idempotencyKey, recordingException);
        }
    }
}

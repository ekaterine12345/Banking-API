package io.tetri.banking.service.impl;

import io.tetri.banking.dto.request.TransferRequest;
import io.tetri.banking.dto.response.TransactionResponse;
import io.tetri.banking.entity.Transaction;
import io.tetri.banking.enums.TransactionStatus;
import io.tetri.banking.exception.ConflictException;
import io.tetri.banking.exception.InvalidTransferException;
import io.tetri.banking.exception.ResourceNotFoundException;
import io.tetri.banking.exception.TransferNotAllowedException;
import io.tetri.banking.exception.TransferReplayException;
import io.tetri.banking.mapper.TransactionMapper;
import io.tetri.banking.repository.AccountRepository;
import io.tetri.banking.repository.TransactionRepository;
import io.tetri.banking.service.TransactionRecorder;
import io.tetri.banking.service.TransferProcessor;
import io.tetri.banking.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransferProcessor transferProcessor;
    private final TransactionRecorder transactionRecorder;
    private final TransactionMapper transactionMapper;

    @Override
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey) {
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw InvalidTransferException.sameAccount();
        }

        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), request);
        }

        try {
            return transferProcessor.process(request, idempotencyKey);
        } catch (ResourceNotFoundException ex) {
            recordFailure(request, idempotencyKey, ex.getMessage(), HttpStatus.NOT_FOUND);
            throw ex;
        } catch (TransferNotAllowedException ex) {
            recordFailure(request, idempotencyKey, ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
            throw ex;
        }
    }

    private TransactionResponse replay(Transaction existing, TransferRequest request) {
        if (!sameParameters(existing, request)) {
            throw new ConflictException("Idempotency-Key has already been used with different parameters");
        }

        if (existing.getStatus() == TransactionStatus.FAILED) {
            HttpStatus status = existing.getFailureStatus() != null
                    ? HttpStatus.valueOf(existing.getFailureStatus())
                    : HttpStatus.UNPROCESSABLE_ENTITY;

            throw new TransferReplayException(status, existing.getFailureReason());
        }

        log.info("Replayed transfer result for idempotencyKey={}", existing.getIdempotencyKey());

        return transactionMapper.toResponse(existing);
    }

    private boolean sameParameters(Transaction existing, TransferRequest request) {
        UUID storedFromId = existing.getFromAccount() != null ? existing.getFromAccount().getId() : null;
        UUID storedToId = existing.getToAccount() != null ? existing.getToAccount().getId() : null;

        return Objects.equals(storedFromId, request.fromAccountId())
                && Objects.equals(storedToId, request.toAccountId())
                && existing.getAmount().compareTo(request.amount()) == 0;
    }

    private void recordFailure(TransferRequest request, String idempotencyKey, String reason, HttpStatus status) {
        try {
            UUID fromId = accountRepository.existsById(request.fromAccountId()) ? request.fromAccountId() : null;
            UUID toId = accountRepository.existsById(request.toAccountId()) ? request.toAccountId() : null;

            transactionRecorder.recordFailure(fromId, toId, request.amount(), idempotencyKey, reason, status.value());
        } catch (RuntimeException recordingException) {
            log.error("Failed to record failed transfer attempt for idempotencyKey={}", idempotencyKey, recordingException);
        }
    }
}

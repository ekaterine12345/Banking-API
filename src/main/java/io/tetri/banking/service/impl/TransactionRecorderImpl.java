package io.tetri.banking.service.impl;

import io.tetri.banking.entity.Account;
import io.tetri.banking.entity.Transaction;
import io.tetri.banking.mapper.TransactionMapper;
import io.tetri.banking.repository.AccountRepository;
import io.tetri.banking.repository.TransactionRepository;
import io.tetri.banking.service.TransactionRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionRecorderImpl implements TransactionRecorder {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional
    public void recordFailure(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String idempotencyKey, String failureReason) {
        Account fromAccount = fromAccountId != null ? accountRepository.getReferenceById(fromAccountId) : null;
        Account toAccount = toAccountId != null ? accountRepository.getReferenceById(toAccountId) : null;

        Transaction transaction = transactionMapper.toFailedTransaction(fromAccount, toAccount, amount, idempotencyKey, failureReason);

        transactionRepository.save(transaction);

        log.info("Recorded failed transfer idempotencyKey={} reason={}", idempotencyKey, failureReason);
    }
}

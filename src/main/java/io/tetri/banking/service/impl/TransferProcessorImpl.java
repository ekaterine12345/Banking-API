package io.tetri.banking.service.impl;

import io.tetri.banking.dto.request.TransferRequest;
import io.tetri.banking.dto.response.TransactionResponse;
import io.tetri.banking.entity.Account;
import io.tetri.banking.entity.Transaction;
import io.tetri.banking.enums.AccountStatus;
import io.tetri.banking.exception.ResourceNotFoundException;
import io.tetri.banking.exception.TransferNotAllowedException;
import io.tetri.banking.mapper.TransactionMapper;
import io.tetri.banking.repository.AccountRepository;
import io.tetri.banking.repository.TransactionRepository;
import io.tetri.banking.service.TransferProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferProcessorImpl implements TransferProcessor {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Override
    @Transactional
    public TransactionResponse process(TransferRequest request, String idempotencyKey) {
        LockedAccounts accounts = lockAccounts(request.fromAccountId(), request.toAccountId());

        validateTransfer(accounts.fromAccount(), accounts.toAccount(), request.amount());

        applyBalanceChange(accounts.fromAccount(), accounts.toAccount(), request.amount());

        Transaction transaction = transactionMapper.toSuccessTransaction(
                accounts.fromAccount(), accounts.toAccount(), request.amount(), idempotencyKey);
        Transaction saved = transactionRepository.save(transaction);

        log.info("Transfer completed id={} from={} to={} amount={}",
                saved.getId(), accounts.fromAccount().getId(), accounts.toAccount().getId(), request.amount());

        return transactionMapper.toResponse(saved);
    }

    private LockedAccounts lockAccounts(UUID fromId, UUID toId) {
        UUID firstId = fromId.compareTo(toId) < 0 ? fromId : toId;
        UUID secondId = firstId.equals(fromId) ? toId : fromId;

        Account first = accountRepository.findByIdForUpdate(firstId).orElse(null);
        Account second = accountRepository.findByIdForUpdate(secondId).orElse(null);

        Account fromAccount = firstId.equals(fromId) ? first : second;
        Account toAccount = firstId.equals(fromId) ? second : first;

        if (fromAccount == null) {
            throw ResourceNotFoundException.account(fromId);
        }

        if (toAccount == null) {
            throw ResourceNotFoundException.account(toId);
        }

        return new LockedAccounts(fromAccount, toAccount);
    }

    private void validateTransfer(Account fromAccount, Account toAccount, BigDecimal amount) {
        if (!fromAccount.getCurrency().equals(toAccount.getCurrency())) {
            throw TransferNotAllowedException.currencyMismatch(fromAccount.getCurrency(), toAccount.getCurrency());
        }

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw TransferNotAllowedException.accountNotActive(fromAccount.getId(), fromAccount.getStatus());
        }

        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw TransferNotAllowedException.accountNotActive(toAccount.getId(), toAccount.getStatus());
        }

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw TransferNotAllowedException.insufficientBalance(fromAccount.getId());
        }
    }

    private void applyBalanceChange(Account fromAccount, Account toAccount, BigDecimal amount) {
        fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
        toAccount.setBalance(toAccount.getBalance().add(amount));

        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);
    }

    private record LockedAccounts(Account fromAccount, Account toAccount) {
    }
}

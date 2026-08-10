package io.tetri.banking.service.impl;

import io.tetri.banking.dto.request.CreateAccountRequest;
import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.dto.response.TopAccountTransactionsResponse;
import io.tetri.banking.dto.response.TransactionResponse;
import io.tetri.banking.entity.Account;
import io.tetri.banking.entity.Transaction;
import io.tetri.banking.entity.User;
import io.tetri.banking.enums.AccountStatus;
import io.tetri.banking.exception.ConflictException;
import io.tetri.banking.exception.ResourceNotFoundException;
import io.tetri.banking.mapper.AccountMapper;
import io.tetri.banking.mapper.TransactionMapper;
import io.tetri.banking.repository.AccountRepository;
import io.tetri.banking.repository.TransactionRepository;
import io.tetri.banking.repository.TransactionSpecifications;
import io.tetri.banking.repository.UserRepository;
import io.tetri.banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Override
    public List<AccountResponse> listAccounts() {
        return accountRepository.findAll().stream()
                .map(accountMapper::toResponse)
                .toList();
    }

    @Override
    public AccountResponse getAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Account lookup failed, no account with id={}", id);
                    return ResourceNotFoundException.account(id);
                });
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public AccountResponse openAccount(CreateAccountRequest request) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> {
                    log.warn("Account creation failed, no user with id={}", request.ownerId());
                    return ResourceNotFoundException.user(request.ownerId());
                });

        String accountNumber = generateAccountNumber();

        Account account = accountMapper.toAccount(owner, accountNumber, request);

        Account saved = accountRepository.save(account);

        log.info("Opened new account id={} accountNumber={} ownerId={}",
                saved.getId(), saved.getAccountNumber(), owner.getId());

        return accountMapper.toResponse(saved);
    }

    private String generateAccountNumber() {
        String accountNumber;

        do {
            accountNumber = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 16)
                    .toUpperCase();
        } while (accountRepository.existsByAccountNumber(accountNumber));

        return accountNumber;
    }

    @Override
    @Transactional
    public void closeAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Account close failed, no account with id={}", id);
                    return ResourceNotFoundException.account(id);
                });

        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            log.warn("Account close rejected, account id={} holds balance={}", id, account.getBalance());
            throw new ConflictException("Account cannot be closed while it holds a non-zero balance");
        }

        account.setDeletedAt(Instant.now());
        account.setStatus(AccountStatus.CLOSED);

        log.info("Closed account id={}", id);
    }

    @Override
    public Page<TransactionResponse> getTransactionHistory(UUID id, Instant from, Instant to, Pageable pageable) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new ResponseStatusException(BAD_REQUEST, "from must be before to");
        }

        if (!accountRepository.existsById(id)) {
            log.warn("Transaction history lookup failed, no account with id={}", id);
            throw ResourceNotFoundException.account(id);
        }

        Pageable sortedPageable = PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Transaction> spec = Specification.where(TransactionSpecifications.forAccount(id))
                .and(TransactionSpecifications.createdFrom(from))
                .and(TransactionSpecifications.createdTo(to));

        return transactionRepository.findAll(spec, sortedPageable)
                .map(transactionMapper::toResponse);
    }

    @Override
    public List<TopAccountTransactionsResponse> getTopAccountsByTransactionCount() {
        return transactionRepository.findTopAccountsByTransactionCount().stream()
                .map(account -> new TopAccountTransactionsResponse(
                        account.getAccountId(),
                        account.getAccountNumber(),
                        account.getTransactionCount()
                ))
                .toList();
    }
}

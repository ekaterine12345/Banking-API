package io.tetri.banking.service.impl;

import io.tetri.banking.dto.request.CreateAccountRequest;
import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.entity.Account;
import io.tetri.banking.entity.User;
import io.tetri.banking.enums.AccountStatus;
import io.tetri.banking.exception.ConflictException;
import io.tetri.banking.exception.ResourceNotFoundException;
import io.tetri.banking.mapper.AccountMapper;
import io.tetri.banking.repository.AccountRepository;
import io.tetri.banking.repository.UserRepository;
import io.tetri.banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final UserRepository userRepository;

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

        Account account = new Account();
        account.setOwner(owner);
        account.setAccountNumber(accountNumber);
        account.setBalance(request.initialBalance());
        account.setCurrency(request.currency());

        Account saved = accountRepository.save(account);

        log.info("Opened new account id={} accountNumber={} ownerId={}",
                saved.getId(), saved.getAccountNumber(), owner.getId()
        );

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
}

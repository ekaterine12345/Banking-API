package io.tetri.banking.service.impl;

import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.entity.Account;
import io.tetri.banking.exception.ResourceNotFoundException;
import io.tetri.banking.mapper.AccountMapper;
import io.tetri.banking.repository.AccountRepository;
import io.tetri.banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

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
}

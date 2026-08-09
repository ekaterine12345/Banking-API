package io.tetri.banking.service;

import io.tetri.banking.dto.request.CreateAccountRequest;
import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.dto.response.TopAccountTransactionsResponse;
import io.tetri.banking.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AccountService {

    List<AccountResponse> listAccounts();

    AccountResponse getAccount(UUID id);

    AccountResponse openAccount(CreateAccountRequest request);

    void closeAccount(UUID id);

    Page<TransactionResponse> getTransactionHistory(UUID id, Instant from, Instant to, Pageable pageable);

    List<TopAccountTransactionsResponse> getTopAccountsByTransactionCount();
}

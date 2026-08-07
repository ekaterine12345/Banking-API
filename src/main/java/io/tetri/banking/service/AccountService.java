package io.tetri.banking.service;

import io.tetri.banking.dto.request.CreateAccountRequest;
import io.tetri.banking.dto.response.AccountResponse;

import java.util.List;
import java.util.UUID;

public interface AccountService {

    List<AccountResponse> listAccounts();

    AccountResponse getAccount(UUID id);

    AccountResponse openAccount(CreateAccountRequest request);

    void closeAccount(UUID id);
}

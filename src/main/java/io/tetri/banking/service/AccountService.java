package io.tetri.banking.service;

import io.tetri.banking.dto.response.AccountResponse;

import java.util.List;
import java.util.UUID;

public interface AccountService {

    List<AccountResponse> listAccounts();

    AccountResponse getAccount(UUID id);
}

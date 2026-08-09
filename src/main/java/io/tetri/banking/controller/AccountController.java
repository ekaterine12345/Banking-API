package io.tetri.banking.controller;

import io.tetri.banking.dto.request.CreateAccountRequest;
import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.dto.response.TopAccountTransactionsResponse;
import io.tetri.banking.dto.response.TransactionResponse;
import io.tetri.banking.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        return ResponseEntity.ok(accountService.listAccounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(accountService.getAccount(id));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getTransactionHistory(@PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20) Pageable pageable) {

        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return ResponseEntity.ok(accountService.getTransactionHistory(id, from, to, pageOnly));
    }

    @GetMapping("/top-by-transactions")
    public ResponseEntity<List<TopAccountTransactionsResponse>> getTopAccountsByTransactionCount() {
        return ResponseEntity.ok(accountService.getTopAccountsByTransactionCount());
    }

    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.openAccount(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> closeAccount(@PathVariable UUID id) {
        accountService.closeAccount(id);

        return ResponseEntity.noContent().build();
    }
}

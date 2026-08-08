package io.tetri.banking.exception;

import io.tetri.banking.enums.AccountStatus;
import io.tetri.banking.enums.Currency;

import java.util.UUID;

public class TransferNotAllowedException extends RuntimeException {

    private TransferNotAllowedException(String message) {
        super(message);
    }

    public static TransferNotAllowedException insufficientBalance(UUID accountId) {
        return new TransferNotAllowedException("Account " + accountId + " has insufficient balance for this transfer");
    }

    public static TransferNotAllowedException accountNotActive(UUID accountId, AccountStatus status) {
        return new TransferNotAllowedException("Account " + accountId + " is " + status + " and cannot take part in a transfer");
    }

    public static TransferNotAllowedException currencyMismatch(Currency fromCurrency, Currency toCurrency) {
        return new TransferNotAllowedException("Cannot transfer between accounts with different currencies: " + fromCurrency + " and " + toCurrency);
    }
}

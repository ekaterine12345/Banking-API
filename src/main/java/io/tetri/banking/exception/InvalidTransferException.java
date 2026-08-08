package io.tetri.banking.exception;

public class InvalidTransferException extends RuntimeException {

    private InvalidTransferException(String message) {
        super(message);
    }

    public static InvalidTransferException sameAccount() {
        return new InvalidTransferException("Source and destination accounts must be different");
    }
}

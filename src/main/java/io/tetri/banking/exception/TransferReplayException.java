package io.tetri.banking.exception;

import org.springframework.http.HttpStatus;

public class TransferReplayException extends RuntimeException {

    private final HttpStatus status;

    public TransferReplayException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

CREATE TABLE users
(
    id         UUID PRIMARY KEY,
    first_name VARCHAR(255)             NOT NULL,
    last_name  VARCHAR(255)             NOT NULL,
    email      VARCHAR(255)             NOT NULL,
    username   VARCHAR(255)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_username UNIQUE (username)
);

CREATE TABLE accounts
(
    id             UUID PRIMARY KEY,
    owner_id       UUID                     NOT NULL,
    account_number VARCHAR(255)             NOT NULL,
    balance        NUMERIC(19, 2)           NOT NULL,
    currency       VARCHAR(3)               NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE,
    deleted_at     TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_accounts_account_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);

CREATE TABLE transactions
(
    id               UUID PRIMARY KEY,
    from_account_id  UUID,
    to_account_id    UUID,
    amount           NUMERIC(19, 2)           NOT NULL,
    status           VARCHAR(20)              NOT NULL,
    failure_reason   VARCHAR(500),
    idempotency_key  VARCHAR(255)             NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP WITH TIME ZONE,
    deleted_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_transactions_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_transactions_from_account FOREIGN KEY (from_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transactions_to_account FOREIGN KEY (to_account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_transactions_from_account_id ON transactions (from_account_id);
CREATE INDEX idx_transactions_to_account_id ON transactions (to_account_id);
CREATE INDEX idx_transactions_created_at ON transactions (created_at);

package io.tetri.banking.repository;

import io.tetri.banking.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}

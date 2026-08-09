package io.tetri.banking.repository;

import io.tetri.banking.entity.Transaction;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> forAccount(UUID accountId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.equal(root.get("fromAccount").get("id"), accountId),
                criteriaBuilder.equal(root.get("toAccount").get("id"), accountId)
        );
    }

    public static Specification<Transaction> createdFrom(Instant from) {
        if (from == null) {
            return null;
        }

        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Transaction> createdTo(Instant to) {
        if (to == null) {
            return null;
        }

        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("createdAt"), to);
    }
}

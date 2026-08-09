package io.tetri.banking.repository;

import io.tetri.banking.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    @Query(value = """
            select a.id as "accountId",
                   a.account_number as "accountNumber",
                   count(t.account_id) as "transactionCount"
            from accounts a
            left join (
                select from_account_id as account_id from transactions where from_account_id is not null
                                                                                   and status = 'SUCCESS'
                union all
                select to_account_id as account_id from transactions where to_account_id is not null
                                                                                 and status = 'SUCCESS'
            ) t on t.account_id = a.id
            where a.deleted_at is null
            group by a.id, a.account_number, a.created_at
            order by count(t.account_id) desc, a.created_at asc
            limit 5
            """, nativeQuery = true)
    List<TopAccountTransactionCount> findTopAccountsByTransactionCount();

    interface TopAccountTransactionCount {
        UUID getAccountId();

        String getAccountNumber();

        Long getTransactionCount();
    }
}

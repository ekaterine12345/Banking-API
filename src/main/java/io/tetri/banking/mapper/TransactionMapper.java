package io.tetri.banking.mapper;

import io.tetri.banking.dto.response.TransactionResponse;
import io.tetri.banking.entity.Account;
import io.tetri.banking.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "fromAccountId", source = "fromAccount.id")
    @Mapping(target = "toAccountId", source = "toAccount.id")
    TransactionResponse toResponse(Transaction transaction);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "failureReason", ignore = true)
    @Mapping(target = "status", constant = "SUCCESS")
    Transaction toSuccessTransaction(Account fromAccount, Account toAccount, BigDecimal amount, String idempotencyKey);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "status", constant = "FAILED")
    Transaction toFailedTransaction(Account fromAccount, Account toAccount, BigDecimal amount, String idempotencyKey,
                                     String failureReason, Integer failureStatus);
}

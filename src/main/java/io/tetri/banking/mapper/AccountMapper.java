package io.tetri.banking.mapper;

import io.tetri.banking.dto.request.CreateAccountRequest;
import io.tetri.banking.dto.response.AccountOwnerResponse;
import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.entity.Account;
import io.tetri.banking.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    AccountResponse toResponse(Account account);

    @Mapping(target = "fullName", expression = "java(owner.getFirstName() + \" \" + owner.getLastName())")
    AccountOwnerResponse toOwnerResponse(User owner);

    @Mapping(target = "owner", source = "owner")
    @Mapping(target = "accountNumber", source = "accountNumber")
    @Mapping(target = "balance", source = "request.initialBalance")
    @Mapping(target = "currency", source = "request.currency")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Account toAccount(User owner, String accountNumber, CreateAccountRequest request);
}

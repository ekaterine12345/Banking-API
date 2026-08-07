package io.tetri.banking.mapper;

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
}

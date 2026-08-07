package io.tetri.banking.mapper;

import io.tetri.banking.dto.response.AccountOwnerResponse;
import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.entity.Account;
import io.tetri.banking.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                account.getCurrency(),
                toOwnerResponse(account.getOwner()),
                account.getCreatedAt()
        );
    }

    private AccountOwnerResponse toOwnerResponse(User owner) {
        String fullName = owner.getFirstName() + " " + owner.getLastName();
        return new AccountOwnerResponse(owner.getId(), fullName, owner.getEmail());
    }
}

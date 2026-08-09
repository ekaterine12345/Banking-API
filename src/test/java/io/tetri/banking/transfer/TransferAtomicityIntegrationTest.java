package io.tetri.banking.transfer;

import io.tetri.banking.AbstractIntegrationTest;
import io.tetri.banking.dto.request.CreateAccountRequest;
import io.tetri.banking.dto.request.CreateUserRequest;
import io.tetri.banking.dto.request.TransferRequest;
import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.dto.response.UserResponse;
import io.tetri.banking.enums.Currency;
import io.tetri.banking.repository.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

class TransferAtomicityIntegrationTest extends AbstractIntegrationTest {

    @MockitoSpyBean
    private AccountRepository accountRepository;

    @Test
    void transferRollsBackCompletelyWhenCreditingTheDestinationFails() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        doThrow(new RuntimeException("simulated crash between debit and credit"))
                .when(accountRepository)
                .save(argThat(account -> account != null && account.getId().equals(to)));

        ResponseEntity<String> response = transfer(from, to, new BigDecimal("40.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(getBalance(from)).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(getBalance(to)).isEqualByComparingTo(new BigDecimal("0.00"));

        Mockito.reset(accountRepository);
    }

    private UUID createUser() {
        String unique = UUID.randomUUID().toString();
        CreateUserRequest request = new CreateUserRequest("Test", "User", "user-" + unique + "@example.com", "user" + unique.substring(0, 8));
        ResponseEntity<UserResponse> response = restTemplate.postForEntity("/api/users", request, UserResponse.class);
        return response.getBody().id();
    }

    private UUID createAccount(UUID ownerId, BigDecimal balance, Currency currency) {
        CreateAccountRequest request = new CreateAccountRequest(ownerId, balance, currency);
        ResponseEntity<AccountResponse> response = restTemplate.postForEntity("/api/accounts", request, AccountResponse.class);
        return response.getBody().id();
    }

    private BigDecimal getBalance(UUID accountId) {
        ResponseEntity<AccountResponse> response = restTemplate.getForEntity("/api/accounts/{id}", AccountResponse.class, accountId);
        return response.getBody().balance();
    }

    private ResponseEntity<String> transfer(UUID from, UUID to, BigDecimal amount, String idempotencyKey) {
        TransferRequest request = new TransferRequest(from, to, amount);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);

        HttpEntity<TransferRequest> entity = new HttpEntity<>(request, headers);

        return restTemplate.exchange("/api/transfers", HttpMethod.POST, entity, String.class);
    }
}

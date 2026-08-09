package io.tetri.banking.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.tetri.banking.AbstractIntegrationTest;
import io.tetri.banking.dto.request.CreateAccountRequest;
import io.tetri.banking.dto.request.CreateUserRequest;
import io.tetri.banking.dto.request.TransferRequest;
import io.tetri.banking.dto.response.AccountResponse;
import io.tetri.banking.dto.response.UserResponse;
import io.tetri.banking.enums.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TransferIntegrationTest extends AbstractIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void transferDebitsSourceAndCreditsDestination() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        ResponseEntity<String> response = transfer(from, to, new BigDecimal("40.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(getBalance(from)).isEqualByComparingTo("60.00");
        assertThat(getBalance(to)).isEqualByComparingTo("40.00");
    }

    @Test
    void transferFailsWhenBalanceIsInsufficient() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("10.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        ResponseEntity<String> response = transfer(from, to, new BigDecimal("50.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(getBalance(from)).isEqualByComparingTo("10.00");
        assertThat(getBalance(to)).isEqualByComparingTo("0.00");
    }

    @Test
    void transferFailsForSameAccount() {
        UUID owner = createUser();
        UUID account = createAccount(owner, new BigDecimal("100.00"), Currency.USD);

        ResponseEntity<String> response = transfer(account, account, new BigDecimal("10.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void transferFailsForZeroAmount() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        ResponseEntity<String> response = transfer(from, to, BigDecimal.ZERO, UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void transferFailsForNegativeAmount() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        ResponseEntity<String> response = transfer(from, to, new BigDecimal("-10.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void transferFailsWhenSourceAccountDoesNotExist() {
        UUID owner = createUser();
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        ResponseEntity<String> response = transfer(UUID.randomUUID(), to, new BigDecimal("10.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void transferFailsWhenDestinationAccountDoesNotExist() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);

        ResponseEntity<String> response = transfer(from, UUID.randomUUID(), new BigDecimal("10.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void transferFailsForCurrencyMismatch() {
        UUID owner = createUser();
        UUID usdAccount = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID eurAccount = createAccount(owner, BigDecimal.ZERO, Currency.EUR);

        ResponseEntity<String> response = transfer(usdAccount, eurAccount, new BigDecimal("10.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void transferFailsWhenDestinationAccountIsClosed() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        restTemplate.delete("/api/accounts/{id}", to);

        ResponseEntity<String> response = transfer(from, to, new BigDecimal("10.00"), UUID.randomUUID().toString());

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void missingIdempotencyKeyHeaderIsRejected() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        TransferRequest request = new TransferRequest(from, to, new BigDecimal("10.00"));
        ResponseEntity<String> response = restTemplate.postForEntity("/api/transfers", request, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void repeatedSuccessfulRequestReturnsOriginalResultWithoutDoubleDebit() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);
        String idempotencyKey = UUID.randomUUID().toString();

        ResponseEntity<String> first = transfer(from, to, new BigDecimal("40.00"), idempotencyKey);
        ResponseEntity<String> second = transfer(from, to, new BigDecimal("40.00"), idempotencyKey);

        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(second.getStatusCode().value()).isEqualTo(201);
        assertThat(readTree(second).get("id").asText()).isEqualTo(readTree(first).get("id").asText());

        assertThat(getBalance(from)).isEqualByComparingTo("60.00");
        assertThat(getBalance(to)).isEqualByComparingTo("40.00");
    }

    @Test
    void repeatedFailedRequestReplaysOriginalFailureInsteadOfReEvaluating() {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("10.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);
        UUID funder = createAccount(owner, new BigDecimal("1000.00"), Currency.USD);
        String idempotencyKey = UUID.randomUUID().toString();

        ResponseEntity<String> first = transfer(from, to, new BigDecimal("50.00"), idempotencyKey);
        assertThat(first.getStatusCode().value()).isEqualTo(422);

        ResponseEntity<String> topUp = transfer(funder, from, new BigDecimal("200.00"), UUID.randomUUID().toString());
        assertThat(topUp.getStatusCode().value()).isEqualTo(201);
        assertThat(getBalance(from)).isEqualByComparingTo("210.00");

        ResponseEntity<String> second = transfer(from, to, new BigDecimal("50.00"), idempotencyKey);

        assertThat(second.getStatusCode().value()).isEqualTo(422);
        assertThat(readTree(second).get("message").asText()).isEqualTo(readTree(first).get("message").asText());
        assertThat(getBalance(from)).isEqualByComparingTo("210.00");
        assertThat(getBalance(to)).isEqualByComparingTo("0.00");
    }

    @Test
    void sameIdempotencyKeyWithDifferentParametersIsRejected() {
        UUID owner = createUser();
        UUID accountA = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID accountB = createAccount(owner, BigDecimal.ZERO, Currency.USD);
        UUID accountC = createAccount(owner, BigDecimal.ZERO, Currency.USD);
        String idempotencyKey = UUID.randomUUID().toString();

        ResponseEntity<String> first = transfer(accountA, accountB, new BigDecimal("10.00"), idempotencyKey);
        assertThat(first.getStatusCode().value()).isEqualTo(201);

        ResponseEntity<String> second = transfer(accountA, accountC, new BigDecimal("10.00"), idempotencyKey);
        assertThat(second.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void concurrentTransfersFromSameAccountNeverOverdraft() throws Exception {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        int attempts = 5;
        BigDecimal amount = new BigDecimal("100.00");

        List<ResponseEntity<String>> results = runConcurrently(attempts,
                index -> transfer(from, to, amount, UUID.randomUUID().toString()));

        long successCount = results.stream().filter(r -> r.getStatusCode().value() == 201).count();
        long insufficientBalanceCount = results.stream().filter(r -> r.getStatusCode().value() == 422).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(insufficientBalanceCount).isEqualTo(attempts - 1);
        assertThat(getBalance(from)).isEqualByComparingTo("0.00");
        assertThat(getBalance(to)).isEqualByComparingTo(amount);
    }

    @Test
    void concurrentOppositeDirectionTransfersDoNotDeadlock() throws Exception {
        UUID owner = createUser();
        UUID accountA = createAccount(owner, new BigDecimal("100.00"), Currency.USD);
        UUID accountB = createAccount(owner, new BigDecimal("100.00"), Currency.USD);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<ResponseEntity<String>> aToB = () -> {
            ready.countDown();
            start.await();
            return transfer(accountA, accountB, new BigDecimal("30.00"), UUID.randomUUID().toString());
        };
        Callable<ResponseEntity<String>> bToA = () -> {
            ready.countDown();
            start.await();
            return transfer(accountB, accountA, new BigDecimal("20.00"), UUID.randomUUID().toString());
        };

        Future<ResponseEntity<String>> futureA = executor.submit(aToB);
        Future<ResponseEntity<String>> futureB = executor.submit(bToA);

        ready.await();
        start.countDown();

        ResponseEntity<String> resultA = futureA.get(10, TimeUnit.SECONDS);
        ResponseEntity<String> resultB = futureB.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(resultA.getStatusCode().value()).isEqualTo(201);
        assertThat(resultB.getStatusCode().value()).isEqualTo(201);
        assertThat(getBalance(accountA)).isEqualByComparingTo("90.00");
        assertThat(getBalance(accountB)).isEqualByComparingTo("110.00");
    }

    @Test
    void concurrentRequestsWithSameIdempotencyKeyApplyTransferExactlyOnce() throws Exception {
        UUID owner = createUser();
        UUID from = createAccount(owner, new BigDecimal("500.00"), Currency.USD);
        UUID to = createAccount(owner, BigDecimal.ZERO, Currency.USD);

        int attempts = 5;
        BigDecimal amount = new BigDecimal("50.00");
        String idempotencyKey = UUID.randomUUID().toString();

        List<ResponseEntity<String>> results = runConcurrently(attempts,
                index -> transfer(from, to, amount, idempotencyKey));

        long successCount = results.stream().filter(r -> r.getStatusCode().value() == 201).count();
        long conflictCount = results.stream().filter(r -> r.getStatusCode().value() == 409).count();

        assertThat(successCount).isGreaterThanOrEqualTo(1);
        assertThat(successCount + conflictCount).isEqualTo(attempts);
        assertThat(getBalance(from)).isEqualByComparingTo("450.00");
        assertThat(getBalance(to)).isEqualByComparingTo(amount);
    }

    private List<ResponseEntity<String>> runConcurrently(int attempts, java.util.function.IntFunction<ResponseEntity<String>> task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        for (int i = 0; i < attempts; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.apply(index);
            }));
        }

        ready.await();
        start.countDown();

        List<ResponseEntity<String>> results = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            results.add(future.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        return results;
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

    private JsonNode readTree(ResponseEntity<String> response) {
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

# Mini Banking API

A small Spring Boot service that manages bank accounts and money transfers between them. Built as a
technical task, so this README leans heavily on *why* things are built the way they are, not just
*what* they do.

## Stack

- Java 25, Spring Boot 4.1, Spring Data JPA, Spring Web
- PostgreSQL, Flyway for schema migrations
- MapStruct for entity ↔ DTO mapping
- springdoc-openapi (Swagger UI) for interactive docs
- Testcontainers + a real Postgres container for integration tests

## Running it locally

```bash
docker compose up -d
./gradlew bootRun
```

The app expects Postgres on `localhost:5432`, database `banking`, user/password `postgres`/`postgres`
(all overridable via `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`). Flyway runs the migrations
automatically on startup. Swagger UI is at `http://localhost:8080/swagger-ui.html`.

```bash
./gradlew test
```

runs the integration test suite against a disposable Testcontainers Postgres instance — it does not
touch the `docker-compose` database, so both can coexist.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/users` | Register a user |
| PATCH | `/api/users/{id}` | Partially update a user's details |
| POST | `/api/accounts` | Open a new account for a user |
| GET | `/api/accounts` | List all accounts |
| GET | `/api/accounts/{id}` | Get a single account |
| DELETE | `/api/accounts/{id}` | Close an account (only if balance is zero) |
| GET | `/api/accounts/{id}/transactions` | An account's transaction history, paginated, newest first |
| GET | `/api/accounts/top-by-transactions` | Top 5 accounts by transaction count |
| POST | `/api/transfers` | Transfer money between two accounts (requires `Idempotency-Key`) |

---

## How did you design your API — why these paths, methods, and status codes?

I went with plain REST-ish resource nouns: `/api/users`,
`/api/accounts`, `/api/transfers`. A transfer gets its own top-level resource instead of living
under `/api/accounts/{id}/transfer`, because a transfer genuinely involves *two* accounts as equal
participants — nesting it under one of them would make that one account look more important than the
other, which isn't true.

Methods follow their usual meaning: `GET` for reads, `POST` for creating a user/account/transfer,
`PATCH` for a partial user update (not `PUT`, since I never accept or expect a full replacement of
the resource — the client sends only the fields they want to change), `DELETE` for closing an
account.

Status codes - (different failures are distinguishable), not just "some 4xx". The rule I landed
on:

- **400** — the request itself is broken: failed validation, a malformed body, a path variable that
  isn't even a valid UUID, a missing required header, or a transfer targeting the same account twice.
  None of these need a database lookup to know they're wrong.
- **404** — the request is well-formed, but something it refers to (a user id, an account id) doesn't
  exist.
- **409** — the request conflicts with something that already happened: reusing an
  `Idempotency-Key` with different parameters, or a duplicate email/username on registration.
- **422** — the request is well-formed and everything it refers to exists, but the operation still
  can't be completed because of business state: insufficient balance, a currency mismatch, an account
  that isn't active. This is deliberately different from 400 — nothing about the *request* is wrong,
  the situation is.
- **500** — anything I didn't anticipate. Logged with a full stack trace server-side, never leaks
  internals to the client.

Every one of these goes through a single `GlobalExceptionHandler`, so the error body shape
(`timestamp`, `status`, `error`, `message`, `path`, `fieldErrors`) is identical no matter which of the
above fired.

## How did you guarantee that a transfer is atomic? Which mechanism did you use, where exactly did you apply it, and why there?

The whole debit → credit → record sequence lives inside one `@Transactional` method —
`TransferProcessor.process()`. Nothing about that method commits partially: if any line after the
first database write throws, Spring rolls the entire transaction back, and none of it ever becomes
visible to another connection.

That covers "all or nothing".  To Guarantee that -  "two transfers touching the same account at the same time
don't corrupt each other" — I take a pessimistic write lock (`SELECT ... FOR UPDATE`) on
both accounts before touching their balances, via a repository method:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select a from Account a where a.id = :id")
Optional<Account> findByIdForUpdate(@Param("id") UUID id);
```

Transfers use pessimistic write locking on the involved accounts. This ensures the balance is read only after the account is locked, so concurrent transfers 
cannot modify the balance during the validation and update process.

Accounts are locked in a deterministic order to prevent deadlocks when transfers occur simultaneously in opposite directions. This makes the insufficient-balance check reliable while keeping concurrent transfers safe.
The locking happens in `TransferProcessor.lockAccounts()`, always in a fixed order — whichever
account id is smaller (by `UUID.compareTo`) gets locked first, regardless of which one is actually the
"from" or "to" side of this particular request. That ordering is the part that prevents a deadlock. Locking by a fixed global order
means both transfers try to acquire locks in the same sequence, so one simply waits for the other to
finish instead of the two waiting on each other.

## Imagine an error occurs after the money has been debited but before it is credited. How do you know the debited amount went back to the account? Try this scenario and describe what happened.

I wrote a test that forces exactly this failure:
`TransferAtomicityIntegrationTest.transferRollsBackCompletelyWhenCreditingTheDestinationFails()`. It
spies on `AccountRepository` and makes `save()` throw a `RuntimeException`, but only when it's called
with the *destination* account — so the source account's debit goes through first, and the crash
happens right before the credit would be applied.

What actually happened when I ran it: the transfer request came back with a `500`, and when I fetched
both accounts fresh afterward, the source account's balance was completely unchanged — still the
original amount, not partially debited. Nothing about the failed attempt was left behind.

The reason this works is that JPA doesn't write anything to the database the instant `setBalance()` is
called in Java — it just marks the entity dirty within the current persistence context. The actual SQL
only gets flushed when the transaction commits (or on an explicit flush). Since the whole method is
one `@Transactional` unit and the exception happens before the method returns normally, Spring never
lets it commit at all — the debit that "happened" only ever existed in memory, inside a transaction
that was rolled back before touching disk for good.

## How did you protect the system against repeated requests?

The transfer endpoint requires an `Idempotency-Key` header. The very first thing `TransferService`
does with it is look for an existing `Transaction` row carrying that key:

- **No row exists** → this is genuinely new, proceed as normal.
- **A row exists, and its stored `fromAccountId`/`toAccountId`/`amount` match this request** → replay
  it. If the original succeeded, return that same transaction's response, without touching the
  processor or the accounts at all. If the original *failed*, re-throw that exact failure — same
  message, same status code — rather than re-running the validation. I stored the HTTP status
  alongside the failure reason specifically so a replayed failure is a byte-for-byte replay of what
  the client saw the first time, not a fresh (and possibly different) recomputation against
  whatever the account state happens to be now.
- **A row exists, but the parameters differ** → `409 Conflict`. Reusing a key for a materially
  different request is a client bug, not something to silently accept.

The application checks for an existing idempotency key, but the database also enforces a unique constraint on 
idempotency_key. This protects against two identical requests arriving at the same time: only one can succeed, so 
the transfer can never be processed twice. The losing request currently gets a generic 409, which could be improved later 
to return the original result.

## What could go wrong if two transfers run against the same account at the same time, and how would you address it?

The obvious danger is a lost update on the balance: both transactions read the same starting balance,
both compute a new value from it, and whichever one writes last wins — silently discarding the other's
change. If two transfers of 50 each hit an account with a balance of 100, without any protection
you could easily end up with a final balance of 50 instead of 0, because both reads happened before
either write.

A related, more dangerous version of this is overdrawing the account: two transfers can each check
"is there enough balance?" against the same stale read, both see "yes", and both proceed — even
though the account only actually has enough money for one of them.

The way I addressed this is the same pessimistic locking described above. Because the row lock is
acquired *before* the balance is read for the insufficient-funds check, the second transfer touching
that account has to wait until the first one's entire transaction — check, debit, credit, commit — is
completely finished before it can even read the balance. There's no window where two transfers are
looking at the same stale number.

I did actually test this rather than just claim it:
`TransferIntegrationTest.concurrentTransfersFromSameAccountNeverOverdraft()` fires five concurrent
transfer requests for the full balance from the same account, released at the same instant via a
`CountDownLatch`. Exactly one succeeds; the other four get `422` for insufficient balance; the
account's final balance is exactly zero, never negative.

## Walk us through the structure of your project: why these layers, why these classes? What would you change first if the project were to grow ten times larger?

```
controller/   HTTP concerns only — status codes, path/query binding, @Valid
service/      interfaces: what the application can do
service/impl/ the actual business logic
repository/   Spring Data JPA interfaces + a couple of Specifications
entity/       JPA entities
dto/          request/response shapes, kept separate from entities on purpose
mapper/       MapStruct interfaces, entity ↔ DTO translation
exception/    domain exceptions + the single GlobalExceptionHandler
enums/        AccountStatus, Currency, TransactionStatus
config/       OpenAPI metadata
```

The controller/service-interface/service-impl split is the standard reason: a controller shouldn't
know *how* a transfer happens, just that it can ask for one and get a response or an exception back.
Interfaces for the services exist mainly so a caller depends on a contract, not an implementation —
useful for testing, and it means the eventual "this needs to be reused somewhere else" doesn't require
touching the class that already works.

DTOs never leak entities out of the service layer, in either direction. Partly that's the usual
reason (I don't want `Account`'s JPA annotations or lazy `owner` relation dictating what the API looks
like), but it mattered concretely here too — `AccountServiceImpl.getAccount()` maps the entity to a
response *inside* the transactional method, specifically because `owner` is a lazy relation and
`open-in-view` is disabled. Map it after the transaction closes and it throws
`LazyInitializationException` instead of working.

`TransferService` doesn't do the transfer itself — it delegates to `TransferProcessor` for the atomic part, and to `TransactionRecorder` for
writing a `FAILED` row when something goes wrong. That split isn't just tidiness. `TransactionRecorder`
has to run in its *own*, fresh transaction, strictly *after* the processor's transaction has already
ended and released its locks. If I recorded the failure from inside a nested transaction while the
processor's transaction was still holding the pessimistic lock, the two could genuinely block on each
other — different database connections, each waiting on the other to finish before either can proceed
— which Spring's self-invocation rules make easy to get wrong if it's all one class. Splitting it into
three collaborators with one job each made the ordering explicit instead of implicit.

If this grew ten times larger, the first thing I'd change is `TransferProcessor`'s locking strategy
scaling — pessimistic locks are fine at the traffic this task implies, but under real load they turn
into a throughput ceiling on popular accounts. I'd look at whether the insufficient-funds check could
move to an optimistic-concurrency-plus-retry model for the common case, keeping pessimistic locking
only where genuine contention is expected. Second, I'd split read-heavy paths (history, top-5) onto a
read replica or a separate query model — they don't need the same consistency guarantees as the
transfer path and currently share the same connection pool and tables under load. Third, the
`account`/`user` domains would probably become separate modules (or services) before the `transfer`
domain does, since transfers are the part that actually needs the other two to be correct, not the
other way around.

## List every assumption you made where this document was silent

- **Transfers require matching currencies.** No FX conversion exists, so a transfer between a USD
  account and a EUR account is rejected (`422`) rather than silently doing something arbitrary.
- **Both accounts must be `ACTIVE`.** I added an `AccountStatus` enum (`ACTIVE`, `FROZEN`, `CLOSED`)
  since the spec didn't require it, but a closed or frozen account being able to send or receive money
  didn't make sense once the concept existed. There's currently no endpoint to move an account into
  `FROZEN` — it exists for future use, not because it's reachable yet.
- **Closing an account is a soft delete, not a row deletion.** `transactions` has foreign keys back to
  `accounts` with no cascade, so a hard delete would fail the moment an account had any transaction
  history — and even for a brand new account, a hard delete throws away an audit trail a bank
  shouldn't lose. Closing sets `deletedAt` and `status = CLOSED`; a `@SQLRestriction` on the entity
  makes closed accounts invisible to normal queries, so re-fetching a closed account returns the same
  `404` as one that never existed.
- **Account numbers are generated server-side**, not supplied by the client — the spec never says who
  picks it, and a client choosing its own account number risks collisions and isn't how real account
  numbers usually work.
- **`Account.owner` is a real relation to a `User` entity** (many accounts to one user), rather than
  the plain `ownerName` string the spec's minimal data model describes. The spec explicitly allows
  adding fields, and a first-class registered user made the "open an account for a user" requirement
  and the eventual owner-details-update endpoint meaningfully more real.
- **Failed transfers are journaled, but only for failures the system anticipates.** A
  `ResourceNotFoundException` or `TransferNotAllowedException` writes a `FAILED` transaction row (with
  the account side(s) that actually exist — nullable FKs on `Transaction` specifically support this).
  A validation failure that never reaches a real account (bad amount, self-transfer) doesn't create a
  row at all, since nothing was actually attempted against the system yet.
- **Idempotency-Key replay is exact, not re-validated.** A retried request that previously failed
  keeps failing the same way even if the account's balance has since changed — it replays the
  original outcome rather than re-running the checks. I think this is the more correct reading of "the
  result of the first attempt" in the spec, even though it means a client can't "fix" a failed
  transfer just by waiting and resending the identical request with the identical key — they need a
  new key.
- **Pagination and sort on transaction history**: `page` and `size` are client-controlled query
  parameters; sort is not. The spec requires "newest first" as a fixed property of the endpoint, not a
  client option, so the server always orders by `createdAt DESC` regardless of anything the client
  sends.
- **The top-5 endpoint counts only `SUCCESS` transactions.** A failed transfer attempt involving an
  account doesn't count toward that account's "activity" for ranking purposes.
- **User update (`PATCH`) is genuinely partial.** Omitted fields are left untouched rather than
  nulled out —  MapStruct's `NullValuePropertyMappingStrategy.IGNORE` now handles this at the mapping layer 
  instead of hand-written null checks.

# UC-005 · Tests

> Partial of `docs/use-cases/UC-005-record-kyc-verdict/`. Owner: `test-architect`.
> Reads `00-caso-de-uso.md`, `10-dominio.md`, `20-persistencia.md`, `25-mensageria.md`,
> `30-rest.md`. Contains no test code. Form references in
> `.claude/skills/test-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/testing.md`.

| Field | Value |
|---|---|
| HTTP contract cases | none new — `30-rest.md` § 5; `UC-003`'s not-found contract test stays as is |
| Test profile | every `@SpringBootTest` already carries `@ActiveProfiles("test")` — survey found none without it |
| Interview | none — every axis is fixed by the partials (scenarios: `00-caso-de-uso.md` § Status transitions and § Errors; no clock — the case records no time; concurrency: `20-persistencia.md` § 3; no external system — broker only) |

Paths relative to `src/test/java/dev/nerviz/bankapp/`.

## 1 · Distribution by level

| Behaviour | Level | Why there |
|---|---|---|
| Every row of the transition table (`00-caso-de-uso.md` § Status transitions) | domain unit — `CustomerTest` | aggregate invariant |
| Verdict → status mapping | domain unit — `KycVerdictTest` | enum behaviour |
| Command rejects `null` id / verdict | application unit — `RecordKycVerdictCommandTest` | compact constructor |
| Unknown id raises `CustomerNotFoundException`; conflict propagates; duplicate does not `save`; a move does `save` the next state | application unit — `RecordKycVerdictUseCaseTest`, `CustomerRepository` mocked | orchestration only |
| `GetCustomerUseCase` raises `CustomerNotFoundException` | application unit — `GetCustomerUseCaseTest` (CHANGE) | promotion, `10-dominio.md` § Impact |
| Payload → command mapping, `KYC_VERDICT_UNKNOWN`, ack only after the use case returns, no ack when it throws | adapter unit — `KycVerificationCompletedListenerTest`, use case and `Acknowledgment` mocked | adapter translation, no broker needed |
| `save` updates an existing customer twice in a row without false conflict; version moves; insert path still raises the security-number conflict | integration — `CustomerRepositoryJpaAdapterIT` (CHANGE) | SQL and `@Version` behaviour, real Postgres |
| Stale version raises `ConflictException` `CUSTOMER_CONCURRENT_UPDATE` | integration — `CustomerRepositoryJpaAdapterIT` | lock failure only exists against a real row |
| End to end through a real broker: approve, reject, duplicate, conflict → DLQ, unknown id → DLQ, malformed JSON → DLQ, unknown verdict → DLQ; transient retry is not end-to-end tested (see § 4) | integration — `KycVerdictKafkaIT` | conversion, error handler, recoverer, offsets are container behaviour |
| Error-handler classification: `DomainException` not retryable, `CUSTOMER_CONCURRENT_UPDATE` retryable | adapter unit — `KafkaListenerConfigTest` | the classifier is plain code; cheaper than forcing a lock failure through a broker |

No behaviour at two levels.

## 2 · Cases per test class

**`domain/model/CustomerTest`** — CHANGE, add:

| Method | Asserts |
|---|---|
| `recordsVerdictFromKycInProgress(KycVerdict, CustomerStatus)` — parameterized: `APPROVED`→`ACTIVE`, `REJECT`→`REJECTED_BY_KYC` | new instance, status moved, every other component equal |
| `sameVerdictOnFinalStatusIsNoOp(CustomerStatus, KycVerdict)` — parameterized: (`ACTIVE`,`APPROVED`), (`REJECTED_BY_KYC`,`REJECT`) | returns the **same** instance (`isSameAs`) |
| `oppositeVerdictOnFinalStatusConflicts(CustomerStatus, KycVerdict)` — parameterized: (`ACTIVE`,`REJECT`), (`REJECTED_BY_KYC`,`APPROVED`) | `ConflictException`, `errorCode` `KYC_VERDICT_CONFLICT` |
| `rejectsMissingVerdict` | `ValidationException`, `KYC_VERDICT_REQUIRED` |

**`domain/model/KycVerdictTest`** — NEW

| Method | Asserts |
|---|---|
| `mapsToResultingStatus(KycVerdict, CustomerStatus)` — parameterized over both constants | `APPROVED`→`ACTIVE`, `REJECT`→`REJECTED_BY_KYC` |

**`application/usecase/customer/RecordKycVerdictCommandTest`** — NEW

| Method | Asserts |
|---|---|
| `rejectsMissingCustomerId` | `ValidationException`, `CUSTOMER_ID_REQUIRED` |
| `rejectsMissingVerdict` | `ValidationException`, `KYC_VERDICT_REQUIRED` |

**`application/usecase/customer/RecordKycVerdictUseCaseTest`** — NEW

| Method | Asserts |
|---|---|
| `approvesCustomerInProgress` | `save` called once with status `ACTIVE` |
| `rejectsCustomerInProgress` | `save` called once with status `REJECTED_BY_KYC` |
| `duplicateVerdictWritesNothing` | customer already `ACTIVE`, verdict `APPROVED` → `save` never called |
| `conflictingVerdictWritesNothing` | `ConflictException` `KYC_VERDICT_CONFLICT`, `save` never called |
| `unknownCustomerIsNotFound` | `CustomerNotFoundException`, `errorCode` `CUSTOMER_NOT_FOUND`, `save` never called |

**`application/usecase/customer/GetCustomerUseCaseTest`** — CHANGE: the not-found test asserts
`CustomerNotFoundException` (type) and `CUSTOMER_NOT_FOUND`.

**`infrastructure/messaging/kycverificationcompleted/KycVerificationCompletedListenerTest`** — NEW

| Method | Asserts |
|---|---|
| `mapsPayloadAndAcknowledges(String, KycVerdict)` — parameterized: `"APPROVED"`, `"REJECT"` | use case called with the matching command; `ack.acknowledge()` after it |
| `rejectsUnknownVerdict(String)` — parameterized: `"approved"`, `"REJECTED"`, `""` | `ValidationException` `KYC_VERDICT_UNKNOWN`; use case and ack never called |
| `doesNotAcknowledgeWhenUseCaseThrows` | use case throws → exception propagates, ack never called |

**`infrastructure/messaging/listener/KafkaListenerConfigTest`** — NEW

| Method | Asserts |
|---|---|
| `domainExceptionsAreNotRetryable` | the error handler classifies `CustomerNotFoundException` and `ConflictException` `KYC_VERDICT_CONFLICT` as not retryable |
| `concurrentUpdateIsRetryable` | `ConflictException` `CUSTOMER_CONCURRENT_UPDATE` is retryable |

**`infrastructure/persistence/customer/CustomerRepositoryJpaAdapterIT`** — CHANGE, add:

| Method | Asserts |
|---|---|
| `updatesStatusOfExistingCustomer` | save → load → `recordKycVerdict` → save in a new transaction: row status `ACTIVE`, no exception |
| `updatesTwiceWithoutFalseConflict` | two successive updates in separate transactions both succeed (the `version = 0` defect of `20-persistencia.md` § 3) |
| `staleVersionIsConcurrentUpdateConflict` | two transactions load the same row, both update; the second gets `ConflictException` with `errorCode` `CUSTOMER_CONCURRENT_UPDATE` |
| existing security-number duplicate test | unchanged — still asserts `errorCode`, proving the insert path survived the branch |

**`infrastructure/messaging/kycverificationcompleted/KycVerdictKafkaIT`** — NEW. Same shape as
`OutboxRelayKafkaIT`: `@SpringBootTest`, `@Import({TestcontainersConfiguration.class, KafkaContainerConfiguration.class})`,
`@ActiveProfiles("test")`, `@TestPropertySource(properties = {"spring.kafka.admin.auto-create=true", "spring.kafka.listener.auto-startup=true"})`,
`@EnabledIf("dockerAvailable")`. Publishes JSON text with a plain `KafkaProducer<String,String>`,
key `customerId`; reads the DLQ with a plain `KafkaConsumer<String,String>`; waits with Awaitility.

| Method | Asserts |
|---|---|
| `approvedVerdictActivatesCustomer` | customer saved `KYC_IN_PROGRESS` → message → status `ACTIVE` |
| `rejectVerdictRejectsCustomer` | → `REJECTED_BY_KYC` |
| `duplicateVerdictKeepsStatusAndSkipsDlq` | same message twice → status `ACTIVE`, version moved once, DLQ empty for that key |
| `conflictingVerdictGoesToDlq` | `ACTIVE` customer + `REJECT` → status still `ACTIVE`; record on `….dlq` with the same value |
| `unknownCustomerGoesToDlq` | random id → record on `….dlq`; no row created |
| `malformedMessageGoesToDlq(String)` — parameterized: not JSON, unknown verdict | record on `….dlq`; status unchanged |

## 3 · Data and doubles

| Item | Value |
|---|---|
| Factory | REUSE `CustomerFixtures` — `customer()` (born `KYC_IN_PROGRESS` via `register`) and `withStatus(...)` for persisted final states. `withStatus(ACTIVE)` / `withStatus(REJECTED_BY_KYC)` model rows this case's transition (or `UC-004`'s backfill) now produces in production — not a fabricated state |
| Meaningful fields | `id` and `status` only; the rest comes from the factory |
| Doubles | `CustomerRepository` mocked in the use-case test; `RecordKycVerdictUseCase` and `Acknowledgment` mocked in the listener test. No double in the ITs — real Postgres, real broker |
| Clock | none — the case reads no time |
| Waiting | Awaitility, `atMost` 15 s, in `KycVerdictKafkaIT` only (`@.claude/rules/testing.md` § Asynchronous effects) — never `Thread.sleep` |
| Backoff in the IT | `KycVerdictKafkaIT` lowers nothing: business and malformed rejections skip retry, so the DLQ is reached without waiting the 7 s backoff |

## 4 · Coverage and gaps

Every invariant of `10-dominio.md` § 2 has a named test: #1–#3 `CustomerTest`, #4 `CustomerTest`
and `RecordKycVerdictCommandTest`, #5 `RecordKycVerdictCommandTest`, #6 `RecordKycVerdictUseCaseTest`.

Left uncovered on purpose:

- **Transient retry then DLQ end to end** — needs the database to fail mid-test; the
  classification is proven in `KafkaListenerConfigTest` and the backoff is Spring Kafka's.
- **DLQ counter value** — Micrometer counter increments are asserted in `KafkaListenerConfigTest`
  only if the recoverer is testable without a container; otherwise covered by the IT's DLQ record.

Coverage gate unchanged; `KafkaListenerConfig` and `KycVerificationCompletedTopicConfig` are
excluded by name, so `KafkaListenerConfigTest` is behaviour proof, not coverage.

## 5 · Test dependencies

none — `testcontainers-kafka`, `awaitility` and the Postgres Testcontainers module are already in
`pom.xml`; `KafkaContainerConfiguration` (`apache/kafka:4.1.0`) is REUSE.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-003-get-customer` | `GetCustomerUseCaseTest` not-found case asserts `CustomerNotFoundException` | exception promotion, `10-dominio.md` § Impact |
| `UC-001-create-customer` | `CustomerRepositoryJpaAdapterIT` gains the update-path cases | `save` branches insert/update, `20-persistencia.md` § 3 |

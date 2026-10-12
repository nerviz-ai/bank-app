# UC-005 · Messaging

> Partial of `docs/use-cases/UC-005-record-kyc-verdict/`. Owner: `messaging-architect`.
> Inherits the incoming message from `10-dominio.md` § 4 — doesn't redefine its business meaning.
> Contains no code. Form references in
> `.claude/skills/messaging-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/messaging.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| Event | `KycVerificationCompleted` — the KYC verdict, **consumed**; published by the KYC application on a contract this project defines |
| Broker | Kafka |
| Divergences from `10-dominio.md` / `00-caso-de-uso.md` | **1.** A business rejection (unknown customer, conflicting verdict) goes to the DLQ with no retry, not "discarded" — user's answer in this pass, which aligns with `@.claude/rules/messaging.md` § Retry and DLQ; the status still stays unchanged. **2.** Requirement to `10-dominio.md`: `errorCode` `KYC_VERDICT_UNKNOWN` (`ValidationException`) for a verdict string outside `APPROVED` / `REJECT`, raised by the listener's mapping — § 3 |

Package base: `dev.nerviz.bankapp`. Paths below are relative to
`src/main/java/dev/nerviz/bankapp/`.

## 1 · Topic and delivery

| Property | Value | Why |
|---|---|---|
| Topic | `bank-app.customer.kyc-verification-completed` | `<bounded-context>.<aggregate>.<event>`; bounded context `bank-app` from the root `CLAUDE.md`. This project defines the contract (`00-caso-de-uso.md`), so the name follows this project's rule, mirroring `bank-app.customer.kyc-verification-requested` |
| Partition key | `customerId` (string form of the UUID) — **a contract obligation of the publisher** | Every verdict of one customer lands in one partition, in order; with one consumer per partition, two verdicts for the same customer are never applied concurrently |
| Partitions / replicas | `3` / `${KAFKA_TOPIC_REPLICAS:1}` — same as the request topic | Declared as a `NewTopic` here because this project owns the contract; production topic provisioning follows the same values |
| Serialization | JSON text — `StringDeserializer` | No build-time contract validation asked for by either side — rule default; no schema registry |
| Converted to JSON by | the KYC application (outside this project) | — |
| Converted from JSON by | one `StringJacksonJsonMessageConverter` bean, into the listener's typed parameter | One bean for every listener; the rule's wire contract |
| Delivery | At-least-once + idempotent consumer (§ 3) | Rule default |

## 2 · Producer adapter

none — this case publishes nothing (`10-dominio.md` § 4: no outbound event). Publication form
not applicable.

**Payload contract** — the message the KYC application publishes, defined here (user's answer:
this project defines it). Handed to the KYC application as is.

| Field | JSON type | Required | Meaning |
|---|---|---|---|
| `eventId` | string, UUID | ✅ | the message's own identity — logged and carried to the DLQ for tracing; not the dedupe key (§ 3) |
| `customerId` | string, UUID | ✅ | the `customerId` received in `bank-app.customer.kyc-verification-requested` |
| `verdict` | string | ✅ | `APPROVED` or `REJECT` |

Record key: `customerId`. No headers required. Adapter-local record:
`infrastructure/messaging/kycverificationcompleted/KycVerificationCompletedPayload(UUID eventId, UUID customerId, String verdict)`.

**Personal data in the payload** — see § 8: none.

## 3 · Consumer adapter and idempotency

| Consuming UC | Listener | Group id | Dedupe key |
|---|---|---|---|
| `RecordKycVerdictUseCase` | `infrastructure/messaging/kycverificationcompleted/KycVerificationCompletedListener` | `bank-app.kyc-verdict` | none — **state comparison** (user's answer) |

Listener, package-private, per `templates/KafkaConsumerAdapter.java.example` minus the dedupe
store:

1. receives `KycVerificationCompletedPayload` (converted by the shared converter; unparseable
   JSON fails conversion → DLQ, § 4);
2. maps `verdict`: `"APPROVED"` → `KycVerdict.APPROVED`, `"REJECT"` → `KycVerdict.REJECT`,
   `null` → passed as `null` (the command raises `KYC_VERDICT_REQUIRED`), anything else →
   `ValidationException` `KYC_VERDICT_UNKNOWN` (requirement to `10-dominio.md`, header row);
3. `recordKycVerdict.record(new RecordKycVerdictCommand(payload.customerId(), verdict))`;
4. `ack.acknowledge()` — only after the use case returns.

No branching on payload content beyond that mapping; no aggregate touched directly.

**Why no dedupe table.** The only effect is moving a customer out of `KYC_IN_PROGRESS` into a
final status. A redelivered verdict finds the customer already in that status, and
`Customer.recordKycVerdict` returns it unchanged — nothing is written (`10-dominio.md` § 1 and
§ 3). The end state of the redelivery equals the end state of the first delivery, which is the
guarantee `@.claude/rules/messaging.md` § Delivery semantics asks of the dedupe. The identity
deduped on is the aggregate id plus the verdict, carried by the state itself. A
`processed_events` table becomes necessary only with a consumer whose effect is not naturally
idempotent — that consumer's case adds it.

**Who absorbs the duplicate**

| Consumer | Where it lives | Idempotency | Evidence |
|---|---|---|---|
| `RecordKycVerdictUseCase` | this project | guaranteed | `Customer.recordKycVerdict` returns `this` for a verdict already applied; the use case saves only on a status change (`10-dominio.md` § 3) |

## 4 · Retry and DLQ

One `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` for the listener container factory,
in the shared listener wiring (§ 5).

| Failure | Handling |
|---|---|
| Transient (database unavailable, lock timeout, connection reset) | 3 retries, exponential backoff 1 s / 2 s / 4 s, then DLQ |
| Business rejection — `CustomerNotFoundException` (`CUSTOMER_NOT_FOUND`), `ConflictException` (`KYC_VERDICT_CONFLICT`) | **No retry** — straight to the DLQ (user's answer). Status unchanged |
| Malformed — unparseable JSON, `ValidationException` (`KYC_VERDICT_UNKNOWN`, `KYC_VERDICT_REQUIRED`, `CUSTOMER_ID_REQUIRED`) | No retry — straight to the DLQ |
| Retries exhausted | DLQ |

Not retryable: `DomainException` (the whole family) and the conversion failures the handler
already classifies as fatal.

| Property | Value |
|---|---|
| DLQ topic | `bank-app.customer.kyc-verification-completed.dlq` — `<topic>.dlq`; `NewTopic`, 3 partitions (the recoverer keeps the source partition), replicas `${KAFKA_TOPIC_REPLICAS:1}` |
| DLQ headers | the recoverer's default exception headers (class, message, stack) — the domain `errorCode` is in the message |
| Monitoring | every record sent to the DLQ: one `WARN` log line with `eventId`, `customerId`, the exception's `errorCode` (or class), and a Micrometer counter `kyc.verdict.dead.lettered` tagged `reason` (`errorCode`, or `TRANSIENT_EXHAUSTED`, or `MALFORMED`). This is the "monitored, not archived" of the rule |

## 5 · Configuration

Values decided for this project. Mandatory ones come from the rule — `@.claude/rules/messaging.md`
§ Configuration. Producer properties of `UC-004` unchanged; the DLQ recoverer reuses Boot's
`KafkaTemplate`, whose `StringSerializer` value serializer re-publishes the original JSON text
untouched.

| Property | Value | Why |
|---|---|---|
| `spring.kafka.consumer.group-id` | `bank-app.kyc-verdict` | One stable id per logical consumer |
| `spring.kafka.consumer.auto-offset-reset` | `earliest` | A lost verdict leaves a customer `KYC_IN_PROGRESS` forever; reprocessing an old one is a no-op (§ 3) |
| `spring.kafka.consumer.key-deserializer` / `value-deserializer` | `org.apache.kafka.common.serialization.StringDeserializer` both | JSON text on the wire |
| `spring.kafka.consumer.enable-auto-commit` | `false` | Offsets commit through the ack only |
| `spring.kafka.listener.ack-mode` | `manual` | Offset commits only after `record()` returns |
| `spring.kafka.listener.auto-startup` | `true` (main) · **`false` in `application-test.yml`** | The test profile starts no broker (`UC-004`); a listener container at context start would poll a broker that is not there. The Kafka IT that exercises the listener turns it back on |
| `spring.kafka.bootstrap-servers` | REUSE `${KAFKA_BOOTSTRAP_SERVERS:localhost:${KAFKA_EXTERNAL_PORT:29092}}` | Already host-first, `UC-004` |
| `app.kafka.topics.kyc-verification-completed.partitions` / `.replicas` | `3` / `${KAFKA_TOPIC_REPLICAS:1}` | Same binding shape as the request topic |

**Classes** (all package-private unless noted):

| Class | Package | Role |
|---|---|---|
| `KafkaListenerConfig` | `infrastructure/messaging/listener/` | shared listener wiring: the single `StringJacksonJsonMessageConverter` bean, the `DefaultErrorHandler` (backoff, not-retryable `DomainException`) with the `DeadLetterPublishingRecoverer` and the DLQ log + counter |
| `KycVerificationCompletedTopicProperties` | `infrastructure/messaging/kycverificationcompleted/` | binds `app.kafka.topics.kyc-verification-completed.*` |
| `KycVerificationCompletedTopicConfig` | `infrastructure/messaging/kycverificationcompleted/` | `NewTopic` for the topic and its DLQ; holds the topic-name constant the listener uses |
| `KycVerificationCompletedPayload` | `infrastructure/messaging/kycverificationcompleted/` | adapter-local record, § 2 |
| `KycVerificationCompletedListener` | `infrastructure/messaging/kycverificationcompleted/` | § 3 |

Coverage: `*Config` classes are excluded by name (root `CLAUDE.md`).

## 6 · Schema requirements

none — idempotency by state comparison (§ 3), no dedupe table; no outbox (no publication). The
listener's update of `customers.status` uses the existing column through `CustomerRepository`.

## 7 · Declared dependencies

none — `spring-boot-starter-kafka` and `testcontainers-kafka` are already in `pom.xml` (`UC-004`);
Micrometer arrives with the existing Actuator starter.

## 8 · Personal data

none — `eventId` and `customerId` are internal surrogate ids, `verdict` is a two-value outcome;
no field matches `@.claude/rules/logging.md` § Masking candidates by type or name. Nothing
personal crosses the boundary in either direction in this case. The DLQ copy carries the same
three fields.

## 9 · Deferred

none

## Compose services

| Service | Image | Decision | Detail |
|---|---|---|---|
| `kafka` | `apache/kafka:<tag>` — tag aligned with the Testcontainers Kafka image in `KafkaContainerConfiguration` | `materialize` | added by `/new-feature-implement` (user's answer). Single-node KRaft, EXTERNAL listener on `${KAFKA_EXTERNAL_PORT:-29092}`, the `app` service gets `KAFKA_BOOTSTRAP_SERVERS` with the internal listener. Also closes `UC-004`'s pending row |

## Design patterns

none — no force in the spec, no symptom on disk. This is the project's first listener; there is
no repeated listener skeleton to factor (`KafkaOutboxEventSender` is a producer). The verdict
mapping is a two-case translation, not a Strategy.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-004-initiate-kyc-verification` | `application.yml` gains `spring.kafka.consumer.*` and `spring.kafka.listener.*`; `application-test.yml` gains `spring.kafka.listener.auto-startup: false`; the `kafka` compose service `UC-004` left pending is materialized | first consumer in the project; same broker |

## Implementation order

1. `KafkaListenerConfig` (converter, error handler, recoverer)
2. `KycVerificationCompletedTopicProperties`, `KycVerificationCompletedTopicConfig`
3. `KycVerificationCompletedPayload` + `KycVerificationCompletedListener`
4. Properties in `application.yml` and `application-test.yml`
5. Integration test against a real broker (Testcontainers) — `40-testes.md`

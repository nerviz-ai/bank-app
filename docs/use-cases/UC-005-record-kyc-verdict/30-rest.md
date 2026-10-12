# UC-005 · REST adapter

> Partial of `docs/use-cases/UC-005-record-kyc-verdict/`. Owner: `rest-api-architect`.
> Inherits the canonical names from `00-caso-de-uso.md` and the ports from `10-dominio.md` —
> doesn't reinvent them.
> Rules applied, never reproduced: `@.claude/rules/api-rest.md`.

| Field | Value |
|---|---|
| Parent spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| Resource | none — the trigger is a Kafka message (`00-caso-de-uso.md` § Trigger); no HTTP exposure |
| Version prefix | not applicable |
| Divergences from `10-dominio.md` | none |

No endpoint is designed for this case. The mother spec fixes a messaging trigger and
`Access: not applicable`; `RecordKycVerdictUseCase` has no HTTP caller, and an endpoint with no
requested caller is a guessed contract (step 4). No question asked: every axis of the interview
has no subject without an endpoint. A manual "record a verdict" endpoint was not requested —
adding one would be a second trigger and a new `Access` decision.

## 1 · Endpoints

none.

## 2 · DTOs

none. Existing DTOs re-derived against `@.claude/rules/logging.md` § Masking candidates
(step 5, existing-DTO check): this case touches no endpoint, so no existing DTO is in scope.

## 3 · Error map

none new. `ValidationException` `KYC_VERDICT_REQUIRED` and `ConflictException`
`KYC_VERDICT_CONFLICT` (`10-dominio.md` § 2) never reach `ApiExceptionHandler`: they fire only
from the messaging trigger.

`CustomerNotFoundException` (promoted in `10-dominio.md` § 2) also reaches `GET` of one customer
(`UC-003`). It `extends NotFoundException` with the same `errorCode` `CUSTOMER_NOT_FOUND`, so
`ApiExceptionHandler`'s existing family mapping answers it unchanged — same status, same body,
same `GetCustomerOpenApiDocs`. No handler change.

## 4 · Pagination, idempotency, and dependencies

| Decision | Value |
|---|---|
| Pagination | none — no collection exposed |
| Idempotency | none on the transport — no `POST`. Consumer-side duplicate handling is `messaging-architect`'s, in `25-mensageria.md` |
| Dependencies | none |

**Schema requirements** — none.

## 5 · Contract test cases

none — no endpoint. The existing `UC-003` not-found contract test keeps asserting the same
status and `errorCode`; it now exercises `CustomerNotFoundException`.

## 6 · Personal data

none — no response body leaves the process.

## Design patterns

none — no controller, mapper or handler is added.

## Impact on approved use cases

none — the `UC-003` exception promotion is `10-dominio.md`'s row; on the transport it changes
nothing observable.

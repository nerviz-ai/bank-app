# Use case backlog

Use cases a split left for later, in dependency order. Each row carries a **`BL-NN`**, its own
identifier, assigned when the row is appended and never reused. **It is not a `UC` number and
never becomes one** — the two sequences are independent, which is what lets a row be dropped or
merged without leaving a hole in the `UC` sequence. No slug either: both the number and the slug
are fixed only when the case is designed.

| Id | Depends on | Description for `/new-feature` | Split from |
|---|---|---|---|
| `BL-02` | `UC-001-create-customer` | REST endpoint that deletes or anonymizes one customer by id in the `customers` table, removing `security_number` and `birth_date`. Unknown id is not found | `UC-001-create-customer` — deferred by `20-persistencia.md` |

## Retired

A row leaves the table above when its `/new-feature` starts and the real `UC-NNN` is assigned.
It lands here instead of being deleted: a spec that cited `BL-NN` is immutable once approved, so
the citation has to stay resolvable exactly at the moment the case stops being backlog.

| Id | Became | Date |
|---|---|---|
| `BL-01` | `UC-002-prune-expired-idempotency-keys` | 2026-10-08 |

# Task B — Execution plan

Build a small **Java + Spring Boot** HTTP API for Navan commerce transactions (flight, hotel, rail, `navan_fee`) and **frozen invoice snapshots**. The API must run against a **real file-backed database**, reject duplicate `external_id`s under concurrency, keep invoiced money immutable, and stay cheap to list at volume.

---

## 1. What already exists

The starter repo is **not** a runnable service. It contains seed payloads and an expected trip invoice (`grand_total` **685.70 EUR**) under `fixtures/task-b/`.

There is no Spring project, schema, or tests yet.

**Out of scope (do not build):** receipt OCR, auto-itemize, receipt upload, inventory, auth, UI, PDF, payments, GDS, Redis, Kafka, Prometheus, nginx, LLM-generated invoice lines.

---

## 2. Stack

| Choice | Decision | Why |
|---|---|---|
| Language | Java 17 (runs on 17+) | Spring Boot 3; available locally |
| Framework | Spring Boot 3, Maven Wrapper | Standard seams: controller → service → repository |
| Database | **H2 file-backed** `jdbc:h2:file:./data/travel` | Real unique indexes and ACID writes. In-memory H2 is a fail. SQLite is allowed but fights Hibernate. |
| Persistence | Spring Data JPA + Flyway | Schema and indexes live in versioned migrations, not “Hibernate auto-create in prod” |
| Money | `BigDecimal` + DB `DECIMAL(19,2)` | Scale **2**, rounding `HALF_UP`. No `float`/`double` in stored totals or rollups. |
| IDs | Prefixed UUIDs: `txn_…`, `inv_…` | Stable public ids, not `len(map)+1` |
| Tests | `spring-boot-starter-test` hitting HTTP | Required cases must go through the API |

Run commands (to put in README later):

- API: `./mvnw spring-boot:run`
- Tests: `./mvnw test`

---

## 3. Package seams

Reviewers must be able to follow the code without a god-class.

```
com.navan.travel
  api/                 HTTP controllers, request/response DTOs, error mapping
  transaction/         create / get / patch / delete, duplicate identity
  invoice/             snapshot create / rebuild, tax+fee rollup
  common/              money, pagination, domain exceptions
```

- Controllers: validate HTTP shape, map status codes, no uniqueness logic.
- Transaction service: persist header + taxes + fees + type-specific rows atomically; enforce duplicate `external_id`.
- Invoice service: load live txns, copy snapshots, roll up, freeze; block mutating billed transactions.

---

## 4. Data model

### 5.1 Live transactions

```
transactions
  id                  PK
  external_id         UNIQUE NOT NULL
  type                flight | hotel | rail | navan_fee
  currency            NOT NULL
  occurred_at         NOT NULL
  total               DECIMAL(19,2) NOT NULL
  created_at, updated_at
  version             optimistic lock

transaction_tax_lines
  id, transaction_id FK, name, rate NULL, amount DECIMAL(19,2), position

transaction_fee_lines
  id, transaction_id FK, name, amount DECIMAL(19,2), position

flight_details          (1:1, transaction_id PK/FK)
  origin, destination, fare DECIMAL(19,2)

flight_coupons
  transaction_id FK, flight_number, carrier NULL, from_code, to_code,
  departure, arrival, position

hotel_details           (1:1)
  name, street, city, country, check_in, check_out, nights,
  room_rate_per_night DECIMAL(19,2)

hotel_extras            optional children (name, amount)

rail_legs
  transaction_id FK, origin, destination, departure, arrival,
  train_number NULL, class NULL, position

navan_fee_details       (1:1)
  fee_kind              trip_fee | agent_call_fee
  description NULL
  related_external_id NULL
```

Type-specific data is **real tables**, not one untyped `varchar metadata`. GET still returns the fixture JSON shape (`metadata` object with coupons / address / legs / `fee_kind`).

Header + tax lines + fee lines + type rows commit in **one database transaction**. Failure rolls everything back.

### 5.2 Frozen invoices

```
invoices
  id, currency, grand_total DECIMAL(19,2), created_at, updated_at, version

invoice_lines
  id, invoice_id FK
  transaction_id        INDEXED (not unique) — “is this txn on an invoice?”
  type, external_id, currency, occurred_at, total
  metadata_snapshot     JSON — frozen copy of type-specific metadata

invoice_line_tax_lines
invoice_line_fee_lines

invoice_tax_rollups     grouped at write time by (name, rate)
invoice_fee_rollups     grouped at write time by name
```

`metadata_snapshot` JSON on the **invoice line** is acceptable because it is a freeze of already-validated structure, not the live query model. Taxes and fees stay as child rows so rollups are explicit.

Later `PATCH` of a flight must not change any existing invoice line, tax, fee, or rollup.

### 5.3 Indexes (volume)

Must exist in the schema, not only in comments:

- Unique index on `transactions.external_id`
- Primary keys on `transactions.id` and `invoices.id`
- Index on `invoice_lines.transaction_id`
- List queries: `ORDER BY created_at, id LIMIT ? OFFSET ?`

Do not `SELECT *` unbounded. Do not load all rows into a Java `List` and then slice.

---

## 5. API contract

### Shared error mapping

| Situation | Status |
|---|---|
| Validation / unknown type / unknown `fee_kind` / bad money / `limit > 100` | 400 |
| Missing transaction or invoice | 404 |
| Duplicate `external_id`; mixed currency; duplicate ids on one invoice; mutate/delete invoiced txn | 409 |

Error body includes a machine-readable `code` and human `message`. Duplicate create also includes `existing_id` and the stored transaction.

### `POST /transactions`

Required fields: `type`, `external_id`, `currency`, `occurred_at`, `total`, `tax_lines`, `fee_lines`, `metadata`.

`metadata` by type (minimum):

| type | required metadata |
|---|---|
| `flight` | `origin`, `destination`, `fare`, `coupons[]` with `flight_number`, `from`, `to`, `departure`, `arrival`; `carrier` optional |
| `hotel` | `name`, `address.{street,city,country}`, `check_in`, `check_out`, `nights`, `room_rate_per_night`; `extras` optional |
| `rail` | `legs[]` with `origin`, `destination`, `departure`, `arrival`; `train_number` / `class` optional |
| `navan_fee` | `fee_kind` ∈ `{trip_fee, agent_call_fee}`; `description`, `related_external_id` optional |

Response: stored transaction including server `id`.

### Identity, concurrency, money

1. Insert.
2. If unique constraint on `external_id` fires, `SELECT` by `external_id` and return **409** with that `id`.
3. Concurrent overlapping POSTs: the unique index is the source of truth. No Redis, no application-level lock service.
4. Never pre-check then insert without handling the race.

### `GET /transactions/{id}`

Full record: `type`, `metadata`, `tax_lines`, `fee_lines`. **404** if missing.

### `GET /transactions`

Query: `limit` (default 50, max 100), `offset` (default 0). `limit > 100` → **400**. SQL `LIMIT`/`OFFSET` only.

### `PATCH /transactions/{id}`

Updatable: `total`, `tax_lines`, `fee_lines`, `metadata`, `occurred_at` (and `currency` only if not invoiced — prefer leaving currency patchable the same way; if invoiced, 409 anyway).

If `invoice_lines` contains this `transaction_id`: **409**, persist nothing. Otherwise replace children atomically.

### `DELETE /transactions/{id}`

**404** if missing. If invoiced: **409**, delete nothing. Else delete header + children in one tx.

### `POST /invoices`

Support **both** bodies:

```json
{ "transaction_id": "txn_123" }
{ "transaction_ids": ["txn_123", "txn_456"] }
```

Rules:

- Any missing id → **404**
- Same id twice in the list → **409**
- Mixed currencies → **409**
- Copy type, totals, tax_lines, fee_lines, metadata onto snapshot rows
- Totals = sum of included snapshot totals
- Tax rollup: sum `amount` grouped by `(name, rate)` (`rate` null is its own bucket)
- Fee rollup: sum `amount` grouped by `name`
- Seed trip (flight + hotel + rail + trip_fee + agent_call_fee) grand total **685.70 EUR**

### `GET /invoices/{id}`

Header, included snapshots, tax rollup, fee rollup, totals.

### `GET /invoices`

Same pagination as transactions.

### `PATCH /invoices/{id}`

Replace the set of `transaction_ids` (same body shape as create). Rebuild snapshot from **current** live transactions in one atomic write. Mixed currency or duplicate ids → **409**, invoice **unchanged**. Missing invoice or txn → **404**.

### `DELETE /invoices/{id}`

Delete invoice + snapshot children. **Do not** delete underlying transactions.

### `GET /health`

`200` with a simple `{ "status": "ok" }`.

---

## 6. Idempotency, concurrency, edge cases

### Duplicate identity

- Unique DB constraint on `external_id`.
- Sequential double POST → one row, second **409** with existing `id`.
- Concurrent double POST → one row; both responses are 201 or 409, same `id`.
- Fixture `duplicate_flight` uses the same `external_id` as `flight` (even with empty coupons in the duplicate body). Identity is `external_id`, not payload equality.

### Invoice freeze

- Snapshot is copied at invoice write time.
- GET invoice never reads live transaction totals/metadata.
- PATCH/DELETE transaction checks indexed `invoice_lines.transaction_id` first.

### Atomic writes

- Create/update transaction: one Spring `@Transactional` covering header + tax + fee + type tables. Replace lists via delete-insert inside that tx (no torn `tax_lines`).
- Create/rebuild invoice: lock invoice row via `@Version`, replace snapshot children, write rollups, commit once.

### Concurrency on the same invoice/txn id

- Optimistic lock on `version`.
- Lost update may 409 on version mismatch or last-write-wins after retry-once; **torn lists are not acceptable**.
- SQLite-style advisory locks / Redis are not used.

### Edge cases to handle in code and tests

| Case | Result |
|---|---|
| Missing required field | 400 |
| Unknown `type` | 400 |
| Unknown `fee_kind` | 400 |
| Flight with zero coupons / rail with zero legs | 400 |
| Hotel without address parts | 400 |
| Empty dummy metadata | 400, not stored |
| `tax_lines`/`fee_lines` omitted | 400 |
| `tax_lines`/`fee_lines` `[]` | 200/201 |
| Negative money | 400 |
| Invalid `occurred_at` | 400 |
| Duplicate `external_id` | 409 + existing id |
| GET unknown id | 404 |
| Invoice with unknown txn id | 404 |
| Duplicate txn id in invoice body | 409 |
| EUR + USD on one invoice | 409 |
| PATCH invoiced transaction | 409, snapshot unchanged |
| DELETE invoiced transaction | 409, row remains |
| DELETE invoice | invoice gone, txns remain |
| PATCH invoice mixed currency | 409, previous snapshot intact |
| `GET /transactions` no `limit` | default 50 |
| `limit=1000` | 400 |
| Concurrent duplicate create | one row |

---

## 7. Tests

HTTP-level tests. One command: `./mvnw test`.

Use `fixtures/task-b/seed-payloads.json` and `expected-invoice.json`.

1. **Create all types** — flight, hotel, rail, `trip_fee`, `agent_call_fee`. GET returns coupons / hotel name+address / rail legs / `fee_kind` (not empty metadata).
2. **Duplicate `external_id`** — second POST does not insert a second row; 409 with existing `id`.
3. **Trip invoice** — all five ids; totals match fixture (`flight` 220.00, `hotel` 331.70, `rail` 94.00, `trip_fee` 25.00, `agent_call_fee` 15.00, **grand_total 685.70**). Tax/fee rollups match the example grouping.
4. **Snapshot** — after invoice exists, change stored flight `total`; GET invoice still shows flight contribution **220.00**.
5. **Mixed currency** — EUR + USD → 409.
6. **Concurrent duplicate create** — two overlapping POSTs, same `external_id`, exactly one row (`CountDownLatch` + thread pool). Unique-constraint handling is the mechanism.
7. **PATCH transaction on an invoice** → 409; invoice totals unchanged.
8. **DELETE transaction on an invoice** → 409; GET still returns the txn.
9. **DELETE invoice** — invoice 404 thereafter; transactions still GET.
10. **List pagination** — default page size 50; `limit=1000` is 400.

Extra (recommended, cheap): unknown type 400; duplicate ids on invoice 409; PATCH invoice mixed currency leaves old snapshot.

---

## 8. Runbook and design notes

Ship a short README with:

- One command to run the API
- One command to run tests
- How money scale works (`DECIMAL(19,2)`)
- Duplicate create policy (409)
- Duplicate invoice line policy (409)
- Example curls:
  - create each type (flight, hotel, rail, both Navan fees)
  - POST same `external_id` twice (only one row)
  - get / list transactions
  - patch / delete transaction, including 409 when invoiced
  - create invoice by id and by id list
  - get / list invoices
  - patch / delete invoice

Ship a one-page `ARCHITECTURE.md` covering:

- `external_id` uniqueness and concurrent POSTs
- Invoice snapshot vs live join
- Tables/models: taxes vs fees; flight coupons/OD/flight number; hotel name+address; rail legs; `navan_fee` trip vs agent-call
- **High volume:** indexes, SQL `LIMIT`, what breaks first under concurrent PATCH + duplicate POST + large lists (unbounded lists, then unique-constraint contention, then “is invoiced?” lookups) and what we did about it

Do not commit API keys. No vendor LLM at runtime.

---

## 9. Implementation slices

Implement in this order. Each slice should be runnable and tested before the next.

| Slice | Deliverable | Done when |
|---|---|---|
| 1. Scaffold | Maven, Spring Boot, Flyway, H2 file, health | `./mvnw test` runs; `GET /health` works |
| 2. Transactions write/read | Schema + `POST`/`GET` by id + typed metadata + unique `external_id` | Seed payloads persist and round-trip; duplicate POST 409 |
| 3. Invoices | `POST`/`GET` snapshot + rollup | Trip invoice totals 685.70; mixed currency 409 |
| 4. Mutate / freeze | PATCH/DELETE txn and invoice | Invoiced txn 409; snapshot test; delete invoice keeps txns |
| 5. Lists | Paginated GETs + indexes | Default 50; `limit=1000` 400; SQL bounded |
| 6. Concurrency + extras | Race test, remaining required tests | All 10 required tests green |
| 7. Docs | README + ARCHITECTURE | Curls and high-volume section present |

---

## 10. Verification before calling the work done

- `./mvnw test` — all required cases pass
- Manual curls against `./mvnw spring-boot:run` for create, duplicate, invoice, patch-after-invoice, list cap
- Confirm H2 file exists on disk (not `jdbc:h2:mem:`)
- Confirm unique index and `invoice_lines.transaction_id` index in Flyway
- Confirm no in-memory maps as source of truth

---

## 11. Handoff (later, when asked)

1. Git repo URL + commit SHA
2. README with run/test commands and curls
3. `ARCHITECTURE.md` as outlined above

A correct, tested create API + duplicate rejection + snapshot invoice is the quality bar — not an unfinished billing platform.

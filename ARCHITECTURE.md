# Architecture

## Uniqueness and concurrent POSTs

`transactions.external_id` has a **unique index**. Create always inserts; it does not “check then insert.” If two overlapping `POST /transactions` share an `external_id`, one insert commits and the other hits the unique constraint. The failed write is mapped to **409** with `existing_id` and the stored resource. There is no Redis or lock service.

## Invoice snapshot

`POST /invoices` (and `PATCH /invoices/{id}`) copies type, totals, tax lines, fee lines, and type-specific metadata onto `invoice_lines` and child snapshot tables, then writes tax/fee **rollups** grouped by `(name, rate)` and `name`. `GET /invoices/{id}` reads only those snapshot rows. It never joins live `transactions`. Later edits to a billed trip cannot change an existing invoice.

If a transaction appears on any invoice line (`idx_invoice_lines_transaction_id`), `PATCH`/`DELETE` of that transaction return **409** and write nothing. Deleting an invoice removes snapshot rows only.

A transaction may appear on more than one invoice; each invoice holds its own copy.

## Tables and models

**Shared:** `transactions` header (`id`, unique `external_id`, `type`, `currency`, `occurred_at`, `total DECIMAL(19,2)`). Taxes and fees are separate child tables (`transaction_tax_lines` with optional `rate`, `transaction_fee_lines`). They are not mashed into one extras column.

**Type-specific (real tables, not an untyped metadata varchar):**

- Flight: `flight_details` (origin, destination, fare) + `flight_coupons` (`flight_number`, carrier, from/to, departure/arrival)
- Hotel: `hotel_details` (property `name`, address street/city/country, stay dates, nights, room rate) + optional `hotel_extras`
- Rail: `rail_legs` (a change of train is two rows)
- Navan fee: `navan_fee_details` with `fee_kind` `trip_fee` or `agent_call_fee`

GET still returns a `metadata` object so clients round-trip the same JSON shape. Invoice lines store a frozen `metadata_snapshot` JSON blob plus copied tax/fee rows.

Writes of header + taxes + fees + type rows happen in one database transaction. List replacements are delete-insert inside that transaction so lists are never torn. Invoice rebuild clears snapshot children and inserts the new set in one transaction. Optimistic `@Version` covers overlapping PATCH/DELETE of the same id.

## High volume

List endpoints require `limit` (default 50, max 100) and `offset`. Queries use SQL `ORDER BY created_at, id LIMIT ? OFFSET ?`. The application never loads the whole table to paginate in memory. `limit > 100` is **400**.

Indexes: unique `external_id`; PK lookups on transaction and invoice ids; `invoice_lines.transaction_id` for “is this billed?”.

What breaks first under concurrent PATCH + duplicate POST + large lists:

1. Unbounded `GET /transactions` would scan and serialize the whole table — prevented by required `LIMIT`.
2. Duplicate-create storms contend on the unique `external_id` index — one winner, others 409; the index is the bottleneck and the correctness mechanism.
3. “Already invoiced?” checks stay index lookups rather than invoice-table scans.

File-backed H2 is enough for this service. Kafka/Redis/Prometheus are not substitutes for constraints, pagination, and snapshot tables.

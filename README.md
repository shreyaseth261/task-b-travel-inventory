# Travel inventory API

HTTP API for flight, hotel, rail, and Navan fee transactions, plus frozen invoices. Data lives in a **file-backed H2** database (`./data/travel.mv.db`), not in memory.

Money is stored as `DECIMAL(19,2)` / `BigDecimal` with scale **2** and `HALF_UP`. Duplicate `external_id` creates return **409** with the existing `id`. Duplicate transaction ids on one invoice return **409** (no silent dedupe, no double-count).

## Run

```bash
./mvnw spring-boot:run
```

API: `http://localhost:8080`

## Test

```bash
./mvnw test
```

## Example curls

Create each type (bodies match `fixtures/task-b/seed-payloads.json`):

```bash
curl -sS -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d @- <<'EOF'
{"type":"flight","external_id":"flight-TXL-LHR-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T06:40:00Z","total":220.0,"tax_lines":[{"name":"Airport tax","amount":15.0}],"fee_lines":[{"name":"YQ","amount":25.0}],"metadata":{"origin":"TXL","destination":"LHR","fare":180.0,"coupons":[{"carrier":"BA","flight_number":"BA982","from":"TXL","to":"LHR","departure":"2026-03-11T07:00:00Z","arrival":"2026-03-11T08:20:00Z"}]}}
EOF

curl -sS -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d @- <<'EOF'
{"type":"hotel","external_id":"hotel-LON-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T15:00:00Z","total":331.7,"tax_lines":[{"name":"VAT","rate":0.07,"amount":21.7}],"fee_lines":[],"metadata":{"name":"Hotel Mitte London","address":{"street":"12 Kingsway","city":"London","country":"GB"},"check_in":"2026-03-11","check_out":"2026-03-13","nights":2,"room_rate_per_night":140.0,"extras":[{"name":"Breakfast","amount":30.0}]}}
EOF

curl -sS -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d @- <<'EOF'
{"type":"rail","external_id":"rail-STP-PAR-2026-03-13","currency":"EUR","occurred_at":"2026-03-13T09:00:00Z","total":94.0,"tax_lines":[],"fee_lines":[{"name":"Booking fee","amount":4.0}],"metadata":{"legs":[{"origin":"London St Pancras","destination":"Lille Europe","departure":"2026-03-13T09:00:00Z","arrival":"2026-03-13T10:30:00Z","train_number":"EST9108","class":"standard"},{"origin":"Lille Europe","destination":"Paris Nord","departure":"2026-03-13T11:00:00Z","arrival":"2026-03-13T12:05:00Z","train_number":"EST9122","class":"standard"}]}}
EOF

curl -sS -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d @- <<'EOF'
{"type":"navan_fee","external_id":"navan-trip-fee-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T06:40:00Z","total":25.0,"tax_lines":[],"fee_lines":[],"metadata":{"fee_kind":"trip_fee","description":"Navan trip fee","related_external_id":"flight-TXL-LHR-2026-03-11"}}
EOF

curl -sS -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d @- <<'EOF'
{"type":"navan_fee","external_id":"navan-agent-call-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T18:00:00Z","total":15.0,"tax_lines":[],"fee_lines":[],"metadata":{"fee_kind":"agent_call_fee","description":"Agent fee for support call","related_external_id":"hotel-LON-2026-03-11"}}
EOF
```

Duplicate create (second POST is **409**, still one row):

```bash
curl -sS -o /tmp/t1.json -w '%{http_code}\n' -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d @- <<'EOF'
{"type":"flight","external_id":"flight-TXL-LHR-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T06:40:00Z","total":220.0,"tax_lines":[{"name":"Airport tax","amount":15.0}],"fee_lines":[{"name":"YQ","amount":25.0}],"metadata":{"origin":"TXL","destination":"LHR","fare":180.0,"coupons":[{"carrier":"BA","flight_number":"BA982","from":"TXL","to":"LHR","departure":"2026-03-11T07:00:00Z","arrival":"2026-03-11T08:20:00Z"}]}}
EOF
curl -sS -w '\n%{http_code}\n' -X POST http://localhost:8080/transactions -H 'Content-Type: application/json' -d @- <<'EOF'
{"type":"flight","external_id":"flight-TXL-LHR-2026-03-11","currency":"EUR","occurred_at":"2026-03-11T06:40:00Z","total":220.0,"tax_lines":[{"name":"Airport tax","amount":15.0}],"fee_lines":[{"name":"YQ","amount":25.0}],"metadata":{"origin":"TXL","destination":"LHR","fare":180.0,"coupons":[]}}
EOF
```

Get / list transactions (`limit` default 50, max 100; `limit=1000` is 400):

```bash
curl -sS http://localhost:8080/transactions/TXN_ID
curl -sS 'http://localhost:8080/transactions?limit=50&offset=0'
```

Patch / delete a transaction. If it is on any invoice, both return **409** and persist nothing:

```bash
curl -sS -X PATCH http://localhost:8080/transactions/TXN_ID -H 'Content-Type: application/json' -d '{"total":10.00}'
curl -sS -X DELETE http://localhost:8080/transactions/TXN_ID
```

Create / get / list / patch / delete invoices:

```bash
curl -sS -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' -d '{"transaction_id":"TXN_ID"}'
curl -sS -X POST http://localhost:8080/invoices -H 'Content-Type: application/json' -d '{"transaction_ids":["FLIGHT_ID","HOTEL_ID","RAIL_ID","TRIP_FEE_ID","AGENT_FEE_ID"]}'
curl -sS http://localhost:8080/invoices/INV_ID
curl -sS 'http://localhost:8080/invoices?limit=50&offset=0'
curl -sS -X PATCH http://localhost:8080/invoices/INV_ID -H 'Content-Type: application/json' -d '{"transaction_ids":["TXN_ID"]}'
curl -sS -X DELETE http://localhost:8080/invoices/INV_ID
```

Deleting an invoice does not delete the underlying transactions.

```bash
curl -sS http://localhost:8080/health
```

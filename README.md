# Duck Warehouse

A warehouse for rubber ducks, built with React 19/TypeScript, Spring Boot 4.1.1/Java 25, and MongoDB 7.0.28. The warehouse has a UI; order pricing is a backend-only API.

## Run locally

Prerequisites: JDK 25, Node.js 22.13+ within the 22.x release line (or Node 24), npm, and Docker with Docker Compose. Set `JAVA_HOME` to your JDK 25 installation; Maven may otherwise select a different installed JDK. The Maven Wrapper downloads Maven 3.9.9 on first use.

From the repository root:

```sh
docker compose up -d --wait
```

This starts a single-node MongoDB replica set with persistent storage on **localhost:27018**. Its health check initializes the replica set and waits for an elected primary. The nonstandard host port avoids conflicting with an existing MongoDB installation.

Start the backend in one terminal:

```sh
cd backend
./mvnw spring-boot:run
```

On Windows PowerShell, use `.\mvnw.cmd spring-boot:run` instead. The API listens on `http://localhost:8081`. To populate an empty active warehouse with four sample records, start with:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

PowerShell: `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=seed'`.

The seed profile does nothing when active records already exist. Use it for local development, not concurrent application startup.

Start the frontend in another terminal:

```sh
cd frontend
npm ci
npm run dev
```

Open **http://127.0.0.1:5174**. Vite proxies `/api` requests to the backend, so development does not require CORS configuration.

To stop the database while retaining inventory, run `docker compose down` from the repository root. Do not remove its volume unless you intend to discard the data.

### Configuration

| Variable | Default |
| --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27018/duckwarehouse?replicaSet=rs0&directConnection=true` |
| `PORT` | `8081` |

An external MongoDB deployment must support transactions: use a replica set or sharded cluster, not a standalone server. The local database binds its published port to loopback. This assignment does not include authentication or production deployment configuration.

## Business decisions

- Colors: `Red`, `Green`, `Yellow`, `Black`. Sizes: `XLarge`, `Large`, `Medium`, `Small`, `XSmall`. These values and shipping modes are case-sensitive.
- Prices are positive USD amounts with at most two decimal places, limited to nine integer digits. Inventory quantities are integers from zero through 2,147,483,647; order quantities start at one. Merges that overflow the stock limit fail without changing either record.
- IDs are stable positive integers from a database counter. Active records are unique by color, size, and price. Decimal128 storage makes prices such as `10`, `10.0`, and `10.00` equivalent.
- Add merges into an active match. A deleted match is preserved; adding again creates a new ID.
- Edit replaces the submitted quantity and price. Color and size cannot be edited. A price collision merges the **submitted** quantity into the existing target and logically deletes the source, retaining the target ID. For example, editing a source from 30 units to 9 units when the target has 5 leaves the target with 14 units.
- Listings exclude deleted records and sort by quantity ascending, then ID ascending. Delete is idempotent for existing IDs, including already deleted records; an unknown ID returns `404`.
- Quotes select the cheapest **single active record with sufficient quantity**. Stock is not combined across records. Quotes neither reserve nor decrement inventory and can become outdated when warehouse data changes.
- Percentages use the original merchandise cost `B = quantity × unitPrice`; they do not compound. Each signed adjustment is rounded independently to cents with `HALF_UP`, then summed. Shipping is added separately.
- Bulk discount applies above 100 units. Air shipping discount applies above 1,000 units and uses the air shipping charge as its basis.
- Countries are trimmed and matched without case sensitivity. `US`/`USA`/`United States`, `BO`/`Bolivia`, and `IN`/`India` receive their named rates; other nonempty values receive 15%.
- Unknown JSON fields, fractional quantities, invalid enums, missing required fields, and invalid monetary values return `400`. In particular, orders cannot supply their own price.

## Transaction behavior

All warehouse mutations run through `TransactionTemplate` and `MongoTransactionManager` with snapshot read concern and majority write concern. Multi-record merges update the target and delete the source in the same transaction. IDs are allocated inside the transaction, too.

MongoDB detects concurrent conflicting writes; the application retries the **entire aborted transaction**, in a fresh transaction, up to three total attempts with randomized backoff. A partial unique index on `(color, size, price)` where `deleted=false` also prevents duplicate active records. Concurrent insert collisions abort and retry against the winning record.

Every mutation increments a record's version. Edits must submit the version returned by the listing. If another operation changed that record, the edit returns `409 STALE_VERSION`; it never silently replaces newer stock. The UI offers a reload action.

An unknown commit result retries **commit on the same transaction**, up to three attempts, rather than executing the stock mutation again. If the outcome remains uncertain, the API returns `503 COMMIT_OUTCOME_UNKNOWN` and instructs the caller to reload inventory. The form blocks resubmission until reload. Other retry exhaustion returns `503 WRITE_CONTENTION`, with no committed change from that request. Add is not an idempotent operation across separate HTTP requests, so callers should not blindly replay a request after a connection loss.

These transactions provide atomicity and conflict detection, not a global application lock. Unrelated records can be updated concurrently.

## API

| Method and path | Request | Response |
| --- | --- | --- |
| `GET /api/ducks` | — | `200`, sorted active duck array |
| `POST /api/ducks` | `color`, `size`, `price`, `quantity` | `201` if created or `200` if merged; `{duck, outcome}` |
| `PUT /api/ducks/{id}` | `price`, `quantity`, `version` | `200`; `{duck, outcome}` |
| `DELETE /api/ducks/{id}` | — | `204` |
| `POST /api/orders/quote` | `color`, `size`, `quantity`, `destinationCountry`, `shippingMode` | `200`, itemized quote |

A duck response includes `id`, `color`, `size`, numeric `price`, `quantity`, `deleted`, and `version`. Mutation outcomes are `created`, `updated`, or `merged`. Price input accepts a JSON number or decimal string; the UI sends decimal text to preserve the input exactly.

Example using curl on macOS/Linux (Windows users can use the PowerShell example below):

```sh
curl -i http://localhost:8081/api/ducks \
  -H 'Content-Type: application/json' \
  -d '{"color":"Red","size":"Large","price":"10.00","quantity":1500}'

curl http://localhost:8081/api/orders/quote \
  -H 'Content-Type: application/json' \
  -d '{"color":"Red","size":"Large","quantity":101,"destinationCountry":"India","shippingMode":"Air"}'
```

PowerShell:

```powershell
Invoke-RestMethod http://localhost:8081/api/ducks -Method Post -ContentType 'application/json' -Body '{"color":"Red","size":"Large","price":"10.00","quantity":1500}'
Invoke-RestMethod http://localhost:8081/api/orders/quote -Method Post -ContentType 'application/json' -Body '{"color":"Red","size":"Large","quantity":101,"destinationCountry":"India","shippingMode":"Air"}'
```

Reference quote (assuming the selected record costs $10):

```json
{
  "duckId": 1,
  "unitPrice": 10.00,
  "currency": "USD",
  "packageType": "Wood",
  "protectionTypes": ["PolystyreneBalls"],
  "total": 4080.40,
  "breakdown": [
    {"code":"MERCHANDISE","label":"Merchandise","basis":1010.00,"rate":null,"amount":1010.00},
    {"code":"BULK_DISCOUNT","label":"Quantity discount","basis":1010.00,"rate":-0.20,"amount":-202.00},
    {"code":"PACKAGE_ADJUSTMENT","label":"Wood package","basis":1010.00,"rate":0.05,"amount":50.50},
    {"code":"DESTINATION_ADJUSTMENT","label":"Destination adjustment","basis":1010.00,"rate":0.19,"amount":191.90},
    {"code":"SHIPPING","label":"Air shipping","basis":3030.00,"rate":null,"amount":3030.00}
  ]
}
```

Packaging types are `Wood`, `Cardboard`, and `Plastic`. Protection types are `PolystyreneBalls`, `BubbleWrapBags`, and `MoistureAbsorbingBeads`. Percentage rates are signed fractions, e.g. `-0.20` means a 20% discount. The breakdown includes merchandise so that every entry sums directly to the total.

Errors use `application/problem+json` with `status`, `title`, `detail`, and a stable `code`; validation errors also include an `errors` map. Missing active variants return `404 VARIANT_NOT_FOUND`; insufficient stock in any single record returns `409 INSUFFICIENT_STOCK`. Backend logs retain infrastructure diagnostics while client errors omit database internals.

## Postman collection

Import [the store collection](postman/DuckWarehouse.Store.postman_collection.json) to exercise `POST /api/orders/quote`, including packaging, shipping, pricing boundaries, destinations, and error responses. The optional [local environment](postman/DuckWarehouse.Local.postman_environment.json) targets `http://127.0.0.1:8081`. See [the Postman instructions](postman/README.md) for seed inventory, variables, and running all response checks.

## Tests and builds

Backend (Docker must be running; Testcontainers creates an isolated replica set and leaves the development database untouched):

```sh
cd backend
./mvnw verify
```

Windows: `.\mvnw.cmd verify`. Pricing and packaging unit tests can also run without Docker:

```sh
./mvnw '-Dtest=PricingTest,PackagingTest,TransactionsTest,CommitRetryTest' test
```

Frontend:

```sh
cd frontend
npm ci
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

The browser test requires the Compose database. It starts the backend and Vite if needed, creates its own uniquely priced record, exercises add/merge/edit/quote/delete and mobile overflow, and logically deletes its record during cleanup. Do not run it against a production database.

Tests cover all 15 size/shipping packaging combinations, percentage bases, rounding both positive and negative half cents, destination aliases, discount boundaries, exact breakdown totals, concurrent matching adds, simultaneous edits, delete/add races, stale edits, rollback after both merge writes, stock overflow, quote selection, API validation, commit-only retries, and UI error handling. A GitHub Actions workflow runs backend checks, frontend checks, and the browser workflow on pushes and pull requests.

## Structure and design patterns

The backend separates HTTP DTOs/controllers, warehouse services, MongoDB persistence/transaction handling, and pricing. `PackagingFactory` implements the factory pattern. `PricingEngine` composes independent pricing-rule strategies producing signed breakdown entries; new rules do not require changing inventory code. MongoDB repositories do not calculate prices or build HTTP responses.

The frontend shares a single add/edit form, modal dialog, typed API client, and formatting logic. Native modal dialogs provide focus containment and keyboard dismissal. No store UI, checkout, authentication, or persisted order subsystem is included.

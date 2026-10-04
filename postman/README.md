# Store API collection

Import `DuckWarehouse.Store.postman_collection.json` into Postman. You can also import `DuckWarehouse.Local.postman_environment.json` and select **Duck Warehouse - Local**. The collection works without an environment; its `baseUrl` defaults to `http://127.0.0.1:8081`.

The store module exposes one endpoint: **POST `/api/orders/quote`**. All requests in this collection exercise that endpoint. No authentication is required and no inventory is created, edited, reserved, or deleted.

## Prepare inventory

Start MongoDB using `docker compose up -d --wait` from the repository root, then start the backend with the `seed` profile against an empty active warehouse:

```sh
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

PowerShell: `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=seed'`.

The seed data contains these active records:

| Color | Size | Unit price | Stock |
| --- | --- | --- | --- |
| Red | Large | $10.00 | 1,500 |
| Green | Medium | $7.25 | 85 |
| Yellow | Small | $4.50 | 240 |
| Black | XLarge | $18.00 | 32 |

The seed profile does nothing when active inventory already exists. With an existing database, prepare equivalent records through the warehouse UI or adapt the example requests to your inventory. Successful quote checks calculate expected prices using the returned unit price, so another eligible price does not invalidate the calculations.

## Requests

- **Custom quote:** edit the collection variables `color`, `size`, `quantity`, `destinationCountry`, and `shippingMode`. Quantity is substituted as a JSON number. Valid colors are Red/Green/Yellow/Black, sizes are XLarge/Large/Medium/Small/XSmall, and shipping modes are Land/Air/Sea.
- **Packaging and shipping:** all nine package/shipping combinations, plus an XLarge example. They use the seeded variants above; XSmall shares Small's plastic packaging and can be selected in the custom request when you stock it.
- **Discount boundaries:** quantities 100/101 and 1000/1001, using Red Large ducks.
- **Destination rules:** USA, Bolivia, India, another country, and a normalized country alias.
- **Validation and inventory errors:** zero, negative, fractional, missing, and overflowing quantities; invalid color, size, and shipping mode; blank or overlong destination; and a forbidden client-supplied price. The missing-variant request requires Red XSmall to be absent. The insufficient-stock request requires Red Large to exist with fewer than 2,147,483,647 units in every record.

Run the whole collection using Postman's Collection Runner. Each request checks its expected HTTP status. Successful responses check packaging, protections, percentage adjustments, shipping, discount thresholds, and the exact sum of the itemized breakdown in cents. Error responses check the Problem Details format and expected error code. Saved response examples are illustrative; IDs and error instance paths may vary.

The configurable quote defaults to 101 Red Large ducks shipped by Air to India. At the seeded $10 unit price, its total is **$4,080.40**. The quote endpoint never accepts a price and never deducts inventory.

## Run from a terminal

With Node.js and the backend running, execute from the repository root:

```sh
npx --yes newman@6.2.2 run postman/DuckWarehouse.Store.postman_collection.json -e postman/DuckWarehouse.Local.postman_environment.json
```

To target another running backend, override the environment variable:

```sh
npx --yes newman@6.2.2 run postman/DuckWarehouse.Store.postman_collection.json --env-var baseUrl=http://localhost:8081
```

The files use the [Postman Collection v2.1 schema](https://schema.postman.com/) and [Postman response test scripts](https://learning.postman.com/docs/tests-and-scripts/write-scripts/test-scripts).

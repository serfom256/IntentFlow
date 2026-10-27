-- products
SELECT * FROM "product" ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: []

-- products | filter name == "Galaxy"
SELECT * FROM "product" WHERE lowerUTF8("name") = lowerUTF8(?) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [Galaxy]

-- products | filter sku == "AB-1"
SELECT * FROM "product" WHERE "sku" = ? ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [AB-1]

-- products | filter category == "laptop"
SELECT * FROM "product" WHERE lowerUTF8("category") = lowerUTF8(?) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [laptop]

-- products | filter category in ("laptop", "phone")
SELECT * FROM "product" WHERE lowerUTF8("category") IN (lowerUTF8(?), lowerUTF8(?)) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [laptop, phone]

-- products | filter sku in ("A", "B")
SELECT * FROM "product" WHERE "sku" IN (?, ?) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [A, B]

-- products | filter name ~ "50%_off"
SELECT * FROM "product" WHERE positionCaseInsensitiveUTF8("name", ?) > 0 ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [50%_off]

-- products | filter price >= 10 and price < 99.5 and active == true
SELECT * FROM "product" WHERE ("price" >= ? AND "price" < CAST(? AS Decimal(38, 10)) AND "active" = ?) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [10, 99.5, true]

-- products | filter not (stock == 0 or discount == null)
SELECT * FROM "product" WHERE NOT (("stock" = ? OR "discount" IS NULL)) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [0]

-- products | filter discount != null
SELECT * FROM "product" WHERE "discount" IS NOT NULL ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: []

-- products | filter created_at == "2026-10-10"
SELECT * FROM "product" WHERE ("created_at" >= ? AND "created_at" < ?) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2026-10-10 00:00:00, 2026-10-11 00:00:00]

-- products | filter created_at != "2026-10-10"
SELECT * FROM "product" WHERE (("created_at" < ? OR "created_at" >= ?) OR "created_at" IS NULL) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2026-10-10 00:00:00, 2026-10-11 00:00:00]

-- products | filter created_at > "2026-10-10"
SELECT * FROM "product" WHERE "created_at" >= ? ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2026-10-11 00:00:00]

-- products | filter created_at <= "2026-10-10"
SELECT * FROM "product" WHERE "created_at" < ? ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2026-10-11 00:00:00]

-- products | filter created_at < "2026-10-10T10:15:00"
SELECT * FROM "product" WHERE "created_at" < ? ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2026-10-10 10:15:00]

-- products | filter created_at in ("2026-10-10", "2026-10-12")
SELECT * FROM "product" WHERE (("created_at" >= ? AND "created_at" < ?) OR ("created_at" >= ? AND "created_at" < ?)) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2026-10-10 00:00:00, 2026-10-11 00:00:00, 2026-10-12 00:00:00, 2026-10-13 00:00:00]

-- products | filter coalesce(discount, 0) > 10
SELECT * FROM "product" WHERE COALESCE(CAST("discount" AS Nullable(Decimal(38, 10))), ?) > ? ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [0, 10]

-- products | filter price * (1 - coalesce(discount, 0) / 100) < 500
SELECT * FROM "product" WHERE ("price" * (? - (CAST(COALESCE(CAST("discount" AS Nullable(Decimal(38, 10))), ?) AS Nullable(Decimal(38, 10))) / NULLIF(?, 0)))) < ? ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [1, 0, 100, 500]

-- products | filter year(created_at) == 2025 and month(created_at) in (1, 2, 3)
SELECT * FROM "product" WHERE ("created_at" >= ? AND "created_at" < ? AND EXTRACT(MONTH FROM "created_at") IN (?, ?, ?)) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2025-01-01 00:00:00, 2026-01-01 00:00:00, 1, 2, 3]
optimizations: date-ranges

-- products | filter lower(name) ~ "pro"
SELECT * FROM "product" WHERE positionCaseInsensitiveUTF8(lowerUTF8("name"), ?) > 0 ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [pro]

-- products | filter length(name) > 10 and round(price) == 100 and abs(stock - 10) < 5
SELECT * FROM "product" WHERE (lengthUTF8("name") > ? AND ROUND(CAST("price" AS Nullable(Decimal(38, 10)))) = ? AND ABS(("stock" - ?)) < ?) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [10, 100, 10, 5]

-- products | filter date_trunc("month", created_at) == "2026-01-01"
SELECT * FROM "product" WHERE ("created_at" >= ? AND "created_at" < ?) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2026-01-01 00:00:00, 2026-02-01 00:00:00]
optimizations: date-ranges

-- products | filter created_at::date == "2026-10-10"
SELECT * FROM "product" WHERE CAST("created_at" AS Nullable(Date)) = CAST(? AS Nullable(Date)) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [2026-10-10 00:00:00]

-- products | filter cast(sku as integer) > 1000
SELECT * FROM "product" WHERE CAST(round(CAST("sku" AS Nullable(Decimal(38, 10)))) AS Nullable(Int64)) > ? ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [1000]

-- products | sort price desc, name
SELECT * FROM "product" ORDER BY "price" DESC NULLS LAST, "name" ASC NULLS LAST, "id" ASC NULLS LAST LIMIT 50
binds: []

-- products | sort round(price, 2) desc | skip 20 | take 10
SELECT * FROM "product" ORDER BY ROUND(CAST("price" AS Nullable(Decimal(38, 10))), 2) DESC NULLS LAST, "id" ASC NULLS LAST LIMIT 10 OFFSET 20
binds: []

-- products | group category | count
SELECT "category", COUNT(*) FROM "product" GROUP BY "category" ORDER BY COUNT(*) DESC NULLS LAST, "category" ASC NULLS LAST LIMIT 50
binds: []

-- products | group category | avg price | sort avg desc | take 3
SELECT "category", avgOrNull("price") FROM "product" GROUP BY "category" ORDER BY avgOrNull("price") DESC NULLS LAST, "category" ASC NULLS LAST LIMIT 3
binds: []

-- products | group year(created_at), category | sum price * stock | sort sum desc
SELECT EXTRACT(YEAR FROM "created_at") AS "intentflow_g0", "category", sumOrNull(("price" * "stock")) FROM "product" GROUP BY "intentflow_g0", "category" ORDER BY sumOrNull(("price" * "stock")) DESC NULLS LAST, "intentflow_g0" ASC NULLS LAST, "category" ASC NULLS LAST LIMIT 50
binds: []

-- products | filter active == true | max price
SELECT maxOrNull("price") FROM "product" WHERE "active" = ? LIMIT 50
binds: [true]

-- products | top 3 per category by price desc | sort category
SELECT * FROM (SELECT "product".*, ROW_NUMBER() OVER (PARTITION BY "category" ORDER BY "price" DESC NULLS LAST, "id" ASC NULLS LAST) AS "intentflow_rank" FROM "product") "product" WHERE "intentflow_rank" <= 3 ORDER BY "category" ASC NULLS LAST, "id" ASC NULLS LAST LIMIT 50
binds: []

-- products | top 5 by price * stock desc
SELECT * FROM (SELECT "product".*, ROW_NUMBER() OVER (ORDER BY ("price" * "stock") DESC NULLS LAST, "id" ASC NULLS LAST) AS "intentflow_rank" FROM "product") "product" WHERE "intentflow_rank" <= 5 ORDER BY ("price" * "stock") DESC NULLS LAST, "id" ASC NULLS LAST LIMIT 50
binds: []

-- orders
SELECT * FROM "orders" ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: []

-- orders | filter customer.vip == true and status == "paid"
SELECT * FROM "orders" WHERE (EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id" AND "intentflow_r"."vip" = ?) AND lowerUTF8("status") = lowerUTF8(?)) ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: [true, paid]

-- orders | filter customer.country in ("DE", "FR") or total > 1000
SELECT * FROM "orders" WHERE (EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id" AND lowerUTF8("intentflow_r"."country") IN (lowerUTF8(?), lowerUTF8(?))) OR "total" > ?) ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: [DE, FR, 1000]

-- customers | filter orders.total > 100 and orders.status == "PAID" and country == "DE"
SELECT * FROM "customer" WHERE (EXISTS (SELECT 1 FROM "orders" "intentflow_r" WHERE "intentflow_r"."customer_id" = "customer"."id" AND "intentflow_r"."total" > ? AND lowerUTF8("intentflow_r"."status") = lowerUTF8(?)) AND lowerUTF8("country") = lowerUTF8(?)) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [100, PAID, DE]

-- customers | filter not (orders.status == "CANCELLED")
SELECT * FROM "customer" WHERE NOT (EXISTS (SELECT 1 FROM "orders" "intentflow_r" WHERE "intentflow_r"."customer_id" = "customer"."id" AND lowerUTF8("intentflow_r"."status") = lowerUTF8(?))) ORDER BY "id" ASC NULLS LAST LIMIT 50
binds: [CANCELLED]

-- orders | sort customer.name
rejected: sorting by 'customer.name' needs a correlated subquery in ORDER BY, which clickhouse does not support; sort by a field of the service itself

-- customers | sort orders.total desc
rejected: sorting by 'orders.total' needs a correlated subquery in ORDER BY, which clickhouse does not support; sort by a field of the service itself

-- orders | filter customer.vip == true | top 1 per customer_id by created_at desc
SELECT * FROM (SELECT "orders".*, ROW_NUMBER() OVER (PARTITION BY "customer_id" ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST) AS "intentflow_rank" FROM "orders" WHERE EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id" AND "intentflow_r"."vip" = ?)) "orders" WHERE "intentflow_rank" <= 1 ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: [true]

-- orders | group status | count | sort status
SELECT "status", COUNT(*) FROM "orders" GROUP BY "status" ORDER BY "status" ASC NULLS LAST LIMIT 50
binds: []

-- orders | filter created_at >= "2026-01-01" | group date_trunc("month", created_at) | sum total
SELECT CAST(dateTrunc('month', "created_at") AS Nullable(DateTime64(6))) AS "intentflow_g0", sumOrNull("total") FROM "orders" WHERE "created_at" >= ? GROUP BY "intentflow_g0" ORDER BY sumOrNull("total") DESC NULLS LAST, "intentflow_g0" ASC NULLS LAST LIMIT 50
binds: [2026-01-01 00:00:00]

-- orders | filter status != "cancelled" | take 5
SELECT * FROM "orders" WHERE (lowerUTF8("status") <> lowerUTF8(?) OR "status" IS NULL) ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 5
binds: [cancelled]

-- customers | filter name ~ "ann" and created_at >= "2025-01-01" | sort created_at desc
SELECT * FROM "customer" WHERE (positionCaseInsensitiveUTF8("name", ?) > 0 AND "created_at" >= ?) ORDER BY "created_at" DESC NULLS LAST, "id" ASC NULLS LAST LIMIT 50
binds: [ann, 2025-01-01 00:00:00]

-- orders | filter customer.name ~ "o" and items_count > 2
SELECT * FROM "orders" WHERE (EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id" AND positionCaseInsensitiveUTF8("intentflow_r"."name", ?) > 0) AND "items_count" > ?) ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: [o, 2]


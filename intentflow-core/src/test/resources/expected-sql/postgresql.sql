-- products
SELECT * FROM "product" ORDER BY "id" ASC LIMIT 50
binds: []

-- products | filter name == "Galaxy"
SELECT * FROM "product" WHERE LOWER("name") = LOWER(?) ORDER BY "id" ASC LIMIT 50
binds: [Galaxy]

-- products | filter sku == "AB-1"
SELECT * FROM "product" WHERE "sku" = ? ORDER BY "id" ASC LIMIT 50
binds: [AB-1]

-- products | filter category == "laptop"
SELECT * FROM "product" WHERE LOWER("category") = LOWER(?) ORDER BY "id" ASC LIMIT 50
binds: [laptop]

-- products | filter category in ("laptop", "phone")
SELECT * FROM "product" WHERE LOWER("category") IN (LOWER(?), LOWER(?)) ORDER BY "id" ASC LIMIT 50
binds: [laptop, phone]

-- products | filter sku in ("A", "B")
SELECT * FROM "product" WHERE "sku" IN (?, ?) ORDER BY "id" ASC LIMIT 50
binds: [A, B]

-- products | filter name ~ "50%_off"
SELECT * FROM "product" WHERE "name" ILIKE ? ESCAPE '\' ORDER BY "id" ASC LIMIT 50
binds: [%50\%\_off%]

-- products | filter price >= 10 and price < 99.5 and active == true
SELECT * FROM "product" WHERE ("price" >= ? AND "price" < ? AND "active" = ?) ORDER BY "id" ASC LIMIT 50
binds: [10, 99.5, true]

-- products | filter not (stock == 0 or discount == null)
SELECT * FROM "product" WHERE NOT (("stock" = ? OR "discount" IS NULL)) ORDER BY "id" ASC LIMIT 50
binds: [0]

-- products | filter discount != null
SELECT * FROM "product" WHERE "discount" IS NOT NULL ORDER BY "id" ASC LIMIT 50
binds: []

-- products | filter created_at == "2026-10-10"
SELECT * FROM "product" WHERE ("created_at" >= CAST(? AS TIMESTAMP) AND "created_at" < CAST(? AS TIMESTAMP)) ORDER BY "id" ASC LIMIT 50
binds: [2026-10-10, 2026-10-11]

-- products | filter created_at != "2026-10-10"
SELECT * FROM "product" WHERE ("created_at" < CAST(? AS TIMESTAMP) OR "created_at" >= CAST(? AS TIMESTAMP)) ORDER BY "id" ASC LIMIT 50
binds: [2026-10-10, 2026-10-11]

-- products | filter created_at > "2026-10-10"
SELECT * FROM "product" WHERE "created_at" >= CAST(? AS TIMESTAMP) ORDER BY "id" ASC LIMIT 50
binds: [2026-10-11]

-- products | filter created_at <= "2026-10-10"
SELECT * FROM "product" WHERE "created_at" < CAST(? AS TIMESTAMP) ORDER BY "id" ASC LIMIT 50
binds: [2026-10-11]

-- products | filter created_at < "2026-10-10T10:15:00"
SELECT * FROM "product" WHERE "created_at" < CAST(? AS TIMESTAMP) ORDER BY "id" ASC LIMIT 50
binds: [2026-10-10T10:15:00]

-- products | filter created_at in ("2026-10-10", "2026-10-12")
SELECT * FROM "product" WHERE (("created_at" >= CAST(? AS TIMESTAMP) AND "created_at" < CAST(? AS TIMESTAMP)) OR ("created_at" >= CAST(? AS TIMESTAMP) AND "created_at" < CAST(? AS TIMESTAMP))) ORDER BY "id" ASC LIMIT 50
binds: [2026-10-10, 2026-10-11, 2026-10-12, 2026-10-13]

-- products | filter coalesce(discount, 0) > 10
SELECT * FROM "product" WHERE COALESCE("discount", ?) > ? ORDER BY "id" ASC LIMIT 50
binds: [0, 10]

-- products | filter price * (1 - coalesce(discount, 0) / 100) < 500
SELECT * FROM "product" WHERE ("price" * (? - (CAST(COALESCE("discount", ?) AS NUMERIC) / NULLIF(?, 0)))) < ? ORDER BY "id" ASC LIMIT 50
binds: [1, 0, 100, 500]

-- products | filter year(created_at) == 2025 and month(created_at) in (1, 2, 3)
SELECT * FROM "product" WHERE ("created_at" >= CAST(? AS TIMESTAMP) AND "created_at" < CAST(? AS TIMESTAMP) AND EXTRACT(MONTH FROM "created_at") IN (?, ?, ?)) ORDER BY "id" ASC LIMIT 50
binds: [2025-01-01, 2026-01-01, 1, 2, 3]
optimizations: date-ranges

-- products | filter lower(name) ~ "pro"
SELECT * FROM "product" WHERE LOWER(LOWER("name")) LIKE LOWER(?) ESCAPE '!' ORDER BY "id" ASC LIMIT 50
binds: [%pro%]

-- products | filter length(name) > 10 and round(price) == 100 and abs(stock - 10) < 5
SELECT * FROM "product" WHERE (CHAR_LENGTH("name") > ? AND ROUND(CAST("price" AS NUMERIC)) = ? AND ABS(("stock" - ?)) < ?) ORDER BY "id" ASC LIMIT 50
binds: [10, 100, 10, 5]

-- products | filter date_trunc("month", created_at) == "2026-01-01"
SELECT * FROM "product" WHERE ("created_at" >= CAST(? AS TIMESTAMP) AND "created_at" < CAST(? AS TIMESTAMP)) ORDER BY "id" ASC LIMIT 50
binds: [2026-01-01, 2026-02-01]
optimizations: date-ranges

-- products | filter created_at::date == "2026-10-10"
SELECT * FROM "product" WHERE CAST("created_at" AS DATE) = CAST(? AS DATE) ORDER BY "id" ASC LIMIT 50
binds: [2026-10-10]

-- products | filter cast(sku as integer) > 1000
SELECT * FROM "product" WHERE CAST("sku" AS BIGINT) > ? ORDER BY "id" ASC LIMIT 50
binds: [1000]

-- products | sort price desc, name
SELECT * FROM "product" ORDER BY "price" DESC NULLS LAST, "name" ASC, "id" ASC LIMIT 50
binds: []

-- products | sort round(price, 2) desc | skip 20 | take 10
SELECT * FROM "product" ORDER BY ROUND(CAST("price" AS NUMERIC), 2) DESC NULLS LAST, "id" ASC LIMIT 10 OFFSET 20
binds: []

-- products | group category | count
SELECT "category", COUNT(*) FROM "product" GROUP BY "category" ORDER BY COUNT(*) DESC NULLS LAST, "category" ASC LIMIT 50
binds: []

-- products | group category | avg price | sort avg desc | take 3
SELECT "category", AVG("price") FROM "product" GROUP BY "category" ORDER BY AVG("price") DESC NULLS LAST, "category" ASC LIMIT 3
binds: []

-- products | group year(created_at), category | sum price * stock | sort sum desc
SELECT EXTRACT(YEAR FROM "created_at") AS "intentflow_g0", "category", SUM(("price" * "stock")) FROM "product" GROUP BY "intentflow_g0", "category" ORDER BY SUM(("price" * "stock")) DESC NULLS LAST, "intentflow_g0" ASC, "category" ASC LIMIT 50
binds: []

-- products | filter active == true | max price
SELECT MAX("price") FROM "product" WHERE "active" = ? LIMIT 50
binds: [true]

-- products | top 3 per category by price desc | sort category
SELECT * FROM (SELECT "product".*, ROW_NUMBER() OVER (PARTITION BY "category" ORDER BY "price" DESC NULLS LAST, "id" ASC) AS "intentflow_rank" FROM "product") "product" WHERE "intentflow_rank" <= 3 ORDER BY "category" ASC, "id" ASC LIMIT 50
binds: []

-- products | top 5 by price * stock desc
SELECT * FROM (SELECT "product".*, ROW_NUMBER() OVER (ORDER BY ("price" * "stock") DESC NULLS LAST, "id" ASC) AS "intentflow_rank" FROM "product") "product" WHERE "intentflow_rank" <= 5 ORDER BY "id" ASC LIMIT 50
binds: []

-- orders
SELECT * FROM "orders" ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: []

-- orders | filter customer.vip == true and status == "paid"
SELECT * FROM "orders" WHERE (EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id" AND "intentflow_r"."vip" = ?) AND LOWER("status") = LOWER(?)) ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: [true, paid]

-- orders | filter customer.country in ("DE", "FR") or total > 1000
SELECT * FROM "orders" WHERE (EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id" AND LOWER("intentflow_r"."country") IN (LOWER(?), LOWER(?))) OR "total" > ?) ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: [DE, FR, 1000]

-- customers | filter orders.total > 100 and orders.status == "PAID" and country == "DE"
SELECT * FROM "customer" WHERE (EXISTS (SELECT 1 FROM "orders" "intentflow_r" WHERE "intentflow_r"."customer_id" = "customer"."id" AND "intentflow_r"."total" > ? AND LOWER("intentflow_r"."status") = LOWER(?)) AND LOWER("country") = LOWER(?)) ORDER BY "id" ASC LIMIT 50
binds: [100, PAID, DE]

-- customers | filter not (orders.status == "CANCELLED")
SELECT * FROM "customer" WHERE NOT (EXISTS (SELECT 1 FROM "orders" "intentflow_r" WHERE "intentflow_r"."customer_id" = "customer"."id" AND LOWER("intentflow_r"."status") = LOWER(?))) ORDER BY "id" ASC LIMIT 50
binds: [CANCELLED]

-- orders | sort customer.name
SELECT * FROM "orders" ORDER BY (SELECT MIN("intentflow_r"."name") FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id") ASC, "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: []

-- customers | sort orders.total desc
SELECT * FROM "customer" ORDER BY (SELECT MAX("intentflow_r"."total") FROM "orders" "intentflow_r" WHERE "intentflow_r"."customer_id" = "customer"."id") DESC NULLS LAST, "id" ASC LIMIT 50
binds: []

-- orders | filter customer.vip == true | top 1 per customer_id by created_at desc
SELECT * FROM (SELECT "orders".*, ROW_NUMBER() OVER (PARTITION BY "customer_id" ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST) AS "intentflow_rank" FROM "orders" WHERE EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id" AND "intentflow_r"."vip" = ?)) "orders" WHERE "intentflow_rank" <= 1 ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: [true]

-- orders | group status | count | sort status
SELECT "status", COUNT(*) FROM "orders" GROUP BY "status" ORDER BY "status" ASC LIMIT 50
binds: []

-- orders | filter created_at >= "2026-01-01" | group date_trunc("month", created_at) | sum total
SELECT DATE_TRUNC('month', "created_at") AS "intentflow_g0", SUM("total") FROM "orders" WHERE "created_at" >= CAST(? AS TIMESTAMP) GROUP BY "intentflow_g0" ORDER BY SUM("total") DESC NULLS LAST, "intentflow_g0" ASC LIMIT 50
binds: [2026-01-01]

-- orders | filter status != "cancelled" | take 5
SELECT * FROM "orders" WHERE LOWER("status") <> LOWER(?) ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 5
binds: [cancelled]

-- customers | filter name ~ "ann" and created_at >= "2025-01-01" | sort created_at desc
SELECT * FROM "customer" WHERE ("name" ILIKE ? ESCAPE '\' AND "created_at" >= CAST(? AS TIMESTAMP)) ORDER BY "created_at" DESC NULLS LAST, "id" ASC LIMIT 50
binds: [%ann%, 2025-01-01]

-- orders | filter customer.name ~ "o" and items_count > 2
SELECT * FROM "orders" WHERE (EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "orders"."customer_id" AND "intentflow_r"."name" ILIKE ? ESCAPE '\') AND "items_count" > ?) ORDER BY "created_at" DESC NULLS LAST, "id" DESC NULLS LAST LIMIT 50
binds: [%o%, 2]


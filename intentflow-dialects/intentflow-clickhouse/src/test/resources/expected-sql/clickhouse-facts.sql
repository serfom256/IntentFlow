-- products
SELECT * FROM "product" WHERE (archived = false) ORDER BY "id" ASC LIMIT 50
binds: []
optimizations: not-null-ordering

-- products | filter category == "laptop" and name == "Galaxy"
SELECT * FROM "product" WHERE ("category" = ? AND lowerUTF8("name") = lowerUTF8(?)) AND (archived = false) ORDER BY "id" ASC LIMIT 50
binds: [LAPTOP, Galaxy]
optimizations: not-null-ordering, exact-values

-- products | filter category in ("laptop", "PHONE")
SELECT * FROM "product" WHERE "category" IN (?, ?) AND (archived = false) ORDER BY "id" ASC LIMIT 50
binds: [LAPTOP, PHONE]
optimizations: not-null-ordering, exact-values

-- products | sort created_at desc, price desc
SELECT * FROM "product" WHERE (archived = false) ORDER BY "created_at" DESC, "price" DESC NULLS LAST, "id" ASC LIMIT 50
binds: []
optimizations: not-null-ordering

-- products | top 2 per category by created_at desc
SELECT * FROM (SELECT "product".*, ROW_NUMBER() OVER (PARTITION BY "category" ORDER BY "created_at" DESC, "id" ASC) AS "intentflow_rank" FROM "product" WHERE (archived = false)) "product" WHERE "intentflow_rank" <= 2 ORDER BY "id" ASC LIMIT 50
binds: []
optimizations: not-null-ordering

-- products | group category | count
SELECT "category", COUNT(*) FROM "product" WHERE (archived = false) GROUP BY "category" ORDER BY COUNT(*) DESC NULLS LAST, "category" ASC NULLS LAST LIMIT 50
binds: []

-- customers | filter orders.total > 100 and orders.status == "paid"
SELECT * FROM "customer" WHERE EXISTS (SELECT 1 FROM "orders" "intentflow_r" WHERE "intentflow_r"."customer_id" = "customer"."id" AND "intentflow_r"."total" > ? AND "intentflow_r"."status" = ? AND (deleted_at IS NULL)) ORDER BY "id" ASC LIMIT 50
binds: [100, PAID]
optimizations: not-null-ordering, exact-values

-- customers | sort orders.total desc
rejected: sorting by 'orders.total' needs a correlated subquery in ORDER BY, which clickhouse does not support; sort by a field of the service itself

-- orders | sort customer.name
rejected: sorting by 'customer.name' needs a correlated subquery in ORDER BY, which clickhouse does not support; sort by a field of the service itself

-- orders | filter year(created_at) == 2026 and month(created_at) == 3 and day(created_at) == 9
SELECT * FROM "orders" WHERE ("created_at" >= ? AND "created_at" < ?) AND (deleted_at IS NULL) ORDER BY "created_at" DESC, "id" DESC LIMIT 50
binds: [2026-03-09 00:00:00, 2026-03-10 00:00:00]
optimizations: not-null-ordering, date-ranges

-- orders | filter year(created_at) in (2024, 2025) or year(created_at) > 2025
SELECT * FROM "orders" WHERE ((("created_at" >= ? AND "created_at" < ?) OR ("created_at" >= ? AND "created_at" < ?)) OR "created_at" >= ?) AND (deleted_at IS NULL) ORDER BY "created_at" DESC, "id" DESC LIMIT 50
binds: [2024-01-01 00:00:00, 2025-01-01 00:00:00, 2025-01-01 00:00:00, 2026-01-01 00:00:00, 2026-01-01 00:00:00]
optimizations: not-null-ordering, date-ranges

-- invoices | sort customer.name desc
rejected: sorting by 'customer.name' needs a correlated subquery in ORDER BY, which clickhouse does not support; sort by a field of the service itself

-- invoices | filter customer.vip == true | sort issued_at desc
SELECT * FROM "billing"."invoice" WHERE EXISTS (SELECT 1 FROM "customer" "intentflow_r" WHERE "intentflow_r"."id" = "invoice"."customer_id" AND "intentflow_r"."vip" = ?) ORDER BY "issued_at" DESC, "id" ASC LIMIT 50
binds: [true]
optimizations: not-null-ordering

-- keys: products | filter price > 10
SELECT "id" FROM "product" WHERE "price" > ? AND (archived = false) ORDER BY "id" ASC LIMIT 50
binds: [10]
optimizations: not-null-ordering, key-projection

-- keys: invoices | sort customer.name
rejected: sorting by 'customer.name' needs a correlated subquery in ORDER BY, which clickhouse does not support; sort by a field of the service itself

-- keys: products | top 1 per category by price desc
SELECT "id" FROM (SELECT "product".*, ROW_NUMBER() OVER (PARTITION BY "category" ORDER BY "price" DESC NULLS LAST, "id" ASC) AS "intentflow_rank" FROM "product" WHERE (archived = false)) "product" WHERE "intentflow_rank" <= 1 ORDER BY "id" ASC LIMIT 50
binds: []
optimizations: not-null-ordering, key-projection

-- keys: products | group category | count
SELECT "category", COUNT(*) FROM "product" WHERE (archived = false) GROUP BY "category" ORDER BY COUNT(*) DESC NULLS LAST, "category" ASC NULLS LAST LIMIT 50
binds: []


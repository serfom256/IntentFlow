-- products
SELECT * FROM `product` WHERE (archived = false) ORDER BY `id` ASC LIMIT 50
binds: []
optimizations: not-null-ordering

-- products | filter category == "laptop" and name == "Galaxy"
SELECT * FROM `product` WHERE (`category` = ? AND LOWER(`name`) = LOWER(?)) AND (archived = false) ORDER BY `id` ASC LIMIT 50
binds: [LAPTOP, Galaxy]
optimizations: not-null-ordering, exact-values

-- products | filter category in ("laptop", "PHONE")
SELECT * FROM `product` WHERE `category` IN (?, ?) AND (archived = false) ORDER BY `id` ASC LIMIT 50
binds: [LAPTOP, PHONE]
optimizations: not-null-ordering, exact-values

-- products | sort created_at desc, price desc
SELECT * FROM `product` WHERE (archived = false) ORDER BY `created_at` DESC, `price` IS NULL, `price` DESC, `id` ASC LIMIT 50
binds: []
optimizations: not-null-ordering

-- products | top 2 per category by created_at desc
SELECT * FROM (SELECT `product`.*, ROW_NUMBER() OVER (PARTITION BY `category` ORDER BY `created_at` DESC, `id` ASC) AS `intentflow_rank` FROM `product` WHERE (archived = false)) `product` WHERE `intentflow_rank` <= 2 ORDER BY `id` ASC LIMIT 50
binds: []
optimizations: not-null-ordering

-- products | group category | count
SELECT `category`, COUNT(*) FROM `product` WHERE (archived = false) GROUP BY `category` ORDER BY COUNT(*) IS NULL, COUNT(*) DESC, `category` IS NULL, `category` ASC LIMIT 50
binds: []

-- customers | filter orders.total > 100 and orders.status == "paid"
SELECT * FROM `customer` WHERE EXISTS (SELECT 1 FROM `orders` `intentflow_r` WHERE `intentflow_r`.`customer_id` = `customer`.`id` AND `intentflow_r`.`total` > ? AND `intentflow_r`.`status` = ? AND (deleted_at IS NULL)) ORDER BY `id` ASC LIMIT 50
binds: [100, PAID]
optimizations: not-null-ordering, exact-values

-- customers | sort orders.total desc
SELECT * FROM `customer` ORDER BY (SELECT MAX(`intentflow_r`.`total`) FROM `orders` `intentflow_r` WHERE `intentflow_r`.`customer_id` = `customer`.`id` AND (deleted_at IS NULL)) IS NULL, (SELECT MAX(`intentflow_r`.`total`) FROM `orders` `intentflow_r` WHERE `intentflow_r`.`customer_id` = `customer`.`id` AND (deleted_at IS NULL)) DESC, `id` ASC LIMIT 50
binds: []
optimizations: not-null-ordering

-- orders | sort customer.name
SELECT * FROM `orders` WHERE (deleted_at IS NULL) ORDER BY (SELECT MIN(`intentflow_r`.`name`) FROM `customer` `intentflow_r` WHERE `intentflow_r`.`id` = `orders`.`customer_id`) IS NULL, (SELECT MIN(`intentflow_r`.`name`) FROM `customer` `intentflow_r` WHERE `intentflow_r`.`id` = `orders`.`customer_id`) ASC, `created_at` DESC, `id` DESC LIMIT 50
binds: []
optimizations: not-null-ordering

-- orders | filter year(created_at) == 2026 and month(created_at) == 3 and day(created_at) == 9
SELECT * FROM `orders` WHERE (`created_at` >= ? AND `created_at` < ?) AND (deleted_at IS NULL) ORDER BY `created_at` DESC, `id` DESC LIMIT 50
binds: [2026-03-09T00:00, 2026-03-10T00:00]
optimizations: not-null-ordering, date-ranges

-- orders | filter year(created_at) in (2024, 2025) or year(created_at) > 2025
SELECT * FROM `orders` WHERE (((`created_at` >= ? AND `created_at` < ?) OR (`created_at` >= ? AND `created_at` < ?)) OR `created_at` >= ?) AND (deleted_at IS NULL) ORDER BY `created_at` DESC, `id` DESC LIMIT 50
binds: [2024-01-01T00:00, 2025-01-01T00:00, 2025-01-01T00:00, 2026-01-01T00:00, 2026-01-01T00:00]
optimizations: not-null-ordering, date-ranges

-- invoices | sort customer.name desc
SELECT `invoice`.* FROM `billing`.`invoice` LEFT JOIN `customer` `intentflow_j0` ON `intentflow_j0`.`id` = `invoice`.`customer_id` ORDER BY `intentflow_j0`.`name` IS NULL, `intentflow_j0`.`name` DESC, `invoice`.`id` ASC LIMIT 50
binds: []
optimizations: not-null-ordering, to-one-join

-- invoices | filter customer.vip == true | sort issued_at desc
SELECT * FROM `billing`.`invoice` WHERE EXISTS (SELECT 1 FROM `customer` `intentflow_r` WHERE `intentflow_r`.`id` = `invoice`.`customer_id` AND `intentflow_r`.`vip` = ?) ORDER BY `issued_at` DESC, `id` ASC LIMIT 50
binds: [true]
optimizations: not-null-ordering

-- keys: products | filter price > 10
SELECT `id` FROM `product` WHERE `price` > ? AND (archived = false) ORDER BY `id` ASC LIMIT 50
binds: [10]
optimizations: not-null-ordering, key-projection

-- keys: invoices | sort customer.name
SELECT `invoice`.`id` FROM `billing`.`invoice` LEFT JOIN `customer` `intentflow_j0` ON `intentflow_j0`.`id` = `invoice`.`customer_id` ORDER BY `intentflow_j0`.`name` IS NULL, `intentflow_j0`.`name` ASC, `invoice`.`id` ASC LIMIT 50
binds: []
optimizations: not-null-ordering, to-one-join, key-projection

-- keys: products | top 1 per category by price desc
SELECT `id` FROM (SELECT `product`.*, ROW_NUMBER() OVER (PARTITION BY `category` ORDER BY `price` IS NULL, `price` DESC, `id` ASC) AS `intentflow_rank` FROM `product` WHERE (archived = false)) `product` WHERE `intentflow_rank` <= 1 ORDER BY `id` ASC LIMIT 50
binds: []
optimizations: not-null-ordering, key-projection

-- keys: products | group category | count
SELECT `category`, COUNT(*) FROM `product` WHERE (archived = false) GROUP BY `category` ORDER BY COUNT(*) IS NULL, COUNT(*) DESC, `category` IS NULL, `category` ASC LIMIT 50
binds: []


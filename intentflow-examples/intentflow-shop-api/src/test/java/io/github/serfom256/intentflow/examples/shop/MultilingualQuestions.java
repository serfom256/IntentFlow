package io.github.serfom256.intentflow.examples.shop;

import io.github.serfom256.intentflow.examples.shop.LlmEvaluationSupport.Case;
import io.github.serfom256.intentflow.examples.shop.domain.Category;
import io.github.serfom256.intentflow.examples.shop.domain.Customer;
import io.github.serfom256.intentflow.examples.shop.domain.Order;
import io.github.serfom256.intentflow.examples.shop.domain.OrderStatus;
import io.github.serfom256.intentflow.examples.shop.domain.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class MultilingualQuestions {

    enum Lang {
        EN, RU, ZH, JA
    }

    record Phrase(String en, String ru, String zh, String ja) {

        String in(Lang lang) {
            return switch (lang) {
                case EN -> en;
                case RU -> ru;
                case ZH -> zh;
                case JA -> ja;
            };
        }
    }

    private static final Map<Category, Phrase> CATEGORIES = Map.of(
            Category.LAPTOP, new Phrase("laptops", "ноутбуки", "笔记本电脑", "ノートパソコン"),
            Category.PHONE, new Phrase("phones", "телефоны", "手机", "スマートフォン"),
            Category.TABLET, new Phrase("tablets", "планшеты", "平板电脑", "タブレット"),
            Category.HEADPHONES, new Phrase("headphones", "наушники", "耳机", "ヘッドホン"),
            Category.MONITOR, new Phrase("monitors", "мониторы", "显示器", "モニター"),
            Category.ACCESSORY, new Phrase("accessories", "аксессуары", "配件", "アクセサリー"));

    private static final Map<String, Phrase> COUNTRIES = Map.of(
            "DE", new Phrase("Germany", "Германии", "德国", "ドイツ"),
            "FR", new Phrase("France", "Франции", "法国", "フランス"),
            "US", new Phrase("the United States", "США", "美国", "アメリカ"),
            "GB", new Phrase("the United Kingdom", "Великобритании", "英国", "イギリス"),
            "JP", new Phrase("Japan", "Японии", "日本", "日本"),
            "IT", new Phrase("Italy", "Италии", "意大利", "イタリア"));

    private static final Map<String, Phrase> CITIES = Map.of(
            "Berlin", new Phrase("Berlin", "Берлин", "柏林", "ベルリン"),
            "Paris", new Phrase("Paris", "Париж", "巴黎", "パリ"),
            "Tokyo", new Phrase("Tokyo", "Токио", "东京", "東京"),
            "London", new Phrase("London", "Лондон", "伦敦", "ロンドン"),
            "Madrid", new Phrase("Madrid", "Мадрид", "马德里", "マドリード"));

    private static final Map<OrderStatus, Phrase> STATUSES = Map.of(
            OrderStatus.NEW, new Phrase("new", "новые", "新", "新規"),
            OrderStatus.PAID, new Phrase("paid", "оплаченные", "已支付", "支払い済み"),
            OrderStatus.SHIPPED, new Phrase("shipped", "отправленные", "已发货", "発送済み"),
            OrderStatus.DELIVERED, new Phrase("delivered", "доставленные", "已送达", "配達済み"),
            OrderStatus.CANCELLED, new Phrase("cancelled", "отменённые", "已取消", "キャンセル済み"));

    private static final Map<OrderStatus, Phrase> STATUS_SINGULAR = Map.of(
            OrderStatus.NEW, new Phrase("new", "новый", "新", "新規"),
            OrderStatus.DELIVERED, new Phrase("delivered", "доставленный", "已送达", "配達済み"),
            OrderStatus.CANCELLED, new Phrase("cancelled", "отменённый", "已取消", "キャンセル済み"));

    private static final Map<OrderStatus, Phrase> STATUS_GENITIVE = Map.of(
            OrderStatus.PAID, new Phrase("paid", "оплаченных", "已支付", "支払い済み"),
            OrderStatus.DELIVERED, new Phrase("delivered", "доставленных", "已送达", "配達済み"),
            OrderStatus.CANCELLED, new Phrase("cancelled", "отменённых", "已取消", "キャンセル済み"));

    private static final List<Phrase> MONTHS = List.of(
            new Phrase("January", "январь", "1", "1"), new Phrase("February", "февраль", "2", "2"),
            new Phrase("March", "март", "3", "3"), new Phrase("April", "апрель", "4", "4"),
            new Phrase("May", "май", "5", "5"), new Phrase("June", "июнь", "6", "6"),
            new Phrase("July", "июль", "7", "7"), new Phrase("August", "август", "8", "8"),
            new Phrase("September", "сентябрь", "9", "9"), new Phrase("October", "октябрь", "10", "10"),
            new Phrase("November", "ноябрь", "11", "11"), new Phrase("December", "декабрь", "12", "12"));

    private final LlmEvaluationSupport data;
    private final ZoneId zone = ZoneId.systemDefault();
    private final List<Case> cases = new ArrayList<>();

    MultilingualQuestions(LlmEvaluationSupport data) {
        this.data = data;
    }

    List<Case> cases() {
        products();
        customers();
        orders();
        declined();
        return List.copyOf(cases);
    }

    private void products() {
        List<Object[]> brands = List.of(
                new Object[] { "Apple", Category.LAPTOP }, new Object[] { "Lenovo", Category.LAPTOP },
                new Object[] { "Samsung", Category.PHONE }, new Object[] { "Google", Category.PHONE },
                new Object[] { "Apple", Category.TABLET }, new Object[] { "Sony", Category.HEADPHONES });
        Phrase brandCategory = new Phrase("{brand} {cat}", "{cat} {brand}", "{brand}的{cat}", "{brand}の{cat}");
        for (Object[] pair : brands) {
            String brand = (String) pair[0];
            Category category = (Category) pair[1];
            set(brandCategory, Map.of("brand", brand, "cat", category),
                    () -> data.productIds(p -> p.getBrand().equals(brand) && p.getCategory() == category));
        }

        Map<Category, Integer> under = Map.of(Category.LAPTOP, 1000, Category.PHONE, 500, Category.TABLET, 600,
                Category.HEADPHONES, 200, Category.MONITOR, 800, Category.ACCESSORY, 50);
        Phrase cheaperThan = new Phrase("{cat} under {price} dollars", "{cat} дешевле {price} долларов",
                "价格低于{price}美元的{cat}", "{price}ドル未満の{cat}");
        under.forEach((category, price) -> set(cheaperThan, Map.of("cat", category, "price", price),
                () -> data.productIds(p -> p.getCategory() == category && below(p.getPrice(), price))));

        Map<Category, Integer> over = Map.of(Category.LAPTOP, 2500, Category.PHONE, 1200, Category.TABLET, 1500,
                Category.HEADPHONES, 400, Category.MONITOR, 1500, Category.ACCESSORY, 150);
        Phrase dearerThan = new Phrase("{cat} more expensive than {price} dollars", "{cat} дороже {price} долларов",
                "价格高于{price}美元的{cat}", "{price}ドルより高い{cat}");
        over.forEach((category, price) -> set(dearerThan, Map.of("cat", category, "price", price),
                () -> data.productIds(p -> p.getCategory() == category && p.getPrice().compareTo(dec(price)) > 0)));

        Phrase rated = new Phrase("{cat} rated {r} or higher", "{cat} с рейтингом {r} и выше",
                "评分{r}及以上的{cat}", "評価{r}以上の{cat}");
        Phrase outOfStock = new Phrase("{cat} that are out of stock", "{cat}, которых нет в наличии",
                "缺货的{cat}", "在庫切れの{cat}");
        Phrase mostExpensive = new Phrase("the {n} most expensive {cat}",
                "{n} самых дорогих {items} в категории «{cat}»",
                "最贵的{n}款{cat}", "最も高価な{cat}を{n}件");
        Phrase cheapest = new Phrase("the {n} cheapest {cat}", "{n} самых дешёвых {items} в категории «{cat}»",
                "最便宜的{n}款{cat}", "最も安い{cat}を{n}件");
        Phrase average = new Phrase("average price of {cat}", "средняя цена в категории «{cat}»",
                "{cat}的平均价格", "{cat}の平均価格");
        Phrase highest = new Phrase("the highest price among {cat}", "максимальная цена в категории «{cat}»",
                "{cat}的最高价格", "{cat}の最高価格");
        int i = 0;
        for (Category category : Category.values()) {
            String r = i % 2 == 0 ? "4.5" : "4.8";
            int n = i % 2 == 0 ? 3 : 5;
            i++;
            set(rated, Map.of("cat", category, "r", r),
                    () -> data.productIds(p -> p.getCategory() == category && p.getRating().compareTo(dec(r)) >= 0));
            set(outOfStock, Map.of("cat", category),
                    () -> data.productIds(p -> p.getCategory() == category && p.getStock() == 0));
            ordered(mostExpensive, Map.of("cat", category, "n", n, "items", items(n)),
                    () -> top(category, n, Comparator.comparing(Product::getPrice).reversed()));
            ordered(cheapest, Map.of("cat", category, "n", n, "items", items(n)),
                    () -> top(category, n, Comparator.comparing(Product::getPrice)));
            single(average, Map.of("cat", category), () -> average(inCategory(category), Product::getPrice));
            single(highest, Map.of("cat", category), () -> inCategory(category).stream().map(Product::getPrice)
                    .max(Comparator.naturalOrder()).orElseThrow());
        }

        set(new Phrase("products that are no longer sold", "товары, снятые с продажи", "已停售的商品", "販売終了した商品"),
                Map.of(), () -> data.productIds(p -> !p.isActive()));
        Phrase discontinued = new Phrase("discontinued {cat}", "снятые с продажи {cat}", "已停售的{cat}",
                "販売終了した{cat}");
        for (Category category : List.of(Category.LAPTOP, Category.PHONE, Category.MONITOR)) {
            set(discontinued, Map.of("cat", category),
                    () -> data.productIds(p -> p.getCategory() == category && !p.isActive()));
        }

        Phrase named = new Phrase("products with {w} in the name", "товары со словом {w} в названии",
                "名称中包含{w}的商品", "名前に「{w}」を含む商品");
        for (String word : List.of("Pro", "Ultra", "Max", "Air", "Mini")) {
            set(named, Map.of("w", word), () -> data.productIds(
                    p -> p.getName().toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT))));
        }

        groups(new Phrase("how many products are there in each category?", "сколько товаров в каждой категории?",
                "每个类别有多少商品？", "カテゴリごとの商品数は？"), Map.of(),
                () -> count(data.products.findAll(), p -> p.getCategory().name()));
        groups(new Phrase("average price per category", "средняя цена по категориям", "各类别的平均价格",
                "カテゴリ別の平均価格"), Map.of(),
                () -> data.products.findAll().stream().collect(Collectors.groupingBy(
                        p -> p.getCategory().name(), Collectors.collectingAndThen(Collectors.toList(),
                                list -> average(list, Product::getPrice)))));

        set(new Phrase("the best rated product in each category", "товар с самым высоким рейтингом в каждой категории",
                "每个类别中评分最高的商品", "各カテゴリで最も評価の高い商品"), Map.of(),
                () -> data.products.findAll().stream()
                        .collect(Collectors.groupingBy(Product::getCategory, Collectors.minBy(
                                Comparator.comparing(Product::getRating).reversed().thenComparing(Product::getId))))
                        .values().stream().map(p -> p.orElseThrow().getId()).sorted().limit(LlmEvaluationSupport.PAGE)
                        .toList());
        set(new Phrase("the cheapest product of each brand", "самый дешёвый товар каждого бренда",
                "每个品牌最便宜的商品", "各ブランドで最も安い商品"), Map.of(),
                () -> data.products.findAll().stream()
                        .collect(Collectors.groupingBy(Product::getBrand, Collectors.minBy(
                                Comparator.comparing(Product::getPrice).thenComparing(Product::getId))))
                        .values().stream().map(p -> p.orElseThrow().getId()).sorted().limit(LlmEvaluationSupport.PAGE)
                        .toList());

        Phrase afterDiscount = new Phrase("{cat} under {price} dollars after discount",
                "{cat} дешевле {price} долларов с учётом скидки", "折后价格低于{price}美元的{cat}",
                "割引後の価格が{price}ドル未満の{cat}");
        for (Object[] pair : List.of(new Object[] { Category.PHONE, 500 }, new Object[] { Category.LAPTOP, 1000 },
                new Object[] { Category.TABLET, 400 })) {
            Category category = (Category) pair[0];
            int price = (Integer) pair[1];
            set(afterDiscount, Map.of("cat", category, "price", price), () -> data.productIds(
                    p -> p.getCategory() == category && below(LlmEvaluationSupport.effectivePrice(p), price)));
        }
        Phrase discounted = new Phrase("discounted {cat}", "{cat} со скидкой", "打折的{cat}", "割引中の{cat}");
        for (Category category : List.of(Category.LAPTOP, Category.PHONE, Category.MONITOR)) {
            set(discounted, Map.of("cat", category),
                    () -> data.productIds(p -> p.getCategory() == category && p.getDiscount() != null));
        }

        Phrase eitherBrand = new Phrase("{b1} or {b2} {cat}", "{cat} {b1} или {b2}", "{b1}或{b2}的{cat}",
                "{b1}か{b2}の{cat}");
        for (Object[] triple : List.of(new Object[] { "Samsung", "Google", Category.PHONE },
                new Object[] { "Sony", "Bose", Category.HEADPHONES },
                new Object[] { "Dell", "LG", Category.MONITOR })) {
            Set<String> pair = Set.of((String) triple[0], (String) triple[1]);
            Category category = (Category) triple[2];
            set(eitherBrand, Map.of("b1", triple[0], "b2", triple[1], "cat", category),
                    () -> data.productIds(p -> pair.contains(p.getBrand()) && p.getCategory() == category));
        }
        Phrase inStock = new Phrase("{brand} {cat} under {price} dollars that are in stock",
                "{cat} {brand} дешевле {price} долларов в наличии", "有现货、价格低于{price}美元的{brand}{cat}",
                "在庫がある{price}ドル未満の{brand}の{cat}");
        for (Object[] triple : List.of(new Object[] { "Apple", Category.LAPTOP, 2000 },
                new Object[] { "Samsung", Category.PHONE, 1000 }, new Object[] { "Sony", Category.HEADPHONES, 400 })) {
            String brand = (String) triple[0];
            Category category = (Category) triple[1];
            int price = (Integer) triple[2];
            set(inStock, Map.of("brand", brand, "cat", category, "price", price), () -> data.productIds(
                    p -> p.getBrand().equals(brand) && p.getCategory() == category && below(p.getPrice(), price)
                            && p.getStock() > 0));
        }
        Phrase addedIn = new Phrase("products added to the catalog in {year}",
                "товары, добавленные в каталог в {year} году",
                "{year}年上架的商品", "{year}年に追加された商品");
        for (int year : List.of(2024, 2025)) {
            set(addedIn, Map.of("year", year), () -> data.productIds(p -> inYear(p.getCreatedAt(), year)));
        }
    }

    private void customers() {
        Phrase from = new Phrase("customers from {country}", "клиенты из {country}", "来自{country}的客户",
                "{country}の顧客");
        Phrase vipFrom = new Phrase("VIP customers from {country}", "VIP-клиенты из {country}",
                "来自{country}的VIP客户", "{country}のVIP顧客");
        for (String country : List.of("DE", "FR", "US", "GB", "JP", "IT")) {
            set(from, Map.of("country", COUNTRIES.get(country)),
                    () -> customerIds(c -> c.getCountry().equals(country)));
            set(vipFrom, Map.of("country", COUNTRIES.get(country)),
                    () -> customerIds(c -> c.isVip() && c.getCountry().equals(country)));
        }
        Phrase livingIn = new Phrase("customers living in {city}", "клиенты из города {city}", "住在{city}的客户",
                "{city}在住の顧客");
        CITIES.forEach((city, phrase) -> set(livingIn, Map.of("city", phrase),
                () -> customerIds(c -> c.getCity().equals(city))));
        groups(new Phrase("how many customers do we have in each country?", "сколько клиентов в каждой стране?",
                "每个国家有多少客户？", "国ごとの顧客数は？"), Map.of(),
                () -> count(data.customers.findAll(), Customer::getCountry));
        single(new Phrase("how many VIP customers are there?", "сколько у нас VIP-клиентов?", "有多少VIP客户？",
                "VIP顧客は何人いますか？"), Map.of(),
                () -> BigDecimal.valueOf(data.customers.findAll().stream().filter(Customer::isVip).count()));
        Phrase hasOrder = new Phrase("customers who have a {status} order", "клиенты, у которых есть {status} заказ",
                "有{status}订单的客户", "{status}の注文がある顧客");
        STATUS_SINGULAR.forEach((status, phrase) -> set(hasOrder, Map.of("status", phrase), () -> {
            Set<Long> ids = data.orders.findAll().stream().filter(o -> o.getStatus() == status)
                    .map(Order::getCustomerId).collect(Collectors.toSet());
            return customerIds(c -> ids.contains(c.getId()));
        }));
        ordered(new Phrase("customers with an order over 5000, biggest order first",
                "клиенты с заказом дороже 5000, сначала с самым крупным заказом",
                "有超过5000的订单的客户，按最大订单排序", "5000を超える注文がある顧客を最大の注文順に"), Map.of(),
                () -> data.orders.findAll().stream()
                        .collect(Collectors.toMap(Order::getCustomerId, Order::getTotal, BigDecimal::max))
                        .entrySet().stream().filter(e -> e.getValue().compareTo(dec(5000)) > 0)
                        .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed()
                                .thenComparing(Map.Entry.comparingByKey()))
                        .map(Map.Entry::getKey).limit(LlmEvaluationSupport.PAGE).toList());
    }

    private void orders() {
        Phrase byStatus = new Phrase("{status} orders", "{status} заказы", "{status}的订单", "{status}の注文");
        Phrase byStatusOver = new Phrase("{status} orders over {amount} dollars",
                "{status} заказы на сумму больше {amount} долларов", "金额超过{amount}美元的{status}订单",
                "{amount}ドルを超える{status}の注文");
        Phrase vipStatus = new Phrase("{status} orders of VIP customers", "{status} заказы VIP-клиентов",
                "VIP客户的{status}订单", "VIP顧客の{status}の注文");
        int i = 0;
        for (OrderStatus status : OrderStatus.values()) {
            int amount = List.of(1000, 3000, 5000).get(i++ % 3);
            set(byStatus, Map.of("status", STATUSES.get(status)), () -> data.orderIds(o -> o.getStatus() == status));
            set(byStatusOver, Map.of("status", STATUSES.get(status), "amount", amount),
                    () -> data.orderIds(o -> o.getStatus() == status && o.getTotal().compareTo(dec(amount)) > 0));
            set(vipStatus, Map.of("status", STATUSES.get(status)), () -> {
                Set<Long> vip = data.customers.findAll().stream().filter(Customer::isVip).map(Customer::getId)
                        .collect(Collectors.toSet());
                return data.orderIds(o -> o.getStatus() == status && vip.contains(o.getCustomerId()));
            });
        }
        Phrase placedIn = new Phrase("orders placed in {year}", "заказы, оформленные в {year} году", "{year}年下的订单",
                "{year}年の注文");
        for (int year : List.of(2024, 2025, 2026)) {
            set(placedIn, Map.of("year", year), () -> data.orderIds(o -> inYear(o.getCreatedAt(), year)));
        }
        Phrase inMonth = new Phrase("orders from {month} {year}", "заказы за {month} {year} года", "{year}年{month}月的订单",
                "{year}年{month}月の注文");
        for (int[] ym : List.of(new int[] { 2025, 3 }, new int[] { 2026, 2 }, new int[] { 2024, 12 })) {
            set(inMonth, Map.of("year", ym[0], "month", MONTHS.get(ym[1] - 1)), () -> data.orderIds(o -> {
                ZonedDateTime at = o.getCreatedAt().atZoneSameInstant(zone);
                return at.getYear() == ym[0] && at.getMonthValue() == ym[1];
            }));
        }
        Phrase items = new Phrase("orders with exactly {n} items", "заказы, в которых ровно {n} товаров",
                "恰好包含{n}件商品的订单", "商品がちょうど{n}点の注文");
        for (int n : List.of(5, 6, 8)) {
            set(items, Map.of("n", n), () -> data.orderIds(o -> o.getItemsCount() == n));
        }
        Phrase fromCountry = new Phrase("orders from customers in {country}", "заказы клиентов из {country}",
                "来自{country}客户的订单", "{country}の顧客からの注文");
        for (String country : List.of("DE", "FR", "US", "GB", "JP")) {
            set(fromCountry, Map.of("country", COUNTRIES.get(country)), () -> {
                Set<Long> ids = data.customers.findAll().stream().filter(c -> c.getCountry().equals(country))
                        .map(Customer::getId).collect(Collectors.toSet());
                return data.orderIds(o -> ids.contains(o.getCustomerId()));
            });
        }
        groups(new Phrase("how many orders are there per status?", "сколько заказов в каждом статусе?",
                "每种状态的订单有多少？", "ステータス別の注文数は？"), Map.of(),
                () -> count(data.orders.findAll(), o -> o.getStatus().name()));
        Phrase revenue = new Phrase("total revenue of {status} orders", "общая сумма {status} заказов",
                "{status}订单的总金额", "{status}の注文の合計金額");
        STATUS_GENITIVE.forEach((status, phrase) -> single(revenue, Map.of("status", phrase),
                () -> data.orders.findAll().stream().filter(o -> o.getStatus() == status).map(Order::getTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)));
        set(new Phrase("the latest order of every VIP customer", "последний заказ каждого VIP-клиента",
                "每个VIP客户的最新订单", "各VIP顧客の最新の注文"), Map.of(), () -> {
                    Set<Long> vip = data.customers.findAll().stream().filter(Customer::isVip).map(Customer::getId)
                            .collect(Collectors.toSet());
                    Comparator<Order> newest = Comparator.comparing(Order::getCreatedAt).reversed()
                            .thenComparing(Order::getId, Comparator.reverseOrder());
                    return data.orders.findAll().stream().filter(o -> vip.contains(o.getCustomerId()))
                            .collect(Collectors.groupingBy(Order::getCustomerId, Collectors.minBy(newest)))
                            .values().stream().map(o -> o.orElseThrow()).sorted(newest).map(Order::getId)
                            .limit(LlmEvaluationSupport.PAGE).toList();
                });
        ordered(new Phrase("paid orders sorted by customer name", "оплаченные заказы, отсортированные по имени клиента",
                "按客户姓名排序的已支付订单", "顧客名順の支払い済みの注文"), Map.of(),
                () -> data.jdbc.sql(
                        "SELECT o.id FROM orders o JOIN customer c ON c.id = o.customer_id WHERE o.status = 'PAID' "
                                + "ORDER BY c.full_name, o.created_at DESC, o.id DESC LIMIT "
                                + LlmEvaluationSupport.PAGE)
                        .query(Long.class).list());
    }

    private void declined() {
        for (Phrase phrase : List.of(
                new Phrase("my orders", "мои заказы", "我的订单", "私の注文"),
                new Phrase("orders near me", "заказы рядом со мной", "我附近的订单", "近くの注文"),
                new Phrase("which employees work in the warehouse?", "какие сотрудники работают на складе?",
                        "哪些员工在仓库工作？", "倉庫で働いている従業員は？"),
                new Phrase("products sorted by supplier cost", "товары, отсортированные по закупочной цене поставщика",
                        "按供应商成本排序的商品", "仕入れ原価順の商品"),
                new Phrase("customers whose email ends with example.com",
                        "клиенты, у которых почта заканчивается на example.com", "邮箱以example.com结尾的客户",
                        "メールアドレスがexample.comで終わる顧客"))) {
            for (Lang lang : Lang.values()) {
                cases.add(Case.declined(phrase.in(lang)).in(lang.name().toLowerCase(Locale.ROOT)));
            }
        }
    }

    private void set(Phrase phrase, Map<String, Object> values, Supplier<List<Long>> ids) {
        add(phrase, values, question -> Case.set(question, ids));
    }

    private void ordered(Phrase phrase, Map<String, Object> values, Supplier<List<Long>> ids) {
        add(phrase, values, question -> Case.ordered(question, ids));
    }

    private void groups(Phrase phrase, Map<String, Object> values, Supplier<Map<String, BigDecimal>> groups) {
        add(phrase, values, question -> Case.groups(question, groups));
    }

    private void single(Phrase phrase, Map<String, Object> values, Supplier<BigDecimal> value) {
        groups(phrase, values, () -> Map.of("", value.get()));
    }

    private void add(Phrase phrase, Map<String, Object> values, Function<String, Case> factory) {
        for (Lang lang : Lang.values()) {
            String question = phrase.in(lang);
            for (Map.Entry<String, Object> value : values.entrySet()) {
                question = question.replace("{" + value.getKey() + "}", word(value.getValue(), lang));
            }
            cases.add(factory.apply(question).in(lang.name().toLowerCase(Locale.ROOT)));
        }
    }

    private static String word(Object value, Lang lang) {
        return switch (value) {
            case Category category -> CATEGORIES.get(category).in(lang);
            case Phrase phrase -> phrase.in(lang);
            case Items items -> lang == Lang.RU ? items.russian() : "";
            default -> String.valueOf(value);
        };
    }

    private record Items(String russian) {
    }

    private static Items items(int n) {
        int last = n % 10;
        return new Items(last >= 2 && last <= 4 && (n % 100 < 12 || n % 100 > 14) ? "товара" : "товаров");
    }

    private List<Product> inCategory(Category category) {
        return data.products.findAll().stream().filter(p -> p.getCategory() == category).toList();
    }

    private List<Long> top(Category category, int n, Comparator<Product> order) {
        return inCategory(category).stream().sorted(order.thenComparing(Product::getId)).limit(n)
                .map(Product::getId).toList();
    }

    private List<Long> customerIds(Predicate<Customer> predicate) {
        return data.customers.findAll().stream().filter(predicate).map(Customer::getId).sorted()
                .limit(LlmEvaluationSupport.PAGE).toList();
    }

    private boolean inYear(OffsetDateTime at, int year) {
        return at.atZoneSameInstant(zone).getYear() == year;
    }

    private static <T> Map<String, BigDecimal> count(List<T> rows, Function<T, String> key) {
        return rows.stream().collect(Collectors.groupingBy(key,
                Collectors.collectingAndThen(Collectors.counting(), BigDecimal::valueOf)));
    }

    private static BigDecimal average(List<Product> rows, Function<Product, BigDecimal> value) {
        return rows.stream().map(value).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(rows.size()), 6, RoundingMode.HALF_UP);
    }

    private static boolean below(BigDecimal value, int limit) {
        return value.compareTo(dec(limit)) < 0;
    }

    private static BigDecimal dec(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}

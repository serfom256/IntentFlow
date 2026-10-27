package io.github.serfom256.intentflow.examples.shop.db;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class BulkDemoData implements CustomTaskChange {

    static final int PRODUCTS = 1500;
    static final int CUSTOMERS = 1000;
    static final int ORDERS = 3000;

    private static final long SEED = 20261015L;
    private static final int BATCH = 500;
    private static final Instant FIRST = LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC);
    private static final Instant LAST = LocalDate.of(2026, 10, 10).atStartOfDay().toInstant(ZoneOffset.UTC);

    private record Line(String category, String brand, String model, int minPrice, int maxPrice,
            List<String> variants) {
    }

    private static final List<Line> LINES = List.of(
            new Line("LAPTOP", "Apple", "MacBook Air", 999, 1899, List.of("13 M3", "15 M3", "13 M4", "15 M4")),
            new Line("LAPTOP", "Apple", "MacBook Pro", 1599, 3999,
                    List.of("14 M4", "14 M4 Pro", "16 M4 Pro", "16 M4 Max")),
            new Line("LAPTOP", "Lenovo", "ThinkPad", 899, 2499,
                    List.of("X1 Carbon Gen 12", "T14 Gen 5", "E14 Gen 6", "P1 Gen 7")),
            new Line("LAPTOP", "Lenovo", "IdeaPad", 449, 999, List.of("Slim 3", "Slim 5", "Pro 5", "Flex 5")),
            new Line("LAPTOP", "Dell", "XPS", 1099, 2799, List.of("13", "14", "16")),
            new Line("LAPTOP", "Dell", "Latitude", 799, 1999, List.of("5450", "7450", "9450")),
            new Line("LAPTOP", "HP", "Spectre x360", 1199, 1999, List.of("14", "16")),
            new Line("LAPTOP", "HP", "EliteBook", 899, 2199, List.of("840 G11", "860 G11", "1040 G11")),
            new Line("LAPTOP", "ASUS", "Zenbook", 799, 1799, List.of("14 OLED", "S 13", "Duo")),
            new Line("LAPTOP", "ASUS", "ROG", 1299, 3499, List.of("Zephyrus G14", "Zephyrus G16", "Strix G18")),
            new Line("LAPTOP", "Acer", "Swift", 599, 1399, List.of("Go 14", "X 14", "Edge 16")),
            new Line("PHONE", "Apple", "iPhone", 699, 1599,
                    List.of("16", "16 Plus", "16 Pro", "16 Pro Max", "17", "17 Pro")),
            new Line("PHONE", "Samsung", "Galaxy", 249, 1799,
                    List.of("S25", "S25+", "S25 Ultra", "A36", "A56", "Z Flip 7", "Z Fold 7")),
            new Line("PHONE", "Google", "Pixel", 499, 1799,
                    List.of("9", "9 Pro", "9 Pro XL", "9a", "10", "10 Pro Fold")),
            new Line("PHONE", "OnePlus", "OnePlus", 399, 999, List.of("13", "13R", "Nord 5")),
            new Line("PHONE", "Xiaomi", "Xiaomi", 299, 1299, List.of("15", "15 Ultra", "Redmi Note 14 Pro")),
            new Line("PHONE", "Nothing", "Phone", 349, 799, List.of("3", "3a", "3a Pro")),
            new Line("TABLET", "Apple", "iPad", 349, 2199,
                    List.of("11", "Air 11", "Air 13", "Pro 11", "Pro 13", "mini")),
            new Line("TABLET", "Samsung", "Galaxy Tab", 249, 1399, List.of("S10", "S10+", "S10 Ultra", "A9+")),
            new Line("TABLET", "Lenovo", "Tab", 199, 799, List.of("P12", "M11", "Plus")),
            new Line("TABLET", "Microsoft", "Surface Pro", 999, 2199, List.of("11", "11 OLED")),
            new Line("HEADPHONES", "Apple", "AirPods", 129, 549, List.of("4", "Pro 2", "Max")),
            new Line("HEADPHONES", "Sony", "WH-1000XM", 299, 449, List.of("5", "6")),
            new Line("HEADPHONES", "Sony", "WF-1000XM", 199, 299, List.of("5")),
            new Line("HEADPHONES", "Bose", "QuietComfort", 199, 449, List.of("Ultra", "Ultra Earbuds", "45")),
            new Line("HEADPHONES", "Sennheiser", "Momentum", 249, 449, List.of("4", "True Wireless 4")),
            new Line("HEADPHONES", "Samsung", "Galaxy Buds", 99, 249, List.of("3", "3 Pro", "FE")),
            new Line("HEADPHONES", "JBL", "Tour", 199, 349, List.of("One M2", "Pro 3")),
            new Line("MONITOR", "Dell", "UltraSharp", 399, 1299, List.of("U2724D", "U2723QE", "U3224KB", "U4025QW")),
            new Line("MONITOR", "LG", "UltraGear", 299, 1499, List.of("27GR95QE", "32GS95UE", "45GR95QE")),
            new Line("MONITOR", "Samsung", "Odyssey", 349, 1799, List.of("G7", "G8 OLED", "G9 OLED", "Neo G9")),
            new Line("MONITOR", "ASUS", "ProArt", 399, 1499, List.of("PA279CRV", "PA32UCR", "PA278CGV")),
            new Line("MONITOR", "Apple", "Studio Display", 1599, 2299, List.of("Standard Glass", "Nano-texture")),
            new Line("ACCESSORY", "Logitech", "MX", 49, 179,
                    List.of("Master 3S", "Keys S", "Anywhere 3S", "Mechanical")),
            new Line("ACCESSORY", "Apple", "Magic", 79, 199, List.of("Keyboard", "Mouse", "Trackpad")),
            new Line("ACCESSORY", "Anker", "USB-C Hub", 29, 99, List.of("7-in-1", "10-in-1", "13-in-1")),
            new Line("ACCESSORY", "Belkin", "BoostCharge", 29, 149,
                    List.of("Pro 3-in-1", "Pro 2-in-1", "Power Bank 10K")),
            new Line("ACCESSORY", "Samsung", "T9 Portable SSD", 99, 349, List.of("1TB", "2TB", "4TB")));

    private static final List<String> EDITIONS = List.of("", "", "", " (2025)", " (2026)", " Refurbished", " Bundle");

    private static final List<String> FIRST_NAMES = List.of("Ada", "Alan", "Alice", "Amir", "Ana", "Ben", "Carla",
            "Chen", "Clara", "Daniel", "Diego", "Elena", "Emma", "Erik", "Fatima", "Felix", "Grace", "Hana", "Hugo",
            "Ines", "Ivan", "Jack", "Julia", "Kai", "Karin", "Lars", "Laura", "Leo", "Lina", "Lucas", "Maya", "Marco",
            "Maria", "Mateo", "Mia", "Nadia", "Noah", "Nora", "Olga", "Omar", "Oscar", "Paula", "Pedro", "Priya",
            "Rafael", "Rosa", "Sam", "Sara", "Sofia", "Sven", "Tara", "Tom", "Uma", "Victor", "Wen", "Yara", "Yuki",
            "Zoe");

    private static final List<String> LAST_NAMES = List.of("Anders", "Becker", "Bianchi", "Brown", "Chen", "Costa",
            "Dubois", "Eriksson", "Fernandez", "Fischer", "Garcia", "Hansen", "Ito", "Jansen", "Kim", "Kowalski",
            "Larsen", "Lee", "Lopez", "Martin", "Meyer", "Moreau", "Muller", "Nakamura", "Novak", "Nowak", "Olsen",
            "Patel", "Petrov", "Rossi", "Santos", "Schmidt", "Silva", "Smith", "Tanaka", "Taylor", "Wagner", "Wang",
            "Weber", "Wilson", "Young", "Zhang");

    private static final List<Map.Entry<String, String>> CITIES = List.of(Map.entry("Berlin", "DE"),
            Map.entry("Hamburg", "DE"), Map.entry("Munich", "DE"), Map.entry("Paris", "FR"), Map.entry("Lyon", "FR"),
            Map.entry("London", "GB"), Map.entry("Manchester", "GB"), Map.entry("Madrid", "ES"),
            Map.entry("Barcelona", "ES"), Map.entry("Rome", "IT"), Map.entry("Milan", "IT"),
            Map.entry("Amsterdam", "NL"), Map.entry("Warsaw", "PL"), Map.entry("Prague", "CZ"),
            Map.entry("Stockholm", "SE"), Map.entry("Oslo", "NO"), Map.entry("Copenhagen", "DK"),
            Map.entry("New York", "US"), Map.entry("San Francisco", "US"), Map.entry("Chicago", "US"),
            Map.entry("Austin", "US"), Map.entry("Toronto", "CA"), Map.entry("Vancouver", "CA"),
            Map.entry("Tokyo", "JP"), Map.entry("Osaka", "JP"), Map.entry("Seoul", "KR"), Map.entry("Singapore", "SG"),
            Map.entry("Sydney", "AU"), Map.entry("Melbourne", "AU"), Map.entry("Sao Paulo", "BR"));

    private static final List<String> STATUSES = List.of("DELIVERED", "DELIVERED", "DELIVERED", "DELIVERED", "PAID",
            "PAID", "PAID", "SHIPPED", "SHIPPED", "NEW", "NEW", "CANCELLED");

    @Override
    public void execute(Database database) throws CustomChangeException {
        Connection connection = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
        Random rnd = new Random(SEED);
        try {
            insertProducts(connection, rnd, PRODUCTS - count(connection, "product"));
            insertCustomers(connection, rnd, CUSTOMERS - count(connection, "customer"));
            insertOrders(connection, rnd, ORDERS - count(connection, "orders"), ids(connection));
        } catch (SQLException e) {
            throw new CustomChangeException("Generating demo data failed", e);
        }
    }

    private static void insertProducts(Connection connection, Random rnd, int rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO product "
                + "(name, brand, category, price, rating, stock, active, supplier_cost, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < rows; i++) {
                Line line = LINES.get(rnd.nextInt(LINES.size()));
                String variant = line.variants().get(rnd.nextInt(line.variants().size()));
                String name = line.model() + " " + variant + EDITIONS.get(rnd.nextInt(EDITIONS.size()));
                BigDecimal price = BigDecimal
                        .valueOf(line.minPrice() + rnd.nextInt(line.maxPrice() - line.minPrice() + 1))
                        .subtract(rnd.nextBoolean() ? BigDecimal.ZERO : new BigDecimal("0.01"));
                insert.setString(1, name);
                insert.setString(2, line.brand());
                insert.setString(3, line.category());
                insert.setBigDecimal(4, price);
                insert.setBigDecimal(5, BigDecimal.valueOf(25 + rnd.nextInt(26), 1));
                insert.setInt(6, rnd.nextInt(100) < 15 ? 0 : 1 + rnd.nextInt(200));
                insert.setBoolean(7, rnd.nextInt(100) < 92);
                insert.setBigDecimal(8,
                        price.multiply(BigDecimal.valueOf(55 + rnd.nextInt(26), 2)).setScale(2, RoundingMode.HALF_UP));
                insert.setTimestamp(9, timestamp(rnd));
                insert.addBatch();
                flush(insert, i);
            }
            insert.executeBatch();
        }
    }

    private static void insertCustomers(Connection connection, Random rnd, int rows) throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO customer "
                + "(full_name, email, city, country, vip, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int i = 0; i < rows; i++) {
                String first = FIRST_NAMES.get(rnd.nextInt(FIRST_NAMES.size()));
                String last = LAST_NAMES.get(rnd.nextInt(LAST_NAMES.size()));
                Map.Entry<String, String> city = CITIES.get(rnd.nextInt(CITIES.size()));
                insert.setString(1, first + " " + last);
                insert.setString(2, (first + "." + last + "." + (i + 1)).toLowerCase(Locale.ROOT) + "@mail.example");
                insert.setString(3, city.getKey());
                insert.setString(4, city.getValue());
                insert.setBoolean(5, rnd.nextInt(100) < 12);
                insert.setTimestamp(6, timestamp(rnd));
                insert.addBatch();
                flush(insert, i);
            }
            insert.executeBatch();
        }
    }

    private static void insertOrders(Connection connection, Random rnd, int rows, List<Long> customers)
            throws SQLException {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO orders "
                + "(customer_id, status, total, items_count, created_at) VALUES (?, ?, ?, ?, ?)")) {
            for (int i = 0; i < rows; i++) {
                int items = 1 + rnd.nextInt(8);
                insert.setLong(1, customers.get(rnd.nextInt(customers.size())));
                insert.setString(2, STATUSES.get(rnd.nextInt(STATUSES.size())));
                insert.setBigDecimal(3, BigDecimal.valueOf(items * (2_000 + rnd.nextInt(80_000)), 2));
                insert.setInt(4, items);
                insert.setTimestamp(5, timestamp(rnd));
                insert.addBatch();
                flush(insert, i);
            }
            insert.executeBatch();
        }
    }

    private static void flush(PreparedStatement insert, int i) throws SQLException {
        if ((i + 1) % BATCH == 0) {
            insert.executeBatch();
        }
    }

    private static Timestamp timestamp(Random rnd) {
        long span = LAST.getEpochSecond() - FIRST.getEpochSecond();
        return Timestamp.from(FIRST.plusSeconds((long) (rnd.nextDouble() * span) / 3600 * 3600));
    }

    private static int count(Connection connection, String table) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT COUNT(*) FROM " + table);
                ResultSet rs = select.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static List<Long> ids(Connection connection) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement select = connection.prepareStatement("SELECT id FROM customer ORDER BY id");
                ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    @Override
    public String getConfirmationMessage() {
        return "Demo data generated: " + PRODUCTS + " products, " + CUSTOMERS + " customers, " + ORDERS + " orders";
    }

    @Override
    public void setUp() {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}

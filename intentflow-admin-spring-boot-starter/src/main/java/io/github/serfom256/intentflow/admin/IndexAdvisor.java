package io.github.serfom256.intentflow.admin;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IndexAdvisor {

    public record Suggestion(String table, String reason, String ddl) {
    }

    public record Advice(@Nullable String plan, List<Suggestion> suggestions, @Nullable String error) {

        public Advice {
            suggestions = List.copyOf(suggestions);
        }

        static Advice failed(String error) {
            return new Advice(null, List.of(), error);
        }
    }

    private record Scan(String table, String condition, List<String> sortKeys) {
    }

    private enum Engine {
        POSTGRES, MYSQL
    }

    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");
    private static final Pattern IDENTIFIER = Pattern.compile("[`\"]?([A-Za-z_][A-Za-z0-9_]*)\\b[`\"]?(?!\\s*\\()");
    private static final Pattern LOWER = Pattern.compile(
            "lower\\(\\(?(?:[`\"]?\\w+[`\"]?\\.)*[`\"]?(\\w+)[`\"]?\\)?(?:::\\w+)?\\)\\s*(~~\\*?|like|=|in\\b)",
            Pattern.CASE_INSENSITIVE);

    private final DataSource dataSource;
    private final JsonParser json = JsonParserFactory.getJsonParser();

    public IndexAdvisor(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public @Nullable Advice advise(String sql, List<?> values) {
        try (Connection connection = dataSource.getConnection()) {
            Engine engine = engine(connection.getMetaData());
            if (engine == null) {
                return null;
            }
            String plan = explain(connection, engine, sql, values);
            List<Scan> scans = new ArrayList<>();
            if (engine == Engine.POSTGRES) {
                json.parseList(plan).forEach(root -> postgresNode(map(root).get("Plan"), List.of(), scans));
            } else {
                mysqlNode(json.parseMap(plan), scans);
            }
            List<Suggestion> suggestions = new ArrayList<>();
            for (Scan scan : scans) {
                suggestions.addAll(suggest(connection.getMetaData(), connection.getCatalog(), engine, scan));
            }
            return new Advice(plan, suggestions.stream().distinct().toList(), null);
        } catch (SQLException | RuntimeException e) {
            return Advice.failed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static @Nullable Engine engine(DatabaseMetaData metaData) throws SQLException {
        String product = metaData.getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("postgres")) {
            return Engine.POSTGRES;
        }
        return product.contains("mysql") || product.contains("mariadb") ? Engine.MYSQL : null;
    }

    private static String explain(Connection connection, Engine engine, String sql, List<?> values)
            throws SQLException {
        String explain = (engine == Engine.POSTGRES ? "EXPLAIN (FORMAT JSON) " : "EXPLAIN FORMAT=JSON ") + sql;
        try (PreparedStatement statement = connection.prepareStatement(explain)) {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
            try (ResultSet rs = statement.executeQuery()) {
                StringBuilder plan = new StringBuilder();
                while (rs.next()) {
                    plan.append(rs.getString(1));
                }
                return plan.toString();
            }
        }
    }

    private static void postgresNode(@Nullable Object value, List<String> sortKeys, List<Scan> scans) {
        Map<String, Object> node = map(value);
        String type = String.valueOf(node.get("Node Type"));
        List<String> keys = type.endsWith("Sort") && node.get("Sort Key") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : sortKeys;
        if (type.equals("Seq Scan") && node.get("Relation Name") instanceof String table) {
            scans.add(new Scan(table, String.valueOf(node.getOrDefault("Filter", "")), keys));
        }
        if (node.get("Plans") instanceof List<?> children) {
            children.forEach(child -> postgresNode(child, keys, scans));
        }
    }

    private static void mysqlNode(@Nullable Object value, List<Scan> scans) {
        if (value instanceof List<?> list) {
            list.forEach(item -> mysqlNode(item, scans));
            return;
        }
        if (!(value instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> node = map(value);
        Object access = node.get("access_type");
        if (("ALL".equals(access) || "index".equals(access)) && node.get("table_name") instanceof String table) {
            scans.add(new Scan(table, String.valueOf(node.getOrDefault("attached_condition", "")), List.of()));
        }
        node.values().forEach(child -> mysqlNode(child, scans));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(@Nullable Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static List<Suggestion> suggest(DatabaseMetaData metaData, String catalog, Engine engine, Scan scan)
            throws SQLException {
        Set<String> columns = columns(metaData, catalog, scan.table());
        if (columns.isEmpty()) {
            return List.of();
        }
        List<Suggestion> suggestions = new ArrayList<>();
        String condition = STRING_LITERAL.matcher(scan.condition()).replaceAll("''");
        Set<String> lowered = new LinkedHashSet<>();
        Matcher lower = LOWER.matcher(condition);
        while (lower.find()) {
            String column = column(columns, lower.group(1));
            if (column == null || !lowered.add(column)) {
                continue;
            }
            boolean contains = !lower.group(2).equals("=") && !lower.group(2).equalsIgnoreCase("in");
            if (contains && engine == Engine.POSTGRES) {
                suggestions.add(new Suggestion(scan.table(),
                        "contains search on " + column + "; trigram indexes need CREATE EXTENSION pg_trgm",
                        "CREATE INDEX " + name(scan.table(), column, "trgm") + " ON " + quote(engine, scan.table())
                                + " USING gin (lower(" + quote(engine, column) + ") gin_trgm_ops);"));
            } else if (!contains) {
                suggestions.add(new Suggestion(scan.table(), "case-insensitive match on " + column,
                        "CREATE INDEX " + name(scan.table(), column, "lower") + " ON " + quote(engine, scan.table())
                                + " ((lower(" + quote(engine, column) + ")));"));
            }
        }
        Set<String> filtered = new LinkedHashSet<>();
        Matcher identifier = IDENTIFIER.matcher(LOWER.matcher(condition).replaceAll(""));
        while (identifier.find()) {
            String column = column(columns, identifier.group(1));
            if (column != null && !lowered.contains(column) && filtered.size() < 3) {
                filtered.add(column);
            }
        }
        List<String> indexed = new ArrayList<>(filtered);
        List<String> definition = new ArrayList<>(filtered.stream().map(c -> quote(engine, c)).toList());
        for (String key : scan.sortKeys()) {
            String[] parts = key.strip().split("\\s+", 2);
            String column = column(columns, parts[0].substring(parts[0].lastIndexOf('.') + 1));
            if (column == null) {
                break;
            }
            if (!indexed.contains(column)) {
                indexed.add(column);
                definition
                        .add(quote(engine, column) + (parts.length > 1 && parts[1].startsWith("DESC") ? " DESC" : ""));
            }
        }
        if (!indexed.isEmpty() && !covered(metaData, catalog, scan.table(), indexed)) {
            String reason = (filtered.isEmpty() ? "" : "full scan filtering on " + String.join(", ", filtered))
                    + (filtered.isEmpty() || scan.sortKeys().isEmpty() ? "" : ", then ")
                    + (scan.sortKeys().isEmpty() ? "" : "sorted without an index");
            suggestions.add(new Suggestion(scan.table(), reason,
                    "CREATE INDEX " + name(scan.table(), String.join("_", indexed), "") + " ON "
                            + quote(engine, scan.table()) + " (" + String.join(", ", definition) + ");"));
        }
        return suggestions;
    }

    private static @Nullable String column(Set<String> columns, String candidate) {
        return columns.stream().filter(c -> c.equalsIgnoreCase(candidate)).findFirst().orElse(null);
    }

    private static Set<String> columns(DatabaseMetaData metaData, String catalog, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        try (ResultSet rs = metaData.getColumns(catalog, null, table, "%")) {
            while (rs.next()) {
                columns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return columns;
    }

    private static boolean covered(DatabaseMetaData metaData, String catalog, String table, List<String> columns)
            throws SQLException {
        Map<String, Map<Integer, String>> indexes = new LinkedHashMap<>();
        try (ResultSet rs = metaData.getIndexInfo(catalog, null, table, false, true)) {
            while (rs.next()) {
                String index = rs.getString("INDEX_NAME");
                String column = rs.getString("COLUMN_NAME");
                if (index != null && column != null) {
                    indexes.computeIfAbsent(index, k -> new TreeMap<>()).put((int) rs.getShort("ORDINAL_POSITION"),
                            column.replace("\"", ""));
                }
            }
        }
        return indexes.values().stream().map(c -> List.copyOf(c.values()))
                .anyMatch(existing -> existing.size() >= columns.size()
                        && existing.subList(0, columns.size()).stream().map(c -> c.toLowerCase(Locale.ROOT)).toList()
                                .equals(columns.stream().map(c -> c.toLowerCase(Locale.ROOT)).toList()));
    }

    private static String quote(Engine engine, String identifier) {
        return engine == Engine.POSTGRES ? "\"" + identifier.replace("\"", "\"\"") + "\""
                : "`" + identifier.replace("`", "``") + "`";
    }

    private static String name(String table, String columns, String suffix) {
        String name = ("ix_" + table + "_" + columns + (suffix.isEmpty() ? "" : "_" + suffix)).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]", "_");
        return name.length() > 60 ? name.substring(0, 60) : name;
    }
}

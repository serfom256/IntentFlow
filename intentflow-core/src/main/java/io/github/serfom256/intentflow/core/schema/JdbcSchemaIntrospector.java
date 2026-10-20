package io.github.serfom256.intentflow.core.schema;

import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JdbcSchemaIntrospector {

    private static final Pattern UPPER_CASE = Pattern.compile("[A-Z_][A-Z0-9_]*");

    private JdbcSchemaIntrospector() {
    }

    public static Map<String, TableFacts> introspect(DataSource dataSource, Collection<String> tables)
            throws SQLException {
        Map<String, TableFacts> facts = new LinkedHashMap<>();
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            for (String table : tables) {
                TableFacts found = table(metaData, connection, table);
                if (found != null) {
                    facts.put(table, found);
                }
            }
        }
        return facts;
    }

    private static @Nullable TableFacts table(DatabaseMetaData metaData, Connection connection, String qualified)
            throws SQLException {
        int dot = qualified.lastIndexOf('.');
        String name = dot < 0 ? qualified : qualified.substring(dot + 1);
        List<String> schemas = dot < 0
                ? Arrays.asList(currentSchema(connection), null)
                : variants(qualified.substring(0, dot));
        String catalog = connection.getCatalog();
        for (String candidate : variants(name)) {
            for (String inSchema : schemas) {
                Columns columns = columns(metaData, catalog, inSchema, candidate);
                if (columns != null) {
                    UnaryOperator<String> folded = folded(metaData);
                    return new TableFacts(
                            primaryKey(metaData, catalog, inSchema, candidate).stream().map(folded).toList(),
                            uniqueKeys(metaData, catalog, inSchema, candidate).stream()
                                    .map(key -> key.stream().map(folded).toList()).toList(),
                            names(columns.notNull(), folded), Map.of(), names(columns.zoned(), folded));
                }
            }
        }
        return null;
    }

    private static UnaryOperator<String> folded(DatabaseMetaData metaData) throws SQLException {
        if (!metaData.storesUpperCaseIdentifiers()) {
            return UnaryOperator.identity();
        }
        return name -> UPPER_CASE.matcher(name).matches() ? name.toLowerCase(Locale.ROOT) : name;
    }

    private static Set<String> names(Set<String> columns, UnaryOperator<String> folded) {
        return columns.stream().map(folded).collect(Collectors.toSet());
    }

    private static List<String> variants(String name) {
        return List.of(name, name.toLowerCase(Locale.ROOT), name.toUpperCase(Locale.ROOT));
    }

    private static @Nullable String currentSchema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException | AbstractMethodError e) {
            return null;
        }
    }

    private record Columns(Set<String> notNull, Set<String> zoned) {
    }

    private static @Nullable Columns columns(DatabaseMetaData metaData, String catalog, @Nullable String schema,
            String table) throws SQLException {
        Set<String> notNull = new HashSet<>();
        Set<String> zoned = new HashSet<>();
        boolean mysql = metaData.getDatabaseProductName().toLowerCase(Locale.ROOT).matches(".*(mysql|mariadb).*");
        boolean found = false;
        try (ResultSet rs = metaData.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                found = true;
                String column = rs.getString("COLUMN_NAME");
                if ("NO".equals(rs.getString("IS_NULLABLE"))) {
                    notNull.add(column);
                }
                if (zoned(rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"), mysql)) {
                    zoned.add(column);
                }
            }
        }
        return found ? new Columns(notNull, zoned) : null;
    }

    private static boolean zoned(int dataType, @Nullable String typeName, boolean mysql) {
        String type = typeName == null ? "" : typeName.toLowerCase(Locale.ROOT);
        return dataType == Types.TIMESTAMP_WITH_TIMEZONE || type.equals("timestamptz")
                || type.equals("timestamp with time zone") || (mysql && type.equals("timestamp"));
    }

    private static List<String> primaryKey(DatabaseMetaData metaData, String catalog, @Nullable String schema,
            String table) throws SQLException {
        Map<Short, String> columns = new TreeMap<>();
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                columns.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }
        return List.copyOf(columns.values());
    }

    private static List<List<String>> uniqueKeys(DatabaseMetaData metaData, String catalog, @Nullable String schema,
            String table) throws SQLException {
        Map<String, Map<Short, String>> indexes = new LinkedHashMap<>();
        Set<String> unusable = new HashSet<>();
        try (ResultSet rs = metaData.getIndexInfo(catalog, schema, table, true, true)) {
            while (rs.next()) {
                String index = rs.getString("INDEX_NAME");
                if (index == null || rs.getShort("TYPE") == DatabaseMetaData.tableIndexStatistic) {
                    continue;
                }
                String column = rs.getString("COLUMN_NAME");
                String filter = rs.getString("FILTER_CONDITION");
                if (column == null || column.contains("(") || (filter != null && !filter.isBlank())) {
                    unusable.add(index);
                    continue;
                }
                indexes.computeIfAbsent(index, i -> new TreeMap<>()).put(rs.getShort("ORDINAL_POSITION"), column);
            }
        }
        List<List<String>> keys = new ArrayList<>();
        indexes.forEach((index, columns) -> {
            if (!unusable.contains(index)) {
                keys.add(List.copyOf(columns.values()));
            }
        });
        return keys;
    }
}

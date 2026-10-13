package io.github.serfom256.intentflow.core.testing;

public final class ComposeDatabases {

    private ComposeDatabases() {
    }

    public static boolean enabled() {
        return "compose".equalsIgnoreCase(System.getenv("INTENTFLOW_TEST_DB"));
    }

    public static int port(String variable, int fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? fallback : Integer.parseInt(value.strip());
    }
}

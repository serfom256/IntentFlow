package io.github.serfom256.intentflow.starter.autoconfigure;

final class BootAutoConfigurations {

    static final Class<?> DATA_SOURCE = resolve(
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
    static final Class<?> JDBC_TEMPLATE = resolve(
            "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration");
    static final Class<?> JDBC_CLIENT = resolve(
            "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.JdbcClientAutoConfiguration");
    static final Class<?> HIBERNATE_JPA = resolve(
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration");

    private BootAutoConfigurations() {
    }

    private static Class<?> resolve(String boot4, String boot3) {
        try {
            return Class.forName(boot4);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName(boot3);
            } catch (ClassNotFoundException missing) {
                throw new IllegalStateException("Neither " + boot4 + " nor " + boot3 + " is on the classpath", missing);
            }
        }
    }
}

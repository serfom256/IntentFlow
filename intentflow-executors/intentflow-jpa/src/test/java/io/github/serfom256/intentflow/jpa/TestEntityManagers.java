package io.github.serfom256.intentflow.jpa;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.cfg.Configuration;

final class TestEntityManagers {

    private TestEntityManagers() {
    }

    static EntityManagerFactory create(String url, String user, String password, Class<?>... entities) {
        Configuration configuration = new Configuration()
                .setProperty("jakarta.persistence.jdbc.url", url)
                .setProperty("jakarta.persistence.jdbc.user", user)
                .setProperty("jakarta.persistence.jdbc.password", password)
                .setProperty("hibernate.hbm2ddl.auto", "none");
        for (Class<?> entity : entities) {
            configuration.addAnnotatedClass(entity);
        }
        return configuration.buildSessionFactory();
    }
}

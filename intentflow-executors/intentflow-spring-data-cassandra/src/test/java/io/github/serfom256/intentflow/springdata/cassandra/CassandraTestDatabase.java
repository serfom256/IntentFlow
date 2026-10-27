package io.github.serfom256.intentflow.springdata.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.config.DriverConfigLoader;
import io.github.serfom256.intentflow.core.testing.ComposeDatabases;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.InetSocketAddress;

final class CassandraTestDatabase {

    static final String KEYSPACE = "intentflow";

    private static InetSocketAddress contactPoint;
    private static String datacenter;

    private CassandraTestDatabase() {
    }

    static CqlSession session() {
        start();
        return builder().withKeyspace(KEYSPACE).build();
    }

    static CqlSession session(DriverConfigLoader config) {
        start();
        return builder().withKeyspace(KEYSPACE).withConfigLoader(config).build();
    }

    private static synchronized void start() {
        if (contactPoint != null) {
            return;
        }
        if (ComposeDatabases.enabled()) {
            contactPoint = new InetSocketAddress("localhost", ComposeDatabases.port("CASSANDRA_PORT", 19042));
            datacenter = "datacenter1";
        } else {
            CassandraContainer c = new CassandraContainer(DockerImageName.parse("mirror.gcr.io/library/cassandra:5.0")
                    .asCompatibleSubstituteFor("cassandra"));
            c.start();
            contactPoint = c.getContactPoint();
            datacenter = c.getLocalDatacenter();
        }
        try (CqlSession admin = builder().build()) {
            admin.execute("CREATE KEYSPACE IF NOT EXISTS " + KEYSPACE
                    + " WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
        }
    }

    private static CqlSessionBuilder builder() {
        return CqlSession.builder().addContactPoint(contactPoint).withLocalDatacenter(datacenter);
    }
}

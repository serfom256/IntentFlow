plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCassandra)
    api(platform(libs.spring.boot.dependencies))
    api(libs.spring.data.cassandra)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.testcontainers.cassandra) {
        exclude(group = "com.datastax.cassandra")
    }
}

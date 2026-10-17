plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCassandra)
    compileOnly(libs.spring.data.cassandra)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.spring.data.cassandra)
    testImplementation(libs.testcontainers.cassandra) {
        exclude(group = "com.datastax.cassandra")
    }
}

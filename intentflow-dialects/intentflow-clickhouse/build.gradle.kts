plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)

    testImplementation(libs.bundles.testing)
    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.testcontainers.clickhouse)
    testRuntimeOnly(libs.clickhouse.jdbc)
}

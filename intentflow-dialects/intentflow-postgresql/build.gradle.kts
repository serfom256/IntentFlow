plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(projects.intentflowCore)

    testFixturesApi(testFixtures(projects.intentflowCore))
    testFixturesImplementation(libs.testcontainers.postgresql)
    testFixturesRuntimeOnly(libs.postgresql)

    testImplementation(libs.bundles.testing)
}

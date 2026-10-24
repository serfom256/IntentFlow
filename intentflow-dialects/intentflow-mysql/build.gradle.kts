plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(projects.intentflowCore)

    testFixturesApi(testFixtures(projects.intentflowCore))
    testFixturesImplementation(libs.testcontainers.mysql)
    testFixturesRuntimeOnly(libs.mysql)

    testImplementation(libs.bundles.testing)
}

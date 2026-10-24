plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.oracle)

    testFixturesApi(testFixtures(projects.intentflowCore))
    testFixturesImplementation(libs.testcontainers.oracle.free)

    testImplementation(libs.bundles.testing)
    testRuntimeOnly(libs.oracle)
}

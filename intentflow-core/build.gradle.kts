plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(libs.jspecify)
    implementation(libs.caffeine)
    implementation(libs.slf4j.api)

    testFixturesApi(libs.bundles.testing)
    testFixturesApi(libs.testcontainers.junit.jupiter)

    testImplementation(libs.bundles.testing)
}

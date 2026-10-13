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
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.testcontainers.mysql)
    testFixturesApi(projects.intentflowPostgresql)
    testFixturesApi(projects.intentflowMysql)

    testImplementation(libs.bundles.testing)
    testImplementation(projects.intentflowPostgresql)
    testImplementation(projects.intentflowMysql)
}

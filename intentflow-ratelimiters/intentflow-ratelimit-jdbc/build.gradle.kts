plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.testing)
    testImplementation(testFixtures(projects.intentflowPostgresql))
    testImplementation(testFixtures(projects.intentflowMysql))
    testImplementation(testFixtures(projects.intentflowOracle))
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
    testRuntimeOnly(libs.oracle)
}

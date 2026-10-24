plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.spring.jdbc)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(testFixtures(projects.intentflowPostgresql))
    testImplementation(testFixtures(projects.intentflowMysql))
    testImplementation(testFixtures(projects.intentflowOracle))
    testImplementation(libs.spring.jdbc)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
    testRuntimeOnly(libs.oracle)
}

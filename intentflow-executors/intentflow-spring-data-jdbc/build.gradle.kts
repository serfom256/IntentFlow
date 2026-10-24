plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.spring.data.jdbc)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(testFixtures(projects.intentflowPostgresql))
    testImplementation(testFixtures(projects.intentflowMysql))
    testImplementation(testFixtures(projects.intentflowOracle))
    testImplementation(libs.spring.data.jdbc)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
    testRuntimeOnly(libs.oracle)
}

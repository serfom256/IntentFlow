plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.jakarta.persistence.api)
    compileOnly(libs.hibernate.core)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(testFixtures(projects.intentflowPostgresql))
    testImplementation(testFixtures(projects.intentflowMysql))
    testImplementation(testFixtures(projects.intentflowOracle))
    testImplementation(libs.hibernate.core)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
    testRuntimeOnly(libs.oracle)
}

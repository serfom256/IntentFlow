plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.jimmer.sql)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(testFixtures(projects.intentflowPostgresql))
    testImplementation(testFixtures(projects.intentflowMysql))
    testImplementation(testFixtures(projects.intentflowOracle))
    testImplementation(libs.jimmer.sql)
    testImplementation(libs.spring.jdbc)
    testAnnotationProcessor(libs.jimmer.apt)
    testRuntimeOnly(libs.jackson3.databind)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
    testRuntimeOnly(libs.oracle)
}

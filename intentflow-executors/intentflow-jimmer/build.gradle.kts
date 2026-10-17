plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.jimmer.sql)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.jimmer.sql)
    testImplementation(libs.spring.jdbc)
    testAnnotationProcessor(libs.jimmer.apt)
    testRuntimeOnly(libs.jackson3.databind)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

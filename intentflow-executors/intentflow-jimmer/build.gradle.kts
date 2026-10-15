plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    api(platform(libs.spring.boot.dependencies))
    api(libs.jimmer.sql)
    runtimeOnly(libs.jackson.databind)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.spring.jdbc)
    testAnnotationProcessor(libs.jimmer.apt)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

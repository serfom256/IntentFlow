plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.openmapper)
    compileOnly(libs.spring.jdbc)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.openmapper)
    testImplementation(libs.spring.boot.starter.jdbc)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

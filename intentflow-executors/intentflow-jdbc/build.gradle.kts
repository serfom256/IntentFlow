plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.spring.jdbc)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.spring.jdbc)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

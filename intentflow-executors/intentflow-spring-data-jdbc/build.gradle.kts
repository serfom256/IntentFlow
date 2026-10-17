plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.spring.data.jdbc)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.spring.data.jdbc)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

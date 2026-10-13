plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    api(platform(libs.spring.boot.dependencies))
    api(libs.spring.jdbc)

    testImplementation(testFixtures(projects.intentflowCore))
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

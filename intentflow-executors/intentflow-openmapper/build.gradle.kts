plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    api(platform(libs.spring.boot.dependencies))
    api(libs.openmapper)
    api(libs.spring.jdbc)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.spring.boot.starter.jdbc)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

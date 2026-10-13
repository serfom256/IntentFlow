plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    api(platform(libs.spring.boot.dependencies))
    api(libs.jakarta.persistence.api)

    testImplementation(testFixtures(projects.intentflowCore))
    testImplementation(libs.hibernate.core)
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.spring.data.redis)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.spring.data.redis)
    testImplementation(libs.lettuce)
    testImplementation(libs.testcontainers.junit.jupiter)
}

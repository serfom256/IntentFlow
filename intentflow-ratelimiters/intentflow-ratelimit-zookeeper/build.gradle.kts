plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    api(libs.curator.framework)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.curator.test)
}

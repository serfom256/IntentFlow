plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    implementation(libs.slf4j.api)

    testImplementation(libs.bundles.testing)
}

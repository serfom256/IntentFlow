plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)

    testImplementation(libs.bundles.testing)
    testImplementation(testFixtures(projects.intentflowCore))
    testRuntimeOnly(libs.h2)
}

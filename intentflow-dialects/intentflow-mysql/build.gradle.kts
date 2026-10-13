plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)

    testImplementation(libs.bundles.testing)
}

plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowOpenai)

    testImplementation(libs.bundles.testing)
}

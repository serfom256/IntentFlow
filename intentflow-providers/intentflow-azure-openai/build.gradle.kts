plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowOpenai)
    compileOnly(libs.azure.identity)

    testImplementation(libs.bundles.testing)
}

plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    implementation(libs.google.genai)

    testImplementation(libs.bundles.testing)
}

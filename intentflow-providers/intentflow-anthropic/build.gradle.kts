plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    implementation(libs.anthropic.java)

    testImplementation(libs.bundles.testing)
}

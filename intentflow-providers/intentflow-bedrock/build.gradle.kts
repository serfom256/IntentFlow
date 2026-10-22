plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.bedrockruntime)

    testImplementation(libs.bundles.testing)
}

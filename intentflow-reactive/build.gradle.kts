plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.reactor.core)

    testImplementation(libs.reactor.core)
    testImplementation(libs.bundles.testing)
    testImplementation(projects.intentflowPostgresql)
}

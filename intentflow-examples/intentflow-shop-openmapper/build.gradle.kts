plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(projects.intentflowShopDomain)
    implementation(projects.intentflowCore)
    implementation(projects.intentflowOpenmapper)
    implementation(libs.openmapper)
    implementation(libs.spring.boot.starter)
}

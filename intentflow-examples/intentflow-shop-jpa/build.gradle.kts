plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(projects.intentflowShopDomain)
    implementation(projects.intentflowCore)
    implementation(projects.intentflowJpa)
    implementation(libs.spring.boot.starter.data.jpa)
}

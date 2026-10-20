plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(projects.intentflowShopDomain)
    implementation(projects.intentflowCore)
    implementation(projects.intentflowJdbc)
    implementation(libs.spring.boot.starter.jdbc)
}

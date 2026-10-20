plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(projects.intentflowShopDomain)
    implementation(projects.intentflowCore)
    implementation(projects.intentflowJimmer)
    implementation(libs.jimmer.sql)
    annotationProcessor(libs.jimmer.apt)
    compileOnly(libs.jackson.annotations)
    implementation(libs.spring.boot.starter.jdbc)
}

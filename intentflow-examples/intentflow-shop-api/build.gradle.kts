plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(projects.intentflowSpringBootStarter)
    implementation(projects.intentflowAdminSpringBootStarter)
    implementation(projects.intentflowOpenmapper)
    implementation(libs.openmapper)
    implementation(projects.intentflowJpa)
    implementation(projects.intentflowJimmer)
    implementation(libs.jimmer.sql)
    annotationProcessor(libs.jimmer.apt)
    implementation(projects.intentflowJdbc)
    implementation(projects.intentflowPostgresql)
    implementation(projects.intentflowMysql)

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.liquibase)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mysql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.mysql)
}

tasks.test {
    useJUnitPlatform { excludeTags("llm") }
}

val ormTests = listOf("jpa", "jimmer", "jdbc").map { orm ->
    tasks.register<Test>("test${orm.replaceFirstChar(Char::uppercase)}") {
        description = "Runs the API tests with the $orm profile instead of OpenMapper."
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform { excludeTags("llm") }
        systemProperty("spring.profiles.active", orm)
    }
}

tasks.check {
    dependsOn(ormTests)
}

tasks.register<Test>("llmTest") {
    description = "Runs the natural-language evaluation suite against a live LLM."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("llm") }
    systemProperties(System.getProperties()
            .filterKeys { (it as String).startsWith("intentflow.") }
            .mapKeys { it.key as String })
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}

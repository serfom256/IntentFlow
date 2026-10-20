plugins {
    alias(libs.plugins.spring.boot)
}

val orms = mapOf(
    "jpa" to projects.intentflowShopJpa,
    "jimmer" to projects.intentflowShopJimmer,
    "jdbc" to projects.intentflowShopJdbc,
)
val orm = providers.gradleProperty("orm").orNull?.takeUnless { it == "openmapper" }
require(orm == null || orm in orms) { "-Porm must be openmapper or one of ${orms.keys}, was '$orm'" }

dependencies {
    implementation(projects.intentflowSpringBootStarter)
    implementation(projects.intentflowAdminSpringBootStarter)
    implementation(projects.intentflowShopDomain)
    implementation(projects.intentflowShopOpenmapper)
    orm?.let { implementation(orms.getValue(it)) }
    implementation(projects.intentflowPostgresql)
    implementation(projects.intentflowMysql)

    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.liquibase)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.mysql)

    testImplementation(projects.intentflowOpenmapper)
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

if (orm != null) {
    tasks.withType<JavaExec>().matching { it.name == "bootRun" || it.name == "bootTestRun" }.configureEach {
        systemProperty("spring.profiles.include", orm)
    }
}

val ormTests = orms.map { (name, module) ->
    val suffix = name.replaceFirstChar(Char::uppercase)
    val extra = configurations.dependencyScope("test${suffix}RuntimeOnly")
    dependencies.add(extra.name, module)
    val testRuntime = configurations.testRuntimeClasspath.get()
    val ormRuntime = configurations.resolvable("test${suffix}RuntimeClasspath") {
        extendsFrom(configurations.testImplementation.get(), configurations.testRuntimeOnly.get(), extra.get())
        testRuntime.attributes.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST")
            attributes.attribute(key as Attribute<Any>, testRuntime.attributes.getAttribute(key)!!)
        }
    }
    tasks.register<Test>("test$suffix") {
        description = "Runs the API tests with the $name profile instead of OpenMapper."
        group = "verification"
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().output + sourceSets.main.get().output + ormRuntime.get()
        useJUnitPlatform { excludeTags("llm") }
        systemProperty("spring.profiles.active", name)
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
    orm?.let { systemProperty("spring.profiles.include", it) }
    systemProperties(System.getProperties()
            .filterKeys { (it as String).startsWith("intentflow.") }
            .mapKeys { it.key as String })
    testLogging { events("passed", "failed", "skipped"); showStandardStreams = true }
}

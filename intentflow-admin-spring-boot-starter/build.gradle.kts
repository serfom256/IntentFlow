plugins {
    `java-library`
    alias(libs.plugins.node.gradle)
}

dependencies {
    api(projects.intentflowSpringBootStarter)
    compileOnly(libs.spring.boot.starter)
    compileOnly(libs.spring.webmvc)
    compileOnly(libs.spring.jdbc)
    compileOnly(libs.spring.security.core)
    compileOnly(libs.jakarta.servlet.api)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.webmvc)
    testImplementation(libs.spring.jdbc)
    testImplementation(libs.jakarta.servlet.api)
    testImplementation(libs.jackson2.databind)
    testImplementation(projects.intentflowPostgresql)
    testImplementation(projects.intentflowH2)
    testImplementation(libs.h2)
    testImplementation(libs.spring.security.core)
    testImplementation(libs.spring.security.web)
    testImplementation(testFixtures(projects.intentflowCore))
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.mysql)
}

val ui = layout.projectDirectory.dir("src/main/ui")
val uiOutput = layout.buildDirectory.dir("ui")

node {
    version.set("24.21.0")
    download.set(true)
    nodeProjectDir.set(ui)
}

val buildUi = tasks.register<com.github.gradle.node.npm.task.NpmTask>("buildUi") {
    description = "Type-checks and bundles the admin UI."
    dependsOn(tasks.npmInstall)
    args.set(uiOutput.map { listOf("run", "build", "--", "--outDir", it.asFile.absolutePath, "--emptyOutDir") })
    inputs.dir(ui.dir("src"))
    inputs.files(ui.files("index.html", "package.json", "package-lock.json", "tsconfig.json", "vite.config.ts"))
    outputs.dir(uiOutput)
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("devUi") {
    description = "Starts the admin UI dev server on http://localhost:5174, proxying the API to INTENTFLOW_API_URL."
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "dev"))
}

tasks.processResources {
    from(buildUi) {
        into("intentflow-admin-ui")
    }
}

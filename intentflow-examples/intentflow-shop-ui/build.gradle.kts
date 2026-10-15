plugins {
    base
    alias(libs.plugins.node.gradle)
}

node {
    version.set("24.21.0")
    download.set(true)
}

val npmBuild = tasks.register<com.github.gradle.node.npm.task.NpmTask>("npmBuild") {
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "build"))
    inputs.dir("src")
    inputs.files("index.html", "package.json", "package-lock.json", "tsconfig.json", "vite.config.ts")
    outputs.dir("dist")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("dev") {
    description = "Starts the Vite dev server on http://localhost:5173, proxying /api to the shop API."
    dependsOn(tasks.npmInstall)
    args.set(listOf("run", "dev"))
}

tasks.assemble {
    dependsOn(npmBuild)
}

tasks.clean {
    delete("dist")
}

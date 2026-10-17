allprojects {
    group = "io.github.serfom256"

    repositories {
        mavenCentral()
    }
}

subprojects {
    if (name.endsWith("-ui")) {
        return@subprojects
    }

    pluginManager.apply("java")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(providers.gradleProperty("javaVersion").orElse("21").get()))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
        options.compilerArgs.add("-parameters")
    }

    val libs = rootProject.libs
    dependencies {
        "compileOnly"(libs.lombok)
        "annotationProcessor"(libs.lombok)
        "testCompileOnly"(libs.lombok)
        "testAnnotationProcessor"(libs.lombok)
        "testRuntimeOnly"(libs.junit.platform.launcher)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    if (!projectDir.parentFile.name.equals("intentflow-examples") && name != "intentflow-core") {
        val springBoot = providers.gradleProperty("springBoot").orElse(libs.versions.springBoot).get()
        val bom = dependencies.platform("org.springframework.boot:spring-boot-dependencies:$springBoot")
        listOf("compileOnly", "annotationProcessor", "testImplementation", "testAnnotationProcessor").forEach {
            dependencies.add(it, bom)
        }
        pluginManager.withPlugin("java-test-fixtures") {
            dependencies.add("testFixturesImplementation", bom)
        }
    }

    if (!projectDir.parentFile.name.equals("intentflow-examples")) {
        pluginManager.apply("maven-publish")

        configure<JavaPluginExtension> {
            withSourcesJar()
        }

        pluginManager.withPlugin("java-test-fixtures") {
            val java = components["java"] as AdhocComponentWithVariants
            java.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
            java.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
        }

        configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    versionMapping {
                        allVariants { fromResolutionResult() }
                    }
                    pom {
                        name.set(project.name)
                        description.set("IntentFlow: plain-language questions to safe SQL, typed results")
                        url.set("https://github.com/serfom256/IntentFlow")
                        licenses {
                            license {
                                name.set("MIT License")
                                url.set("https://opensource.org/licenses/MIT")
                            }
                        }
                        developers {
                            developer {
                                id.set("serfom256")
                                name.set("serfom256")
                            }
                        }
                        scm {
                            url.set("https://github.com/serfom256/IntentFlow")
                        }
                    }
                }
            }
        }
    }
}

val versionedFiles = fileTree(rootDir) {
    include("README.md", "*/README.md", "*/*/README.md", "intentflow-examples/intentflow-shop-ui/package.json",
        "intentflow-admin-spring-boot-starter/src/main/ui/package.json")
    exclude("**/node_modules/**", "**/build/**")
}
val dependencyVersion = Regex("""(io\.github\.serfom256:intentflow-[a-z-]+:)[0-9][^"\s)]*""")
val mavenVersion = Regex("""(<artifactId>intentflow-[a-z-]+</artifactId>\s*<version>)[^<]+""")
val packageVersion = Regex("""("version":\s*")[^"]+""")

fun versioned(text: String, file: File): String {
    val version = project.version.toString()
    return if (file.name == "package.json") {
        packageVersion.replaceFirst(text, "$1$version")
    } else {
        mavenVersion.replace(dependencyVersion.replace(text, "$1$version"), "$1$version")
    }
}

tasks.register("syncVersion") {
    description = "Writes the version from gradle.properties into the READMEs and UI package.json files."
    group = "build"
    doLast {
        versionedFiles.forEach { file ->
            val text = file.readText()
            val updated = versioned(text, file)
            if (updated != text) {
                file.writeText(updated)
                logger.lifecycle("Updated ${file.relativeTo(rootDir)}")
            }
        }
    }
}

val checkVersion = tasks.register("checkVersion") {
    description = "Fails when a README or UI package.json does not match the version in gradle.properties."
    group = "verification"
    inputs.files(versionedFiles)
    inputs.property("version", project.version.toString())
    doLast {
        val stale = versionedFiles.filter { versioned(it.readText(), it) != it.readText() }
        if (!stale.isEmpty) {
            throw GradleException("Version ${project.version} is not written in " + stale.joinToString {
                it.relativeTo(rootDir).path
            } + "; run ./gradlew syncVersion")
        }
    }
}

subprojects {
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn(checkVersion)
    }
}

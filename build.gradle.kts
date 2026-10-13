allprojects {
    group = "io.github.serfom256"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
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

    if (name != "intentflow-examples") {
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

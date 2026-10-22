plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    compileOnly(libs.spring.boot.starter)
    annotationProcessor(libs.spring.boot.configuration.processor)

    compileOnly(libs.spring.jdbc)
    compileOnly(libs.spring.security.core)
    compileOnly(libs.micrometer.core)
    compileOnly(libs.micrometer.observation)
    compileOnly(libs.resilience4j.circuitbreaker)
    compileOnly(libs.spring.boot.health) {
        version { strictly(libs.versions.springBoot.get()) }
        isTransitive = false
    }
    compileOnly(libs.spring.boot.actuator) {
        version { strictly(libs.versions.springBoot3.get()) }
        isTransitive = false
    }
    compileOnly(libs.spring.orm)
    compileOnly(libs.spring.data.jdbc)
    compileOnly(libs.spring.data.cassandra)
    compileOnly(libs.jakarta.persistence.api)
    compileOnly(libs.jimmer.sql)
    compileOnly(libs.openmapper)
    compileOnly(projects.intentflowJdbc)
    compileOnly(projects.intentflowSpringDataJdbc)
    compileOnly(projects.intentflowJpa)
    compileOnly(projects.intentflowJimmer)
    compileOnly(projects.intentflowOpenmapper)
    compileOnly(projects.intentflowOpenai)
    compileOnly(projects.intentflowAzureOpenai)
    compileOnly(projects.intentflowGigachat)
    compileOnly(projects.intentflowAnthropic)
    compileOnly(projects.intentflowVertex)
    compileOnly(projects.intentflowBedrock)
    compileOnly(projects.intentflowSpringDataCassandra)

    testImplementation(projects.intentflowJdbc)
    testImplementation(projects.intentflowSpringDataJdbc)
    testImplementation(projects.intentflowJpa)
    testImplementation(projects.intentflowJimmer)
    testImplementation(projects.intentflowOpenmapper)
    testImplementation(projects.intentflowOpenai)
    testImplementation(projects.intentflowAzureOpenai)
    testImplementation(projects.intentflowGigachat)
    testImplementation(projects.intentflowAnthropic)
    testImplementation(projects.intentflowVertex)
    testImplementation(projects.intentflowBedrock)
    testImplementation(projects.intentflowSpringDataCassandra)
    testImplementation(projects.intentflowPostgresql)
    testImplementation(projects.intentflowMysql)
    testImplementation(projects.intentflowH2)
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.starter.data.jdbc)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.data.cassandra)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.security.core)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.micrometer.observation)
    testImplementation(libs.resilience4j.circuitbreaker)
    testImplementation(
        if (providers.gradleProperty("springBoot").orElse(libs.versions.springBoot).get().startsWith("3."))
            libs.spring.boot.actuator else libs.spring.boot.health
    )
    testImplementation(libs.jimmer.sql)
    testImplementation(libs.openmapper)
    testRuntimeOnly(libs.jackson3.databind)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.mysql)
}

tasks.test {
    systemProperty("intentflow.llm.provider", "openai")
}

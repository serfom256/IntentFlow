plugins {
    `java-library`
}

dependencies {
    api(projects.intentflowCore)
    api(platform(libs.spring.boot.dependencies))
    api(libs.spring.boot.starter)
    annotationProcessor(platform(libs.spring.boot.dependencies))
    annotationProcessor(libs.spring.boot.configuration.processor)

    compileOnly(libs.spring.boot.jdbc)
    compileOnly(libs.spring.orm)
    compileOnly(projects.intentflowJdbc)
    compileOnly(projects.intentflowSpringDataJdbc)
    compileOnly(projects.intentflowJpa)
    compileOnly(projects.intentflowJimmer)
    compileOnly(projects.intentflowOpenmapper)
    compileOnly(projects.intentflowSpringDataCassandra)

    testImplementation(projects.intentflowJdbc)
    testImplementation(projects.intentflowSpringDataJdbc)
    testImplementation(projects.intentflowJpa)
    testImplementation(projects.intentflowJimmer)
    testImplementation(projects.intentflowOpenmapper)
    testImplementation(projects.intentflowSpringDataCassandra)
    testImplementation(projects.intentflowPostgresql)
    testImplementation(projects.intentflowMysql)
    testImplementation(libs.spring.boot.starter.jdbc)
    testImplementation(libs.spring.boot.starter.data.jdbc)
    testImplementation(libs.spring.boot.starter.data.jpa)
    testImplementation(libs.spring.boot.starter.data.cassandra)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.mysql)
}

rootProject.name = "intentflow"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(
    "intentflow-core", 
    "intentflow-spring-boot-starter"
)

val dialects = listOf(
    "intentflow-postgresql", 
    "intentflow-mysql", 
    "intentflow-cassandra"
)
val executors = listOf(
    "intentflow-jdbc", 
    "intentflow-spring-data-jdbc", 
    "intentflow-jpa", 
    "intentflow-jimmer",
    "intentflow-openmapper",
    "intentflow-spring-data-cassandra",
)

dialects.forEach {
    include(it)
    project(":$it").projectDir = file("intentflow-dialects/$it")
}
executors.forEach {
    include(it)
    project(":$it").projectDir = file("intentflow-executors/$it")
}

val examples = listOf(
    "intentflow-shop-api",
    "intentflow-shop-ui"
)

examples.forEach {
    include(it)
    project(":$it").projectDir = file("intentflow-examples/$it")
}

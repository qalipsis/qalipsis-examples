plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.allopen") version "2.3.21"
    id("com.google.devtools.ksp") version "2.3.7"
    id("io.micronaut.application") version "5.0.2"
    id("io.micronaut.aot") version "5.0.2"
}

group = "io.qalipsis"
version = "0.2.0"
description = "QALIPSIS demo microservice: HTTP to Kafka or RabbitMQ, and Kafka or RabbitMQ to TimescaleDB"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        javaParameters = true
    }
}

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "jakarta.inject.Singleton",
        "io.micronaut.validation.Validated",
        "io.micronaut.context.annotation.Bean"
    )
}

micronaut {
    version("5.2.7")
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.qalipsis.demo.*")
    }
    aot {
        // See https://micronaut-projects.github.io/micronaut-aot/latest/guide/ before changing them.
        optimizeServiceLoading = true
        convertYamlToJava = true
        precomputeOperations = true
        cacheEnvironment = true
        optimizeClassLoading = true
        deduceEnvironment = true
        optimizeNetty = true
    }
}

application {
    mainClass = "io.qalipsis.demo.QalipsisDemoMicroserviceKt"
}

graalvmNative {
    toolchainDetection = false
    binaries.named("main") {
        // The default amd64 target (x86-64-v3) needs AVX2: old CPUs and Rosetta could not run the image.
        buildArgs.add("-march=compatibility")
    }
}

val bouncyCastleVersion = "1.87"

dependencies {
    ksp("io.micronaut.security:micronaut-security-annotations")
    ksp("io.micronaut.data:micronaut-data-processor")
    ksp("io.micronaut.serde:micronaut-serde-processor")
    ksp("io.micronaut.validation:micronaut-validation-processor")
    ksp("io.micronaut:micronaut-http-validation")

    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    implementation("io.micronaut.validation:micronaut-validation")
    implementation("io.micronaut.security:micronaut-security-session")
    implementation("io.micronaut.redis:micronaut-redis-lettuce")
    implementation("io.micronaut.kafka:micronaut-kafka")
    implementation("io.micronaut.rabbitmq:micronaut-rabbitmq")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.7")
    // The self-signed certificate of the HTTPS server (micronaut.server.ssl.build-self-signed).
    implementation("org.bouncycastle:bcpkix-jdk18on:$bouncyCastleVersion")

    implementation("org.postgresql:postgresql:42.7.14")
    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.yaml:snakeyaml")

    testImplementation("io.micronaut:micronaut-http-client")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("com.willowtreeapps.assertk:assertk:0.28.1")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers")
}

tasks.test {
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED")
}

// Runs the service against the distributed example (../distributed-system/docker-compose.yml).
fun registerServer(name: String, text: String, env: Map<String, Any>, vararg arguments: String) =
    tasks.register<JavaExec>(name) {
        group = "application"
        description = text
        mainClass = application.mainClass
        maxHeapSize = "512m"
        jvmArgs("-Dmicronaut.env.deduction=false")
        environment = env
        args(*arguments)
        classpath = sourceSets["main"].runtimeClasspath
        workingDir = projectDir
    }

registerServer(
    "runHttpToKafkaServer", "Starts the microservice as an HTTP server that pushes the data to Kafka",
    mapOf(
        "KAFKA_ENABLED" to true,
        "MESSAGING_KAFKA_PUBLISHER_ENABLED" to true,
        "KAFKA_BOOTSTRAP_SERVERS" to "localhost:19092",
        "REDIS_URI" to "redis://localhost:6379",
    )
)

registerServer(
    "runKafkaToDbServer", "Starts the microservice as a Kafka listener that saves the data into TimescaleDB",
    mapOf(
        "KAFKA_ENABLED" to true,
        "MESSAGING_KAFKA_LISTENER_ENABLED" to true,
        "KAFKA_BOOTSTRAP_SERVERS" to "localhost:19092",
        "REDIS_URI" to "redis://localhost:6379",
        "DATASOURCES_DEFAULT_URL" to "jdbc:postgresql://localhost:25432/qalipsis",
        "DATASOURCES_DEFAULT_USERNAME" to "qalipsis_demo",
        "DATASOURCES_DEFAULT_PASSWORD" to "qalipsis",
    ),
    "--micronaut.server.port=-1", "--micronaut.ssl.port=-1"
)

// The Docker image: a GraalVM native executable, built inside the builder image (src/main/docker/Dockerfile).
// By default for the architecture of this machine, loaded into the local Docker. DOCKER_PLATFORMS selects others,
// DOCKER_BUILD_ARGS=--push publishes them. An amd64 image needs an x86-64-v3 CPU to build (no emulation on Apple
// silicon): the workflow .github/workflows/demo-microservice-image.yml builds both on their own runners.
val dockerImage = "aerisconsulting/qalipsis-demo-microservice"

tasks.register<Exec>("docker") {
    group = "docker"
    description = "Builds the native Docker image $dockerImage:$version."
    val hostArch = if (System.getProperty("os.arch") in listOf("aarch64", "arm64")) "arm64" else "amd64"
    val platforms = System.getenv("DOCKER_PLATFORMS") ?: "linux/$hostArch"
    val extra = System.getenv("DOCKER_BUILD_ARGS")?.split(" ")?.filter(String::isNotBlank)
        ?: if (platforms.contains(",")) emptyList() else listOf("--load")
    commandLine(
        listOf(
            "docker", "buildx", "build", "--platform", platforms, "--pull",
            "--tag", "$dockerImage:$version", "--tag", "$dockerImage:latest",
            "--file", "src/main/docker/Dockerfile"
        ) + extra + listOf(".")
    )
}

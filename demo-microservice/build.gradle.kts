import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen") version "1.8.21"

    id("io.micronaut.minimal.application") version "3.6.2"
    id("io.micronaut.graalvm") version "3.6.2"
    id("io.micronaut.aot") version "3.6.2"
    id("io.micronaut.test-resources") version "3.5.3"

    id("com.palantir.docker")
    `maven-publish`
}

version = "0.1.1"

allOpen {
    annotations(
        "io.micronaut.aop.Around",
        "jakarta.inject.Singleton",
        "io.micronaut.validation.Validated",
        "io.micronaut.context.annotation.Bean"
    )
}

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.majorVersion
        javaParameters = true
    }
}

val kotlinVersion: String by project
val postgresqlDriverVersion = "42.3.1"

dependencies {
    compileOnly("org.graalvm.nativeimage:svm")

    kapt("io.micronaut.security:micronaut-security-annotations")
    kapt("io.micronaut:micronaut-inject-java")
    kapt("io.micronaut.data:micronaut-data-processor")
    kapt("io.micronaut:micronaut-http-validation")

    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut.beanvalidation:micronaut-hibernate-validator")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.security:micronaut-security-session")
    implementation("javax.annotation:javax.annotation-api")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut.redis:micronaut-redis-lettuce")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut.kafka:micronaut-kafka")
    implementation("io.micronaut.rabbitmq:micronaut-rabbitmq")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3")
    implementation("io.micronaut.data:micronaut-data-jdbc")
    implementation("io.micronaut.sql:micronaut-jdbc-hikari")
    implementation("org.postgresql:postgresql:$postgresqlDriverVersion")
    implementation("io.micronaut.liquibase:micronaut-liquibase")
    implementation("io.github.microutils:kotlin-logging:2.1.23")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.bouncycastle:bcprov-jdk15on:1.64")
    implementation("org.bouncycastle:bcprov-ext-jdk15on:1.64")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.64")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("org.fusesource.jansi:jansi:2.4.1")

    testImplementation("io.micronaut.rxjava3:micronaut-rxjava3-http-client")
    testImplementation("io.mockk:mockk:1.11.0")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("com.willowtreeapps.assertk:assertk:0.+")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:rabbitmq")
    testImplementation("org.testcontainers:testcontainers")

    configurations.all {
        resolutionStrategy {
            force("ch.qos.logback:logback-classic:1.4.8")
            force("ch.qos.logback:logback-core:1.4.8")
        }
    }
}

application {
    mainClass.set("io.qalipsis.demo.QalipsisDemoMicroserviceKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

graalvmNative.toolchainDetection.set(false)
micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.qalipsis.*")
    }
    aot {
        // Please review carefully the optimizations enabled below
        // Check https://micronaut-projects.github.io/micronaut-aot/latest/guide/ for more details
        optimizeServiceLoading.set(true)
        convertYamlToJava.set(true)
        precomputeOperations.set(true)
        cacheEnvironment.set(true)
        optimizeClassLoading.set(true)
        deduceEnvironment.set(true)
        optimizeNetty.set(true)
    }
    testResources {
        sharedServer.set(true)
    }
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.majorVersion
        }
    }

    test {
        // JDK 16 enforces strong encapsulation of standard modules. In practice this mean overriding accessibility
        // modifiers in JDK classes (for example, privateMember.setAccessible(true)) is forbidden.
        // To allow mocking of the relevant classes, additional arguments are required.
        jvmArgs(
            "--add-opens", "java.base/java.time=ALL-UNNAMED",
            "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED"
        )
    }

    withType<Jar> {
        isZip64 = true
    }

    val collectJars = create("collectJars") {
        group = "build"

        doLast {
            val target = project.layout.buildDirectory.dir("classpath/libs").get().asFile
            if (!target.isDirectory) {
                target.deleteRecursively()
            }
            project.mkdir(target)
            configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.forEach {
                it.copyTo(File(target, it.name), true)
            }
        }
    }

    named("dockerPrepare").configure {
        dependsOn(collectJars)
    }

}

application {
    // Keep using mainClassName for ShadowJar.
    mainClassName = "io.qalipsis.demo.QalipsisDemoMicroserviceKt"
    applicationDefaultJvmArgs = listOf("-noverify", "-Dcom.sun.management.jmxremote", "-Dmicronaut.env.deduction=false")
    this.ext["workingDir"] = projectDir
}

task<JavaExec>("runHttpToKafkaServer") {
    group = "application"
    description = "Starts the microservice as a HTTP Server that pushes data to Kafka"
    mainClass.set("io.qalipsis.demo.QalipsisDemoMicroserviceKt")
    maxHeapSize = "512m"
    jvmArgs("-Dmicronaut.env.deduction=false")
    environment = mapOf(
        "KAFKA_ENABLED" to true,
        "MESSAGING_KAFKA_PUBLISHER_ENABLED" to true,
        "KAFKA_BOOTSTRAP_SERVERS" to "localhost:9093",
        "REDIS_URI" to "redis://localhost:6379",
        //"SERVER_HTTP_VERSION" to "1.1"
    )
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}

task<JavaExec>("runKafkaToDbServer") {
    group = "application"
    description = "Starts the microservice as a Kafka listener to save data into Timescale"
    mainClass.set("io.qalipsis.demo.QalipsisDemoMicroserviceKt")
    maxHeapSize = "512m"
    jvmArgs("-Dmicronaut.env.deduction=false")
    environment = mapOf(
        "KAFKA_ENABLED" to true,
        "MESSAGING_KAFKA_LISTENER_ENABLED" to true,
        "KAFKA_BOOTSTRAP_SERVERS" to "localhost:19092",
        "REDIS_URI" to "redis://localhost:6379",
        "DATASOURCES_DEFAULT_URL" to "jdbc:postgresql://localhost:25432/qalipsis",
        "DATASOURCES_DEFAULT_USERNAME" to "qalipsis_demo",
        "DATASOURCES_DEFAULT_PASSWORD" to "qalipsis",
        "LIQUIBASE_ENABLED" to "true",
    )
    args("--micronaut.server.port=-1", "--micronaut.ssl.port=-1")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}

val dockerImage = "aerisconsulting/qalipsis-demo-microservice"
docker {
    name = dockerImage

    setDockerfile(project.layout.projectDirectory.file("src/main/docker/Dockerfile").asFile)

    val jarFile = tasks.getByName<org.gradle.jvm.tasks.Jar>("jar").archiveFile.get().asFile
    files(
        project.layout.projectDirectory.dir("src/main/docker/resources"),
        project.layout.buildDirectory.dir("classpath"),
        project.layout.buildDirectory.file("libs/${jarFile.name}")
    )
    buildx(true)
    if (System.getenv("GITHUB_ACTIONS") != "true") {
        // On Github, the multiplatform build throws the error "docker exporter does not currently support exporting manifest lists".
        platform("linux/amd64", "linux/arm64")
    }

    buildArgs(mapOf("JAR_NAME" to jarFile.name, "START_CLASS" to application.mainClass.get()))
    noCache(false)
    pull(true)
}

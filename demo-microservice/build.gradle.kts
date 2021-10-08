import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.allopen")
    id("com.github.johnrengelman.shadow") version "6.0.0"
    id("com.palantir.docker")
}

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
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

val kotlinVersion: String by project
val micronautVersion = "3.0.+"

dependencies {
    implementation(kotlin("stdlib"))

    kapt(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    kapt("io.micronaut.security:micronaut-security-annotations")
    kapt("io.micronaut:micronaut-inject-java")

    implementation(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    implementation("io.qalipsis:api-dev:${project.version}")
    implementation("io.micronaut:micronaut-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut:micronaut-http-server-netty")
    implementation("io.micronaut.beanvalidation:micronaut-hibernate-validator")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut.security:micronaut-security-session")
    implementation("javax.annotation:javax.annotation-api")
    implementation("org.jetbrains.kotlin:kotlin-reflect:${kotlinVersion}")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${kotlinVersion}")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut.elasticsearch:micronaut-elasticsearch")
    implementation("io.micronaut.redis:micronaut-redis-lettuce")
    implementation("io.micronaut.kotlin:micronaut-kotlin-extension-functions")
    implementation("io.micronaut.kafka:micronaut-kafka")
    implementation("io.micronaut.rabbitmq:micronaut-rabbitmq")
    implementation("io.micronaut.rxjava3:micronaut-rxjava3")

    runtimeOnly("ch.qos.logback:logback-classic")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
    runtimeOnly("io.netty:netty-tcnative:2.0.29.Final")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.29.Final")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.40.Final:linux-x86_64")
    runtimeOnly("io.netty:netty-tcnative-boringssl-static:2.0.40.Final:osx-x86_64")

    kaptTest("io.micronaut:micronaut-inject-java")

    testImplementation(platform("io.micronaut:micronaut-bom:$micronautVersion"))
    testImplementation("io.micronaut.rxjava3:micronaut-rxjava3-http-client")
    testImplementation("io.mockk:mockk:1.+")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testImplementation("com.willowtreeapps.assertk:assertk:0.+")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.micronaut.test:micronaut-test-junit5")
    testImplementation("org.testcontainers:kafka:1.+")
    testImplementation("org.testcontainers:rabbitmq:1.+")
    testImplementation("org.testcontainers:elasticsearch:1.+")
    testImplementation("org.testcontainers:junit-jupiter:1.+") {
        exclude("junit", "junit")
    }
}

application {
    // Keep using mainClassName for ShadowJar.
    mainClassName = "io.qalipsis.demo.QalipsisDemoMicroserviceKt"
    applicationDefaultJvmArgs = listOf("-noverify", "-Dcom.sun.management.jmxremote", "-Dmicronaut.env.deduction=false")
    this.ext["workingDir"] = projectDir
}

task<JavaExec>("runHttpKafkaServer") {
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

task<JavaExec>("runKafkaListenerServer") {
    group = "application"
    description = "Starts the microservice as a Kafka listener to save data into Elasticsearch"
    mainClass.set("io.qalipsis.demo.QalipsisDemoMicroserviceKt")
    maxHeapSize = "256m"
    jvmArgs("-Dmicronaut.env.deduction=false")
    environment = mapOf(
        "KAFKA_ENABLED" to true,
        "MESSAGING_KAFKA_LISTENER_ENABLED" to true,
        "KAFKA_BOOTSTRAP_SERVERS" to "localhost:9093",
        "ELASTICSEARCH_HTTP_HOSTS" to "http://localhost:9200",
        "REDIS_URI" to "redis://localhost:6379"
    )
    args("--micronaut.server.port=-1", "--micronaut.ssl.port=-1")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
}

tasks {
    withType<KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_11.majorVersion
            javaParameters = true
        }
    }

    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        mergeServiceFiles()
        archiveClassifier.set("qalipsis")
    }
    build {
        dependsOn(shadowJar)
    }
}

val shadowJarName = "examples-${project.name}-${project.version}-qalipsis.jar"
docker {
    name = "aerisconsulting/qalipsis-demo-microservice"
    setDockerfile(project.file("src/docker/Dockerfile"))
    pull(true)
    noCache(true)
    files("build/libs/$shadowJarName", "src/docker/entrypoint.sh")
    buildArgs(mapOf("JAR_NAME" to shadowJarName))
}
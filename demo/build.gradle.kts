import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow")
    id("io.micronaut.application")
}

description = "Qalipsis Simple Demo"

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

val kotlinCoroutinesVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    kapt("io.micronaut.security:micronaut-security-annotations")
    kapt("io.micronaut:micronaut-graal")

    compileOnly("org.graalvm.nativeimage:svm")
    implementation("io.micronaut:micronaut-validation")
    implementation("io.micronaut.kotlin:micronaut-kotlin-runtime")
    implementation("io.micronaut:micronaut-runtime")
    implementation("javax.annotation:javax.annotation-api")
    implementation("io.micronaut:micronaut-http-client")
    implementation("io.micronaut.security:micronaut-security-oauth2")
    implementation("io.micronaut.redis:micronaut-redis-lettuce")
    implementation("io.micronaut.kafka:micronaut-kafka")
    implementation("io.micronaut.views:micronaut-views-thymeleaf")
    runtimeOnly("com.fasterxml.jackson.module:jackson-module-kotlin")
}

micronaut {
    runtime("netty")
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("io.qalipsis.*")
    }
}

application {
    mainClassName = "io.qalipsis.demo.ApplicationKt"
    applicationDefaultJvmArgs = listOf("-noverify", "-XX:TieredStopAtLevel=1", "-Dcom.sun.management.jmxremote",
        "-Dmicronaut.env.deduction=false")
    this.ext["workingDir"] = projectDir
}

tasks {
    named<ShadowJar>("shadowJar") {
        mergeServiceFiles()
        archiveClassifier.set("qalipsis")
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }
}

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "7.1.1"
}

val kotlinCoroutinesVersion: String by project
description = "Cassandra demo. Show how to do save & poll and save & search"

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

val assertkVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.kotest:kotest-assertions-core:5.4.2")
    implementation("io.qalipsis:api-dsl:${project.version}")
    implementation("io.qalipsis:api-processors:${project.version}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")
    implementation("ch.qos.logback:logback-classic:1.2.3"){ version { strictly("1.2.3") } }
    implementation("ch.qos.logback:logback-core:1.2.3"){ version { strictly("1.2.3") } }

    implementation("io.qalipsis:plugin-cassandra:${project.version}")
    implementation("io.qalipsis:plugin-jackson:${project.version}")

    kapt("io.qalipsis:api-processors:${project.version}")

    runtimeOnly("io.qalipsis:runtime:${project.version}")
    runtimeOnly("io.qalipsis:head:${project.version}")
    runtimeOnly("io.qalipsis:factory:${project.version}")
}

task<JavaExec>("runCampaignForSaveAndPoll") {
    group = "application"
    description = "Start a campaign with all the scenarios"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args("--autostart", "-c", "report.export.console.enabled=true", "-s", "cassandra-save-and-poll")
    workingDir = projectDir
    classpath = sourceSets["main"].runtimeClasspath
}

task<JavaExec>("runCampaignForSaveAndSearch") {
    group = "application"
    description = "Start a campaign with all the scenarios"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args("--autostart", "-c", "report.export.console.enabled=true", "-s", "cassandra-save-and-search")
    workingDir = projectDir
    classpath = sourceSets["main"].runtimeClasspath
}

tasks {
    named<ShadowJar>("shadowJar") {
        mergeServiceFiles()
        transform(ServiceFileTransformer().also { it.setPath("META-INF/qalipsis/**") })
        archiveClassifier.set("qalipsis")
    }

    build {
        dependsOn(shadowJar)
    }
}

val shadowJarName = "examples-${project.name}-${project.version}-qalipsis.jar"
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "7.1.1"
}

description = "Qalipsis Demo of a TCP reusable connection"

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

val assertkVersion: String by project

dependencies {
    implementation(enforcedPlatform("io.qalipsis:qalipsis-platform:0.5.a-SNAPSHOT"))
    kapt(enforcedPlatform("io.qalipsis:qalipsis-platform:0.5.a-SNAPSHOT"))
    kapt("io.qalipsis:api-processors")

    runtimeOnly("io.qalipsis:runtime")
    runtimeOnly("io.qalipsis:head")
    runtimeOnly("io.qalipsis:factory")

    implementation("io.qalipsis.plugin:netty")
    implementation("io.qalipsis.plugin:elasticsearch")

    implementation("io.github.microutils:kotlin-logging:2.1.23")
    implementation("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")
}


task<JavaExec>("runCampaign") {
    group = "application"
    description = "Start a campaign with all the scenarios"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args("--autostart", "-c", "report.export.console.enabled=true")
    workingDir = projectDir
    classpath = sourceSets["main"].runtimeClasspath
}

application {
    mainClass.set("io.qalipsis.runtime.Qalipsis")
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

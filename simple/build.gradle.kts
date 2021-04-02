import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "6.0.0"
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
val micronautVersion: String by project
val assertkVersion: String by project

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.qalipsis:api-dsl:${project.version}")
    /*
    implementation("io.qalipsis:plugin-XXX:${project.version}")
     */
    implementation("io.qalipsis:plugin-elasticsearch:${project.version}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")

    implementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
    implementation("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")

    runtimeOnly("io.qalipsis:runtime:${project.version}")
    kapt("io.qalipsis:api-processors:${project.version}")
}

application {
    mainClassName = "io.qalipsis.runtime.Qalipsis"
    applicationDefaultJvmArgs = listOf("-noverify", "-Dcom.sun.management.jmxremote", "-Dmicronaut.env.deduction=false")
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
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
    implementation("io.qalipsis:api-processors:${project.version}")
    implementation("io.qalipsis:plugin-elasticsearch:${project.version}")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${kotlinCoroutinesVersion}")

    implementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
    implementation("com.willowtreeapps.assertk:assertk-jvm:$assertkVersion")

    kapt("io.qalipsis:api-processors:${project.version}")

    runtimeOnly("io.qalipsis:runtime:${project.version}")
    runtimeOnly("io.qalipsis:head:${project.version}")
    runtimeOnly("io.qalipsis:factory:${project.version}")
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

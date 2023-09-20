import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "7.1.1"
}

description = "R2dbc demo. Show how to do save & poll and save & search"

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

kapt {
    includeCompileClasspath = true
}

dependencies {
    implementation(platform("io.qalipsis:qalipsis-platform:0.7.b-SNAPSHOT"))
    kapt(platform("io.qalipsis:qalipsis-platform:0.7.b-SNAPSHOT"))
    kapt("io.qalipsis:qalipsis-api-processors")

    runtimeOnly("io.qalipsis:qalipsis-runtime")
    runtimeOnly("io.qalipsis:qalipsis-head")
    runtimeOnly("io.qalipsis:qalipsis-factory")

    implementation("io.qalipsis.plugin:qalipsis-plugin-r2dbc-jasync")
    implementation("io.qalipsis.plugin:qalipsis-plugin-jackson")

    implementation("io.kotest:kotest-assertions-core:5.4.2")
}

task<JavaExec>("runCampaignForSaveAndPoll") {
    group = "application"
    description = "Start a campaign for the r2dbc-jasync-save-and-poll scenario"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args("--autostart", "-c", "report.export.console.enabled=true", "-s", "r2dbc-jasync-save-and-poll")
    workingDir = projectDir
    classpath = sourceSets["main"].runtimeClasspath
}

task<JavaExec>("runCampaignForSaveAndSearch") {
    group = "application"
    description = "Start a campaign for the r2dbc-jasync-save-and-search scenario"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args("--autostart", "-c", "report.export.console.enabled=true", "-s", "r2dbc-jasync-save-and-search")
    workingDir = projectDir
    classpath = sourceSets["main"].runtimeClasspath
}

application {
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    this.ext["workingDir"] = projectDir
}

val shadowJarName = "examples-${project.name}-${project.version}-qalipsis.jar"
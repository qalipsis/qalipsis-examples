import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "7.1.1"
}

description = "Qalipsis Demo - Testing a HTTP server"

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
    implementation("io.kotest:kotest-assertions-core:4.+")
    implementation("io.qalipsis:api-dsl:${project.version}")
    implementation("io.qalipsis:api-processors:${project.version}")
    implementation("io.qalipsis:plugin-netty:${project.version}")

    kapt("io.qalipsis:api-processors:${project.version}")

    runtimeOnly("io.qalipsis:runtime:${project.version}")
    runtimeOnly("io.qalipsis:head:${project.version}")
    runtimeOnly("io.qalipsis:factory:${project.version}")
}

task<JavaExec>("runCampaign") {
    group = "application"
    description = "Start a campaign with all the scenarios"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "2G"
    jvmArgs = listOf(
        "-Xms2G",
        "-XX:-MaxFDLimit",
        "-server",
        "-Dio.netty.leakDetectionLevel=advanced",
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=20",
        "-XX:InitiatingHeapOccupancyPercent=35",
        "-XX:+ExplicitGCInvokesConcurrent",
        "-XX:MaxInlineLevel=15",
        "-Djava.awt.headless=true",
        "-XX:+HeapDumpOnOutOfMemoryError",
        "-XX:HeapDumpPath=heap-dump.hprof",
        "-XX:ErrorFile=logs/hs_err_pid%p.log"
    )
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

val shadowJarName = "examples-${project.name}-${project.version}-qalipsis.jar"
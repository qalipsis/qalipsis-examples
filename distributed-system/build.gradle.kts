/*
 * Copyright 2022 AERIS IT Solutions GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "7.1.1"
    id("com.palantir.docker")
}

description = "Qalipsis Demo of a distributed system"

// Configure both compileKotlin and compileTestKotlin.
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.majorVersion
        javaParameters = true
    }
}

val assertkVersion: String by project

kapt {
    includeCompileClasspath = true
}

dependencies {
    implementation(platform("io.qalipsis:qalipsis-platform:0.7.b-SNAPSHOT"))
    kapt(platform("io.qalipsis:qalipsis-platform:0.7.b-SNAPSHOT"))
    kapt("io.qalipsis:qalipsis-api-processors")

    implementation("io.qalipsis.plugin:qalipsis-plugin-netty")
    implementation("io.qalipsis.plugin:qalipsis-plugin-kafka")
    implementation("io.qalipsis.plugin:qalipsis-plugin-elasticsearch")
    implementation("io.qalipsis.plugin:qalipsis-plugin-r2dbc-jasync")

    implementation("com.willowtreeapps.assertk:assertk:$assertkVersion")
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

application {
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    this.ext["workingDir"] = projectDir
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

docker {
    name = "aerisconsulting/qalipsis-demo-distributed-system"
    setDockerfile(project.file("src/docker/Dockerfile"))
    pull(true)
    noCache(true)
    files("build/libs/$shadowJarName", "src/docker/entrypoint.sh")
    buildArgs(mapOf("JAR_NAME" to shadowJarName))
}
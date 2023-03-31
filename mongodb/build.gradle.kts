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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "7.1.1"
}

description = "MongoDB demo. Show how to do save & poll and save & search"

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
    implementation(platform("io.qalipsis:qalipsis-platform:0.6.a-SNAPSHOT"))
    kapt(platform("io.qalipsis:qalipsis-platform:0.6.a-SNAPSHOT"))
    kapt("io.qalipsis:qalipsis-api-processors")

    runtimeOnly("io.qalipsis:qalipsis-runtime")
    runtimeOnly("io.qalipsis:qalipsis-head")
    runtimeOnly("io.qalipsis:qalipsis-factory")

    implementation("io.qalipsis.plugin:qalipsis-plugin-mongodb")
    implementation("io.qalipsis.plugin:qalipsis-plugin-jackson")

    implementation("io.kotest:kotest-assertions-core:5.4.2")
}

task<JavaExec>("runCampaignForSaveAndPoll") {
    group = "application"
    description = "Start a campaign for the mongodb-save-and-poll scenario"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args("--autostart", "-c", "report.export.console.enabled=true", "-s", "mongodb-save-and-poll")
    workingDir = projectDir
    classpath = sourceSets["main"].runtimeClasspath
}

task<JavaExec>("runCampaignForSaveAndSearch") {
    group = "application"
    description = "Start a campaign for the mongodb-save-and-search scenario"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args("--autostart", "-c", "report.export.console.enabled=true", "-s", "mongodb-save-and-search")
    workingDir = projectDir
    classpath = sourceSets["main"].runtimeClasspath
}

application {
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    this.ext["workingDir"] = projectDir
}

val shadowJarName = "examples-${project.name}-${project.version}-qalipsis.jar"
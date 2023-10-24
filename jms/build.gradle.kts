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
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "7.1.1"
}

description = "JMS demo. Show how to produce and consume"

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

// Enable the zip64 extension for shadowJar task.
tasks.withType<ShadowJar> {
    isZip64 = true
}

repositories {
    maven {
        name = "jitpack-dependencies"
        setUrl("https://jitpack.io")
    }
}

dependencies {
    implementation(platform("io.qalipsis:qalipsis-platform:0.7.c-SNAPSHOT"))
    kapt(platform("io.qalipsis:qalipsis-platform:0.7.c-SNAPSHOT"))
    kapt("io.qalipsis:qalipsis-api-processors")

    runtimeOnly("io.qalipsis:qalipsis-runtime")
    runtimeOnly("io.qalipsis:qalipsis-head")
    runtimeOnly("io.qalipsis:qalipsis-factory")

    implementation("io.qalipsis.plugin:qalipsis-plugin-jms")
    implementation("io.qalipsis.plugin:qalipsis-plugin-jackson")
    implementation("org.apache.activemq:activemq-all:5.15.14")
    implementation("io.kotest:kotest-assertions-core:5.4.2")
}

task<JavaExec>("runCampaignForProduceAndConsume") {
    group = "application"
    description = "Start a campaign for jms-produce-and-consume scenario"
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    maxHeapSize = "256m"
    args("--autostart", "-c", "report.export.console.enabled=true", "-s", "jms-produce-and-consume")
    workingDir = projectDir
    classpath = sourceSets["main"].runtimeClasspath
}

application {
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    this.ext["workingDir"] = projectDir
}

val shadowJarName = "examples-${project.name}-${project.version}-qalipsis.jar"
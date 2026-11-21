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
pluginManagement {
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    }

    repositories {
        mavenLocal()
        gradlePluginPortal()
    }
}

// Gives Gradle the repository to download JDK 21 from, when it is missing: for the daemon
// (gradle/gradle-daemon-jvm.properties) and for the Kotlin toolchain (build.gradle.kts).
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "qalipsis-examples"

include(
    "hello-world",
    "cassandra",
    "quickstart-http",
    "distributed-system",
    "cassandra",
    "elasticsearch",
    "r2dbc-jasync",
    "mongodb",
    "influxdb",
    "kafka",
    "redis",
    "rabbitmq",
    "jms",
    "netty-mqtt",
    "netty-tcp",
    "jakarta-ee-messaging",
    "graphite",
    "sql",
)
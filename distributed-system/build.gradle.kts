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
import io.qalipsis.gradle.bootstrap.tasks.RunQalipsis

plugins {
    id("io.qalipsis.bootstrap")
    id("com.github.johnrengelman.shadow") version "7.1.1"
    id("com.palantir.docker-compose")
}

description = "Demo how to test a distributed system"

qalipsis {
    plugins {
        apacheKafka()
        http()
        sql()
        timescaleDb()
        elasticsearch()
        influxDb()
    }
}

dependencies {
    implementation("io.kotest:kotest-assertions-core-jvm:5.+") {
        exclude(group = "org.jetbrains.kotlin")
    }
}

tasks {
    create("runDistributedSystemDemo", RunQalipsis::class.java) {
        scenarios("distributed-system")
        jvmArgs(
            "-Xms2G",
            "-XX:-MaxFDLimit",
            "-server",
            "-XX:+UseG1GC",
            "-XX:MaxGCPauseMillis=20",
            "-XX:InitiatingHeapOccupancyPercent=35",
            "-XX:+ExplicitGCInvokesConcurrent",
            "-XX:MaxInlineLevel=15"
        )
        maxHeapSize = "2G"

        dependsOn("dockerComposeUp")
        finalizedBy("dockerComposeDown")
    }

    named("check") {
        dependsOn("runDistributedSystemDemo")
    }

    withType<ShadowJar> {
        isZip64 = true
    }

    register<JavaExec>("runQalipsisWithGui") {
        group = "qalipsis"
        description = "Starts QALIPSIS standalone with the GUI, for PostgreSQL"
        mainClass.set("io.qalipsis.runtime.Qalipsis")
        maxHeapSize = "2G"
        args(
            "--persistent",
            "--gui",
            "-e", "api-documentation"
        )
        classpath = sourceSets["main"].runtimeClasspath
        workingDir = projectDir

        dependsOn("dockerComposeUp")
    }
}


application {
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    this.ext["workingDir"] = projectDir
}

val shadowJarName = "examples-${project.name}-${project.version}-qalipsis.jar"
/** End of the configuration for the shadow plugin. **/
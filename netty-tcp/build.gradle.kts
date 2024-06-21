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

description = "TCP demo. Show how to generate TCP requests"

qalipsis {
    plugins {
        netty()
        jackson()
    }
}

dependencies {
    implementation("com.willowtreeapps.assertk:assertk-jvm:0.23")
    implementation("io.kotest:kotest-assertions-core:5.4.2")
}

tasks {
    create("runCampaignForTcpEcho", RunQalipsis::class.java) {
        scenarios("tcp-echo")
    }

    withType<RunQalipsis> {
        configuration(
            "report.export.console.enabled" to "true",
            "report.export.junit.enabled" to "true",
            "report.export.junit.folder" to project.layout.buildDirectory.dir("test-results").get().asFile.path
        )
    }

    named("check") {
        dependsOn("runCampaignForTcpEcho")
    }
}

/** Beginning of the configuration for the shadow plugin. **/
tasks {
    withType<ShadowJar> {
        isZip64 = true
    }
}

application {
    mainClass.set("io.qalipsis.runtime.Qalipsis")
    this.ext["workingDir"] = projectDir
}

val shadowJarName = "examples-${project.name}-${project.version}-qalipsis.jar"
/** End of the configuration for the shadow plugin. **/

/** Start of the configuration to set up the testing environment **/
tasks {
    withType<RunQalipsis> {
        dependsOn("dockerComposeUp")
        finalizedBy("dockerComposeDown")
    }
}
/** End of the configuration to set up the testing environment **/
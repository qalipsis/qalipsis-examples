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

import io.qalipsis.gradle.bootstrap.tasks.RunQalipsis

plugins {
    id("io.qalipsis.bootstrap")
}

description = "Hello World demo"

tasks {
    create("runCampaignForHelloWorld", RunQalipsis::class.java) {
        scenarios("hello-world")
    }

    withType<RunQalipsis> {
        configuration(
            "report.export.console.enabled" to "true",
            "report.export.junit.enabled" to "true",
            "report.export.junit.folder" to project.layout.buildDirectory.dir("test-results").get().asFile.path
        )
    }

    named("check") {
        dependsOn("runCampaignForHelloWorld")
    }
}
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

import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    id("io.qalipsis.bootstrap") version "0.1.1"

    id("nebula.maven-publish") version "18.4.0"
    id("com.palantir.docker") version "0.36.0" apply false
    id("com.palantir.docker-compose") version "0.36.0" apply false
    id("com.github.jk1.dependency-license-report") version "1.17"
}

licenseReport {
    renderers = arrayOf<com.github.jk1.license.render.ReportRenderer>(
        com.github.jk1.license.render.InventoryHtmlReportRenderer(
            "report.html",
            "QALIPSIS Examples"
        )
    )
    allowedLicensesFile = File("$projectDir/build-config/allowed-licenses.json")
    filters =
        arrayOf<com.github.jk1.license.filter.DependencyFilter>(com.github.jk1.license.filter.LicenseBundleNormalizer())
}

description = "QALIPSIS Examples"

java {

    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<Wrapper> {
    distributionType = Wrapper.DistributionType.BIN
    gradleVersion = "7.2"
}

allprojects {
    group = "io.qalipsis"
    version = File(rootDir, "project.version").readText().trim()

    apply(plugin = "java")
    apply(plugin = "nebula.maven-publish")

    repositories {
        mavenLocal()
        mavenCentral()
        maven {
            name = "maven-central-snapshots"
            setUrl("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    val ossrhUsername: String? by project
    val ossrhPassword: String? by project

    publishing {
        publications {
            filterIsInstance<MavenPublication>().forEach {
                it.artifactId = "examples-${project.name}"
            }
        }
        repositories {
            maven {
                val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
                val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots/"
                name = "sonatype"
                url = uri(if (project.version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)
                credentials {
                    username = ossrhUsername
                    password = ossrhPassword
                }
            }
        }
    }

    tasks {
        withType<Jar> {
            archiveBaseName.set("examples-${project.name}")
        }

        if (!project.file("src/main/kotlin").isDirectory) {
            project.logger.lifecycle("Disabling publish for ${project.name}")
            withType<AbstractPublishToMaven> {
                enabled = false
            }
        }

        withType<Test> {
            useJUnitPlatform()
            testLogging {
                events(FAILED, STANDARD_ERROR)
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT

                debug {
                    events(*org.gradle.api.tasks.testing.logging.TestLogEvent.values())
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }

                info {
                    events(FAILED, SKIPPED, PASSED, STANDARD_ERROR)
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }

        artifacts {
            if (project.plugins.hasPlugin("java-test-fixtures")) {
                archives(findByName("testFixturesSources") as Jar)
                archives(findByName("testFixturesJavadoc") as Jar)
                archives(findByName("testFixturesJar") as Jar)
            }
        }
    }
}

val testTasks = subprojects.flatMap {
    val testTasks = mutableListOf<Test>()
    (it.tasks.findByName("test") as Test?)?.apply {
        testTasks.add(this)
    }
    testTasks
}

tasks.register("testReport", TestReport::class) {
    this.group = "verification"
    destinationDirectory.set(file("${buildDir}/reports/tests"))
    testResults.from(*(testTasks.toTypedArray()))
}

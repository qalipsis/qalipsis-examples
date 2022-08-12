import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    java
    idea
    kotlin("jvm") version "1.5.31"
    kotlin("kapt") version "1.5.31"
    kotlin("plugin.allopen") version "1.5.31"

    id("nebula.contacts") version "6.0.0"
    id("nebula.info") version "11.3.3"
    id("nebula.maven-publish") version "18.4.0"
    id("nebula.maven-scm") version "18.4.0"
    id("nebula.maven-manifest") version "18.4.0"
    signing
    id("com.palantir.docker") version "0.28.0" apply false
}

configure<JavaPluginConvention> {
    description = "Qalipsis"

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
    apply(plugin = "nebula.contacts")
    apply(plugin = "nebula.info")
    apply(plugin = "nebula.maven-publish")
    apply(plugin = "nebula.maven-scm")
    apply(plugin = "nebula.maven-manifest")
    apply(plugin = "nebula.maven-developer")
    apply(plugin = "nebula.javadoc-jar")
    apply(plugin = "nebula.source-jar")
    apply(plugin = "signing")

    infoBroker {
        excludedManifestProperties =listOf("Module-Owner", "Module-Email", "Module-Source")
    }

    contacts {
        addPerson("eric.jesse@aeris-consulting.com", delegateClosureOf<nebula.plugin.contacts.Contact> {
            moniker = "Eric Jessé"
            github = "ericjesse"
            role("Owner")
        })
    }

    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
        maven {
            name = "maven-central-snapshots"
            setUrl("https://oss.sonatype.org/content/repositories/snapshots")
        }
    }

    configure<JavaPluginConvention> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    signing {
        publishing.publications.forEach { sign(it) }
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

        named<Test>("test") {
            ignoreFailures = System.getProperty("ignoreUnitTestFailures", "false").toBoolean()
            this.exclude("**/*IntegrationTest.*", "**/*IntegrationTest$*")
        }

        val integrationTest = register<Test>("integrationTest") {
            this.group = "verification"
            ignoreFailures = System.getProperty("ignoreIntegrationTestFailures", "false").toBoolean()
            include("**/*IntegrationTest.*", "**/*IntegrationTest$*")
        }

        named<Task>("check") {
            dependsOn(integrationTest.get())
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
    (it.tasks.findByName("integrationTest") as Test?)?.apply {
        testTasks.add(this)
    }
    testTasks
}

tasks.register("testReport", TestReport::class) {
    this.group = "verification"
    destinationDir = file("${buildDir}/reports/tests")
    reportOn(*(testTasks.toTypedArray()))
}

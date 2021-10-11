import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm")
    kotlin("kapt")
    id("com.github.johnrengelman.shadow") version "6.0.0"
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

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.willowtreeapps.assertk:assertk:$assertkVersion")

    implementation("io.qalipsis:api-dsl:${project.version}")

    implementation("io.qalipsis:plugin-netty:${project.version}")
    implementation("io.qalipsis:plugin-elasticsearch:${project.version}")
    implementation("io.qalipsis:plugin-kafka:${project.version}")
    implementation("io.qalipsis:plugin-rabbitmq:${project.version}")
    implementation("io.qalipsis:plugin-redis-lettuce:${project.version}")

    kapt("io.qalipsis:api-processors:${project.version}")

    runtimeOnly("io.qalipsis:runtime:${project.version}")
}

application {
    mainClassName = "io.qalipsis.runtime.Qalipsis"
    applicationDefaultJvmArgs = listOf(
        "-Xmx2G",
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
    this.ext["workingDir"] = projectDir
}

tasks {
    named<ShadowJar>("shadowJar") {
        mergeServiceFiles()
        archiveClassifier.set("qalipsis")
    }
}

tasks {
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
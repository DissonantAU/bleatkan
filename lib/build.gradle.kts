import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)

    `java-library`
    `maven-publish`

    alias(libs.plugins.dokka)
}

/* Version */
val versionMajor: Int = 0
val versionMinor: Int = 6
val versionPatch: Int = 6 //Is padded with 0 to left if needed


group = "io.github.dissonantau"

val buildsDir = rootProject.layout.projectDirectory.dir("libBuilds")

repositories {
    mavenCentral()
}

dependencies {
    /* Main Dependencies */
    //Kotlin BOM
    runtimeOnly(libs.kotlin.bom)
    implementation(platform(libs.kotlin.gradle.plugins.bom))

    // Coroutines - concurrent library
    implementation(libs.kotlinx.coroutines.bom)
    implementation(libs.kotlinx.coroutines.core)
    runtimeOnly(libs.kotlinx.coroutines.slf4j)

    // Websocket (and HTTP) Framework
    implementation(platform(libs.ktor.client.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.logging)
    // HTTP Engines - pick one
    implementation(libs.ktor.client.cio) // No HTTP/2 Support, fine for Veadotube Websockets
    // JSON - probably best to use Probably KotlinX for JSON
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.serialization)
    implementation(platform(libs.kotlinx.serialization.bom))
    implementation(libs.kotlinx.serialization.json)


    // Log4J
    implementation(platform(libs.log4j.bom))
    implementation(libs.log4j.core)
    implementation(libs.log4j.api)
    implementation(libs.log4j.slf4j2.impl)
    // Generic Logging Interface that can use Log4J
    implementation(platform(libs.slf4j.bom))
    implementation(libs.slf4j.api)
    implementation(libs.logging.kotlin) //Kotlin Wrapper for slf4j

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.debug)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlin.test.junit5)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    testImplementation(libs.ktor.client.mock)

    testImplementation(libs.mockk)

}

kotlin {
    //jvmToolchain(8)

    compilerOptions {
        javaParameters = true // Needed if Reflection is used to get named parameters
        jvmTarget.set(JvmTarget.JVM_1_8)
        apiVersion.set(KotlinVersion.KOTLIN_2_0)
        languageVersion.set(KotlinVersion.KOTLIN_2_0)
    }
}

java {
    //withJavadocJar()
    //withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {

    register("calculateVersion") {

        doFirst {
            println("Is Release: ${(project.extra.has("IS_RELEASE") && project.extra["IS_RELEASE"] == true)}")

            val isRelease =
                System.getenv("IS_RELEASE") == "YES" ||
                        (project.extra.has("IS_RELEASE") && project.extra["IS_RELEASE"] == true)
            val versionSuffix: String = isRelease.ifFalse { "-DEV" }.orEmpty()

            // Version becomes 1203
            val versionCode: Int = versionMajor * 1000 + versionMinor * 100 + versionPatch
            project.extra["versionCode"] = versionCode
            println("Version Code: $versionCode")

            // Version becomes 1.2.03 (Or 1.2.03-DEV etc.)
            val versionName =
                "$versionMajor.$versionMinor.${versionPatch.toString().padStart(2, '0')}$versionSuffix"
            project.extra["versionName"] = versionName
            project.version = versionName
            println("Version Name: $versionName")
        }

    }


    withType<Jar> {
        dependsOn(
            named("calculateVersion")
        )

        archiveBaseName.set(rootProject.name)
        manifest {
            attributes(
                mapOf(
                    "Implementation-Title" to rootProject.name,
                    "Implementation-Version" to project.version
                )
            )
        }
    }


    register<Jar>("dokkaHtmlJar") {
        group = "build"
        dependsOn(dokkaHtml)
        from(dokkaHtml.flatMap { it.outputDirectory })
        archiveClassifier.set("html-docs")
    }

    register<Jar>("dokkaJavadocJar") {
        group = "build"
        dependsOn(dokkaJavadoc)
        from(dokkaJavadoc.flatMap { it.outputDirectory })
        archiveClassifier.set("javadoc")
    }

    // From https://github.com/Kotlin/dokka/blob/1.9.20/examples/gradle/dokka-gradle-example/build.gradle.kts
    withType<DokkaTask>().configureEach {
        dokkaSourceSets {
            named("main") {
                // used as project name in the header
                moduleName.set("BleatKan")

                // adds source links that lead to this repository, allowing readers
                // to easily find source code for inspected declarations
                sourceLink {
                    localDirectory.set(
                        project.layout.projectDirectory.dir("src/main/kotlin").asFile
                    )
                    remoteUrl.set(
                        URL(
                            "https://github.com/DissonantAU/bleatkan/tree/main/" +
                                    "lib/src/main/kotlin"
                        )
                    )

                    println("Local Source Link: ${localDirectory.get()}")
                    println("Remote Source Link: ${remoteUrl.get()}")
                }
            }
        }
    }


    register("setBuildDev") {
        doFirst {
            println("Set to Non-Release Build")
            project.ext["IS_RELEASE"] = false
        }
    }

    register("setBuildRelease") {
        doFirst {
            println("Set to Release Build")
            project.ext["IS_RELEASE"] = true
        }
    }

    /* Task to build the project, copy to libBuilds*/

    register<Copy>("copyToLibBuilds") {
        duplicatesStrategy = DuplicatesStrategy.WARN

        doFirst {
            println("Copy to '${buildsDir.dir("${rootProject.name}-${project.version}")}'")
        }

        mustRunAfter(
            named("calculateVersion")
        )
        dependsOn(
            build,
            jar,
            kotlinSourcesJar,
            named<Jar>("dokkaJavadocJar"),
            named<Jar>("dokkaHtmlJar"),
        )
        from(
            jar,
            kotlinSourcesJar,
            named<Jar>("dokkaJavadocJar"),
            named<Jar>("dokkaHtmlJar"),
        )
        into { buildsDir.dir("${rootProject.name}-${project.version}") }

    }


    register<Copy>("copyToLibBuildsHtml") {
        duplicatesStrategy = DuplicatesStrategy.WARN

        doFirst {
            println("Copy to '${buildsDir.dir("${rootProject.name}-${project.version}").dir("docs").dir("html")}'")
        }

        mustRunAfter(named("calculateVersion"))
        dependsOn(dokkaHtml)
        from(dokkaHtml)
        into { buildsDir.dir("${rootProject.name}-${project.version}").dir("docs").dir("html") }

    }

    register<Copy>("copyToLibBuildsJavadoc") {
        duplicatesStrategy = DuplicatesStrategy.WARN

        doFirst {
            println("Copy to '${buildsDir.dir("${rootProject.name}-${project.version}").dir("docs").dir("javadoc")}'")
        }

        mustRunAfter(named("calculateVersion"))
        dependsOn(dokkaJavadoc)
        from(dokkaJavadoc)
        into { buildsDir.dir("${rootProject.name}-${project.version}").dir("docs").dir("javadoc") }
    }

    register("buildCopyDevToLibBuilds") {
        group = "build"

        doFirst {
            println("Set to Dev Build")
            project.ext["IS_RELEASE"] = false
        }

        finalizedBy(
            named("calculateVersion"),
            named("copyToLibBuilds"),
            named("copyToLibBuildsHtml"),
            named("copyToLibBuildsJavadoc"),
        )

    }

    register("buildCopyReleaseToLibBuilds") {
        group = "build"

        doFirst {
            println("Set to Release Build")
            project.ext["IS_RELEASE"] = true
        }

        finalizedBy(
            named("calculateVersion"),
            named("copyToLibBuilds"),
            named("copyToLibBuildsHtml"),
            named("copyToLibBuildsJavadoc"),
        )

    }

    test {
        useJUnitPlatform()
    }
}

publishing {
    publications {
        create<MavenPublication>("bleatkan") {
            from(components["java"])
            artifactId = rootProject.name
        }
    }

    repositories {
        maven {
            name = "tempRepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

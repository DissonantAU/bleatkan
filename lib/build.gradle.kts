import org.gradle.jvm.tasks.Jar
import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
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
val versionPatch: Int = 85 //Is padded with 0 to left if needed


group = "io.github.dissonantau"

val buildsDir = rootProject.layout.projectDirectory.dir("libBuilds")
println("Bleatkan LibBuilds Dir: $buildsDir")



project.extra["releaseName"] = rootProject.name
println("Release project: ${project.extra["releaseName"]}")

// Version becomes 1203
val versionCode: Int = versionMajor * 1000 + versionMinor * 100 + versionPatch
project.extra["versionCode"] = versionCode
println("Version Code: $versionCode")

// Version Base Name becomes 1.2.03
val versionName =
    "$versionMajor.$versionMinor.${versionPatch.toString().padStart(2, '0')}"
project.extra["versionBaseName"] = versionName

calcVersion()


repositories {
    mavenCentral()
}

dependencies {
    /* Main Dependencies */
    // Kotlin BOM
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
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {

    // Calculate Version/Build names, etc.
    register("calculateLibraryVersion") {
        doLast {
            calcVersion()
        }
    }

    // Run Version Calculation during Build, including IDE Import/refresh
    // Mainly targeting prepareKotlinBuildScriptModel
    //try {
    //    rootProject.tasks.named("prepareKotlinBuildScriptModel") {
    //        println("Add calculateLibraryVersion to dependsOn $name > Class ${javaClass.name}")
    //        dependsOn(
    //            named("calculateLibraryVersion")
    //        )
    //    }
    //} catch (_: Throwable) {
    //    // Add if prepareKotlinBuildScriptModel not found
    //    println("prepareKotlinBuildScriptModel Not Found - Falling Back to Add to all")
    //
    //    rootProject.tasks.forEach {
    //        if (it.name != "calculateLibraryVersion") {
    //            println("Add calculateLibraryVersion to dependsOn $name > Class ${javaClass.name}")
    //            it.dependsOn(
    //                named("calculateLibraryVersion")
    //            )
    //        }
    //    }
    //}

    /* Jar Tasks - Modify to generate version names and set archive properties */

    withType<Jar> {
        dependsOn(
            named("calculateLibraryVersion")
        )

        doFirst {
            println(
                "Project ${
                    rootProject.name
                } - archiveBaseName = ${
                    project.extra["releaseName"]
                }; archiveVersion = ${project.extra["versionName"]}"
            )
        }

        archiveBaseName.set("${project.extra["releaseName"]}")
        archiveVersion.set(provider { "${project.extra["versionName"]}" })
    }

    /* Doc Generation */

    register<Jar>("dokkaHtmlJar") {
        group = "build"
        archiveClassifier.set("html-docs")
        dependsOn(dokkaHtml)
        from(dokkaHtml.flatMap { it.outputDirectory })
    }

    register<Jar>("dokkaJavadocJar") {
        group = "build"
        archiveClassifier.set("javadoc")
        dependsOn(dokkaJavadoc)
        from(dokkaJavadoc.flatMap { it.outputDirectory })
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

                    //println("Local Source Link: ${localDirectory.get()}")
                    //println("Remote Source Link: ${remoteUrl.get()}")
                }
            }
        }
    }


    /* Tasks to build the project/docs & copy to libBuilds*/

    register<Copy>("copyToLibBuilds") {
        duplicatesStrategy = DuplicatesStrategy.WARN

        doFirst {
            println(
                "Copy ${rootProject.name} to '${
                    buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}")
                }'"
            )
        }

        mustRunAfter(
            named("calculateLibraryVersion")
        )
        dependsOn(
            //build,
            jar,
            kotlinSourcesJar,
        )
        from(
            jar,
            kotlinSourcesJar,
        )
        into { buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}") }

    }


    register<Copy>("copyToLibBuildsHtml") {
        duplicatesStrategy = DuplicatesStrategy.WARN

        doFirst {
            println(
                "Copy to '${
                    buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}")
                        .dir("docs")
                        .dir("html")
                }'"
            )
        }

        mustRunAfter(named("calculateLibraryVersion"))
        dependsOn(dokkaHtml)
        from(dokkaHtml)
        into {
            buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}")
                .dir("docs")
                .dir("html")
        }
    }

    register<Copy>("copyToLibBuildsJavadoc") {
        duplicatesStrategy = DuplicatesStrategy.WARN

        doFirst {
            println(
                "Copy to '${
                    buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}")
                        .dir("docs")
                        .dir("javadoc")
                }'"
            )
        }

        mustRunAfter(named("calculateLibraryVersion"))
        dependsOn(dokkaJavadoc)
        from(dokkaJavadoc)
        into {
            buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}").dir("docs").dir("javadoc")
        }
    }

    /* Meta Build Jobs */

    register("buildCopySnapshotToLibBuilds") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Snapshot Build")
            project.ext["BUILD_TYPE"] = "SNAPSHOT"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            named("copyToLibBuilds"),
            //named("copyToLibBuildsHtml"),
            //named("copyToLibBuildsJavadoc"),
        )
    }

    register("buildCopyDevToLibBuilds") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Dev Build")
            project.ext["BUILD_TYPE"] = "DEV"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            named("copyToLibBuilds"),
            named("copyToLibBuildsHtml"),
            named("copyToLibBuildsJavadoc"),
        )
    }

    register("buildCopyReleaseToLibBuilds") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Release Build")
            project.ext["BUILD_TYPE"] = "RELEASE"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
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


fun calcVersion() {
    println("Release Name: ${rootProject.name}")
    // Get Environment Var if it exists
    val envBuildType: String = try {
        System.getenv("BUILD_TYPE")
    } catch (_: Throwable) {
        ""
    }

    // Release Type - Variable from Build Tasks > Environment Var > Snapshot Default
    val buildType: String =
        when {
            project.extra.has("BUILD_TYPE") -> "${project.extra["BUILD_TYPE"]}"
            envBuildType.isNotBlank() -> System.getenv("BUILD_TYPE")
            else -> "SNAPSHOT"
        }

    println("Release Type: $buildType")

    val buildSuffix: String = when (buildType) {
        "RELEASE" -> ""
        "DEV" -> "-DEV"
        else -> "-SNAPSHOT"
    }

    // Version Name becomes 1.2.03 or 1.2.03-DEV etc.
    val versionNameSuffix = "${project.extra["versionBaseName"]}$buildSuffix"

    project.extra["versionName"] = versionNameSuffix
    project.version = versionNameSuffix
    println("Version Full Name: $versionNameSuffix")

    java.manifest {
        attributes(
            mapOf(
                "Implementation-Title" to rootProject.name,
                "Implementation-Version" to project.version
            )
        )
    }

}
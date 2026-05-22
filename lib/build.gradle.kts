import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.serialization)

    `java-library`
    `maven-publish`

    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
}

/* Version */
val versionMajor: Int = 0
val versionMinor: Int = 8
val versionPatch: Int = 3


group = "io.github.dissonantau"

val buildsDir = rootProject.layout.projectDirectory.dir("libBuilds")
println("Bleatkan LibBuilds Dir: $buildsDir")



project.extra["releaseName"] = rootProject.name
println("Release project: ${project.extra["releaseName"]}")


// Version Base Name becomes 1.2.03
val versionName =
    "$versionMajor.$versionMinor.$versionPatch"
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

    // HTTP/Websocket Framework
    implementation(platform(libs.ktor.client.bom))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.websockets)
    implementation(libs.ktor.client.logging)
    // HTTP Engine
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

    register<Jar>("dokkaGenerateHtmlJar") {
        group = "documentation"
        archiveClassifier.set("html-docs")
        dependsOn(dokkaGeneratePublicationHtml)
        from(dokkaGeneratePublicationHtml.flatMap { it.outputDirectory })
    }

    register<Jar>("dokkaGenerateJavadocJar") {
        group = "documentation"
        archiveClassifier.set("javadoc")
        dependsOn(dokkaGeneratePublicationJavadoc)
        from(dokkaGeneratePublicationJavadoc.flatMap { it.outputDirectory })
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
        dependsOn(dokkaGeneratePublicationHtml)
        from(dokkaGeneratePublicationHtml)
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
        dependsOn(dokkaGeneratePublicationJavadoc)
        from(dokkaGeneratePublicationJavadoc)
        into {
            buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}").dir("docs").dir("javadoc")
        }
    }

    register<Copy>("copyToLibBuildsHtmlJar") {
        duplicatesStrategy = DuplicatesStrategy.WARN

        doFirst {
            println(
                "Copy to '${
                    buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}")
                }'"
            )
        }

        dependsOn(named("dokkaGenerateHtmlJar"))
        from(named("dokkaGenerateHtmlJar"))
        into {
            buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}")
        }
    }

    register<Copy>("copyToLibBuildsJavadocJar") {
        duplicatesStrategy = DuplicatesStrategy.WARN

        doFirst {
            println(
                "Copy to '${
                    buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}")
                }'"
            )
        }

        dependsOn(named("dokkaGenerateJavadocJar"))
        from(named("dokkaGenerateJavadocJar"))
        into {
            buildsDir.dir("${project.extra["releaseName"]}-${project.extra["versionName"]}")
        }
    }


    /* Meta Build Jobs */

    register("buildCopySnapshotToLibBuildsJarDocs") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Snapshot Build")
            project.ext["BUILD_TYPE"] = "SNAPSHOT"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            named("copyToLibBuilds"),
            named("copyToLibBuildsHtmlJar"),
            named("copyToLibBuildsJavadocJar"),
        )
    }

    register("buildCopySnapshotToLibBuildsOnlyJar") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Snapshot Build")
            project.ext["BUILD_TYPE"] = "SNAPSHOT"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            named("copyToLibBuilds"),
            //named("copyToLibBuildsHtmlJar"),
            //named("copyToLibBuildsJavadocJar"),
        )
    }

    register("buildCopySnapshotToLibBuildsOnlyDocs") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Snapshot Build")
            project.ext["BUILD_TYPE"] = "SNAPSHOT"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            //named("copyToLibBuilds"),
            named("copyToLibBuildsHtmlJar"),
            named("copyToLibBuildsJavadocJar"),
        )
    }

    register("buildCopyDevToLibBuildsJarDocs") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Dev Build")
            project.ext["BUILD_TYPE"] = "DEV"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            named("copyToLibBuilds"),
            named("copyToLibBuildsHtmlJar"),
            named("copyToLibBuildsJavadocJar"),
        )
    }

    register("buildCopyDevToLibBuildsOnlyJar") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Dev Build")
            project.ext["BUILD_TYPE"] = "DEV"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            named("copyToLibBuilds"),
            //named("copyToLibBuildsHtmlJar"),
            //named("copyToLibBuildsJavadocJar"),
        )
    }

    register("buildCopyDevToLibBuildsOnlyDocs") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Dev Build")
            project.ext["BUILD_TYPE"] = "DEV"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            //named("copyToLibBuilds"),
            named("copyToLibBuildsHtmlJar"),
            named("copyToLibBuildsJavadocJar"),
        )
    }

    register("buildCopyReleaseToLibBuildsJarDocs") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Release Build")
            project.ext["BUILD_TYPE"] = "RELEASE"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            named("copyToLibBuilds"),
            named("copyToLibBuildsHtmlJar"),
            named("copyToLibBuildsJavadocJar"),
        )
    }

    register("buildCopyReleaseToLibBuildsOnlyJar") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Release Build")
            project.ext["BUILD_TYPE"] = "RELEASE"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            named("copyToLibBuilds"),
            //named("copyToLibBuildsHtmlJar"),
            //named("copyToLibBuildsJavadocJar"),
        )
    }

    register("buildCopyReleaseToLibBuildsOnlyDocs") {
        group = "build"

        doFirst {
            println("Building ${rootProject.name} as Release Build")
            project.ext["BUILD_TYPE"] = "RELEASE"
        }

        finalizedBy(
            named("calculateLibraryVersion"),
            //named("copyToLibBuilds"),
            named("copyToLibBuildsHtmlJar"),
            named("copyToLibBuildsJavadocJar"),
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


// Dokka Config Gen - https://kotlinlang.org/docs/dokka-migration.html https://github.com/Kotlin/dokka/blob/1.9.20/examples/gradle/dokka-gradle-example/build.gradle.kts
dokka {
    // used as project name in the header
    moduleName.set("BleatKan")

    dokkaSourceSets.main {
        sourceLink {

            localDirectory.set(
                project.layout.projectDirectory.dir("src/main/kotlin").asFile
            )
            remoteUrl(
                "https://github.com/DissonantAU/bleatkan/tree/main/" +
                        "lib/src/main/kotlin"
            )

            localDirectory.set(file("src/main/kotlin"))
            remoteUrl("https://example.com/src")
            remoteLineSuffix.set("#L")
        }
    }

    // Dokka generates a new process managed by Gradle
    dokkaGeneratorIsolation = ProcessIsolation {
        // Configures heap size
        maxHeapSize = "4g"
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
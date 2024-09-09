import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.utils.addToStdlib.ifFalse


plugins {
    kotlin("jvm")
    kotlin("kapt")
    kotlin("plugin.serialization")
    `java-library`
}


group = "xyz.dissonant.lib.veadotube"

/* Version */
val versionMajor: Int = 0
val versionMinor: Int = 5
val versionPatch: Int = 0 //Is padded with 0 left if needed

val isRelease = System.getenv("IS_RELEASE") == "YES"
val versionSuffix:String = isRelease.ifFalse { "-DEV" }.orEmpty()

// Version becomes 1203
val versionCode: Int = versionMajor * 1000 + versionMinor * 100 + versionPatch
extra["versionCode"] = versionCode

// Version becomes 1.2.03 (Or 1.2.03-DEV etc.)
val versionName: String = "$versionMajor.$versionMinor.${versionPatch.toString().padStart(2, '0')}$versionSuffix"
extra["versionName"] = versionName
version = versionName



repositories {
    mavenCentral()
}

dependencies {
    /* Dep Versions */
    val versionKotlin:String by project
    val versionKtor:String by project
    val versionCoroutines:String by project
    val versionLog4j:String by project
    val versionJUnit:String by project
    val versionJUnitPlatformLauncher:String by project
    val versionKotlinxSerializationJson:String by project
    val versionSlf4j:String by project
    val versionKotlinLogging:String by project


    /* Main Dependencies */
    //Coroutines - concurrent library
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$versionCoroutines")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:$versionCoroutines")

    //Websocket (and HTTP) Framework
    implementation("io.ktor:ktor-client-core:$versionKtor")// KTOR for Websockets
    implementation("io.ktor:ktor-client-websockets:$versionKtor")
    implementation("io.ktor:ktor-client-logging:$versionKtor")
    // HTTP Engines - pick one
    implementation("io.ktor:ktor-client-cio:$versionKtor") // No HTTP/2 Support, fine for Veadotube Websockets
    //implementation("com.squareup.okhttp3:okhttp:4.12.0+") //Switching would need some changes in Connection.kt


    // JSON - probably best to use Probably KotlinX for JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$versionKotlinxSerializationJson")
    implementation("io.ktor:ktor-serialization:$versionKtor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$versionKtor")




    // Log4J
    implementation(platform("org.apache.logging.log4j:log4j-bom:$versionLog4j"))
    implementation("org.apache.logging.log4j:log4j-core:$versionLog4j")
    implementation("org.apache.logging.log4j:log4j-api:$versionLog4j")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:$versionLog4j")
    // Generic Logging Interface that can use Log4J - used in Ktor so may as well use here too
    implementation("org.slf4j:slf4j-api:$versionSlf4j")
    implementation("io.github.oshai:kotlin-logging-jvm:$versionKotlinLogging") //Kotlin Wrapper for slf4j

    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-debug:$versionCoroutines")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:$versionKotlin")
    testImplementation(platform("org.junit:junit-bom:$versionJUnit"))
    testImplementation("org.junit.jupiter:junit-jupiter:$versionJUnit")
    testImplementation("io.ktor:ktor-client-mock:$versionKtor")

    //testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:$versionJUnitPlatformLauncher")

}

tasks.test {
    useJUnitPlatform()
}


kotlin {
    jvmToolchain(8)

    compilerOptions { javaParameters = true }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}


tasks.withType<Jar> {
    archiveBaseName.set(rootProject.name)
    manifest {
        attributes(
            mapOf("Implementation-Title" to rootProject.name,
                "Implementation-Version" to project.version))
    }
}

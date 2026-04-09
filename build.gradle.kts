import java.text.SimpleDateFormat
import java.util.Date

plugins {
    java
    kotlin("jvm") version "2.3.20"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group   = "itdelatrisu"
version = "0.16.1"

val useXDG:       String  = project.findProperty("XDG") as String? ?: "false"
val excludeFFmpeg: Boolean = project.hasProperty("excludeFFmpeg")

// ---- Toolchain: Java 25 + Kotlin 2.3.20 ----

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    jvmToolchain(25)
}

// ---- Sources & resources ----

sourceSets {
    main {
        java.setSrcDirs(listOf("src"))
        kotlin.setSrcDirs(listOf("src"))
        resources.setSrcDirs(listOf("res"))
    }
}

application {
    mainClass.set("itdelatrisu.opsu.Opsu")
}

// ---- Repositories ----
// `repo/` is a vendored local Maven repository for artifacts not on Maven Central:
//   - org.lwjgl.lwjgl:lwjgl:2.9.4-SNAPSHOT   (custom LWJGL 2 build + platform natives)
//   - net.indiespot:media:0.8.9               (indiespot media library)

repositories {
    mavenCentral()
    maven { url = uri("${rootProject.projectDir}/repo") }
}

// ---- Dependencies ----

dependencies {
    implementation("org.lwjgl.lwjgl:lwjgl:2.9.4-SNAPSHOT") {
        exclude(group = "net.java.jinput", module = "jinput")
    }
    implementation("org.slick2d:slick2d-core:1.0.2") {
        exclude(group = "org.lwjgl.lwjgl", module = "lwjgl")
        exclude(group = "org.jcraft",      module = "jorbis")
        exclude(group = "javax.jnlp",      module = "jnlp-api")
    }
    implementation("net.lingala.zip4j:zip4j:1.3.2")
    implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4") {
        exclude(group = "com.googlecode.soundlibs", module = "tritonus-share")
    }
    implementation("com.googlecode.soundlibs:tritonus-all:0.3.7.2")
    implementation("org.xerial:sqlite-jdbc:3.15.1")
    implementation("org.json:json:20160810")
    implementation("net.java.dev.jna:jna:4.2.2")
    implementation("net.java.dev.jna:jna-platform:4.2.2")
    implementation("org.apache.maven:maven-artifact:3.3.3")
    implementation("org.tukaani:xz:1.6")
    implementation("net.indiespot:media:0.8.9")
}

// ---- Native library extraction ----

val nativePlatforms = listOf("windows", "linux", "osx", "all")

nativePlatforms.forEach { platform ->
    tasks.register("${platform}Natives") {
        val outputDir = layout.buildDirectory.dir("natives")
        outputs.dir(outputDir)
        doLast {
            copy {
                configurations.runtimeClasspath.get()
                    .resolvedConfiguration.resolvedArtifacts
                    .filter { it.classifier == "natives-$platform" }
                    .forEach { from(zipTree(it.file)) }
                into(outputDir.get())
            }
        }
    }
}

tasks.register("unpackNatives") {
    description = "Copies native libraries to the build directory."
    dependsOn(nativePlatforms.map { "${it}Natives" })
}

// ---- Resource processing ----

tasks.processResources {
    exclude("**/Thumbs.db")
    val now = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
    filesMatching("version") {
        expand("version" to project.version, "timestamp" to now)
    }
}

// ---- Run ----

tasks.named<JavaExec>("run") {
    dependsOn("unpackNatives")
}

// ---- Fat JAR (replaces maven-shade-plugin) ----

tasks.shadowJar {
    archiveBaseName.set("opsu")
    archiveClassifier.set("")
    manifest {
        attributes(
            "Main-Class" to "itdelatrisu.opsu.Opsu",
            "Use-XDG"   to useXDG
        )
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("**/Thumbs.db")
    if (excludeFFmpeg) exclude("ffmpeg*")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

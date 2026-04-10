import org.apache.tools.ant.filters.ReplaceTokens
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "2.3.20"
    application
    idea
}

group   = "itdelatrisu"
version = "0.16.1"

// ─── Toolchain ──────────────────────────────────────────────────────────────

kotlin {
    jvmToolchain(25)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ─── Repositories ───────────────────────────────────────────────────────────

repositories {
    mavenCentral()
    // TODO: net.indiespot:media нужна замена — пока берём из локального repo
    maven {
        url = uri("${rootProject.projectDir}/repo")
        content {
            includeGroup("net.indiespot")
        }
    }
}

// ─── Dependencies ───────────────────────────────────────────────────────────

dependencies {
    // Kotlin stdlib (automatically added by plugin, but explicit is fine)
    implementation(kotlin("stdlib"))

    // ── Graphics / Audio engine ───────────────────────────────────────────
    // TODO: org.newdawn.slick and LWJGL2 removed - all code using them WILL NOT COMPILE
    // Replace with LibGDX as we rewrite

    // ── Audio ────────────────────────────────────────────────────────────────
    implementation("com.googlecode.soundlibs:jlayer:1.0.1.4")
    implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4") {
        exclude(group = "com.googlecode.soundlibs", module = "tritonus-share")
    }
    implementation("com.googlecode.soundlibs:tritonus-all:0.3.7.2")

    // ── Compression / Archiving ───────────────────────────────────────────────
    implementation("net.lingala.zip4j:zip4j:1.3.2")
    implementation("org.tukaani:xz:1.6")

    // ── Database ──────────────────────────────────────────────────────────────
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")  // latest stable

    // ── JSON ──────────────────────────────────────────────────────────────────
    implementation("org.json:json:20240303")

    // ── Native interop (JNA) ──────────────────────────────────────────────────
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    // ── Version comparison ────────────────────────────────────────────────────
    implementation("org.apache.maven:maven-artifact:3.9.6")

    // ── Database ORM (Exposed) ───────────────────────────────────────────────
    val exposed = "0.61.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed")

    // ── Video (FFmpeg wrapper) ────────────────────────────────────────────────
    // TODO: net.indiespot:media - find a replacement or cut it out
    implementation("net.indiespot:media:0.8.9")
}

// ─── Source sets ────────────────────────────────────────────────────────────

sourceSets {
    main {
        kotlin { srcDir("src") }
        java   { srcDir("src") }
        resources { srcDir("res") }
    }
}

// ─── Application ────────────────────────────────────────────────────────────

application {
    mainClass = "itdelatrisu.opsu.Opsu"

    // Java 25 + LWJGL2/Slick2D/JNA requires these to silence
    // InaccessibleObjectException and enable native access.
    applicationDefaultJvmArgs = jvmArgs()
}

fun jvmArgs(): List<String> = listOf(
    // ── Reflection access for LWJGL2 / Slick2D ────────────────────────────
    "--add-opens", "java.base/java.lang=ALL-UNNAMED",
    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens", "java.base/java.nio=ALL-UNNAMED",
    "--add-opens", "java.base/java.util=ALL-UNNAMED",
    "--add-opens", "java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",

    // ── AWT / desktop access (Slick2D uses AWT under the hood) ────────────
    "--add-opens", "java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.awt.image=ALL-UNNAMED",
    "--add-opens", "java.desktop/sun.java2d=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens", "java.desktop/java.awt.image=ALL-UNNAMED",

    // ── JNA native access (required since Java 22) ────────────────────────
    "--enable-native-access=ALL-UNNAMED",

    // ── LWJGL2 library path is set at runtime, but this suppresses warns ──
    "-Dorg.lwjgl.util.NoChecks=true",

    // ── Prevent headless mode when running from CLI ────────────────────────
    "-Djava.awt.headless=false",

    // ── GC: throughput-oriented (game loop benefits from lower pause) ──────
    "-XX:+UseZGC",
    "-XX:+ZGenerational",
)

// ─── Resources: inject version + build date ─────────────────────────────────

tasks.processResources {
    exclude("**/Thumbs.db")
    filesMatching("version") {
        expand(
            "version"   to project.version,
            "timestamp" to LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
        )
    }
}

// ─── Natives unpacking ───────────────────────────────────────────────────────

val nativePlatforms = listOf("windows", "linux", "osx", "all")

val unpackNatives by tasks.registering {
    description = "Copies native libraries to the build directory."
    group = "build"
    inputs.files(configurations.runtimeClasspath)
    val outputDir = layout.buildDirectory.dir("natives")
    outputs.dir(outputDir)

    doLast {
        val dir = outputDir.get().asFile
        dir.mkdirs()
        configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
            .filter { artifact ->
                nativePlatforms.any { platform -> artifact.classifier == "natives-$platform" }
            }
            .forEach { artifact ->
                copy {
                    from(zipTree(artifact.file))
                    into(dir)
                }
            }
    }
}

tasks.run.configure {
    dependsOn(unpackNatives)
    jvmArgs(jvmArgs())
    // Inject native library path at run time
    doFirst {
        val nativesDir = layout.buildDirectory.dir("natives").get().asFile.absolutePath
        jvmArgs("-Dorg.lwjgl.librarypath=$nativesDir", "-Djava.library.path=$nativesDir")
    }
}

// ─── Fat JAR ─────────────────────────────────────────────────────────────────

tasks.jar {
    dependsOn(unpackNatives)
    archiveBaseName = "opsu"

    manifest {
        attributes(
            "Implementation-Title"   to "opsu!",
            "Implementation-Version" to project.version,
            "Main-Class"             to application.mainClass.get(),
        )
    }

    // Merge all runtime deps into a single jar
    from(configurations.runtimeClasspath.map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "**/Thumbs.db")

    // Allow excluding FFmpeg natives (e.g. -PexcludeFFmpeg)
    if (project.hasProperty("excludeFFmpeg")) {
        exclude("ffmpeg*")
    }

    // Force rebuild every time (resources/version stamp changes)
    outputs.upToDateWhen { false }
}

// ─── Kotlin compiler options ─────────────────────────────────────────────────

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        // Explicit API is opt-in for now; enable once migration further along
        // explicitApi()
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",            // default interface methods in bytecode
            "-opt-in=kotlin.RequiresOptIn", // suppress OptIn warnings
        )
    }
}

// ─── IDE ─────────────────────────────────────────────────────────────────────

idea {
    module {
        isDownloadSources = true
    }
}

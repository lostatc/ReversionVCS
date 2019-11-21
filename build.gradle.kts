import org.beryx.runtime.RuntimeTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.50"
    application
    id("org.beryx.runtime") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

group = "io.github.lostatc"

version = "0.2.2"

sourceSets {
    val api by creating
    val main by getting {
        compileClasspath += api.output
        runtimeClasspath += api.output
    }
    val test by getting {
        compileClasspath += api.output
        runtimeClasspath += api.output
    }
}

configurations {
    val implementation by getting
    val apiImplementation by getting {
        extendsFrom(implementation)
    }
}

repositories {
    jcenter()
}

val jfxPlatforms = listOf("linux", "mac", "win")

dependencies {
    // Kotlin standard library
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.2.1")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-javafx", version = "1.2.1")

    // Database access
    implementation(group = "org.jetbrains.exposed", name = "exposed", version = "0.13.6")
    implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.28.0")

    // Serialization
    api(group = "com.google.code.gson", name = "gson", version = "2.8.5")

    // Miscellaneous I/O
    implementation(group = "commons-codec", name = "commons-codec", version = "1.12")
    implementation(group = "commons-io", name = "commons-io", version = "2.6")

    // Logging
    api(group = "org.slf4j", name = "slf4j-api", version = "1.7.26")
    implementation(group = "ch.qos.logback", name = "logback-core", version = "1.2.3")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")

    // GUI
    implementation(group = "com.jfoenix", name = "jfoenix", version = "9.0.8")
    implementation(group = "org.kordamp.ikonli", name = "ikonli-javafx", version = "11.3.4")
    implementation(group = "org.kordamp.ikonli", name = "ikonli-material-pack", version = "11.3.4")

    // JavaFX
    for (platform in jfxPlatforms) {
        implementation(group = "org.openjfx", name = "javafx-base", version = "12.0.1", classifier = platform)
        implementation(group = "org.openjfx", name = "javafx-controls", version = "12.0.1", classifier = platform)
        implementation(group = "org.openjfx", name = "javafx-graphics", version = "12.0.1", classifier = platform)
        implementation(group = "org.openjfx", name = "javafx-fxml", version = "12.0.1", classifier = platform)
    }

    // FUSE file system
    implementation(group = "com.github.serceman", name = "jnr-fuse", version = "0.5.3")

    // Preventing multiple application instances
    implementation(group = "de.huxhorn.lilith", name = "de.huxhorn.lilith.3rdparty.junique", version = "1.0.4")

    // Unit testing
    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.4.2")
}

application {
    applicationName = "Reversion"
    mainClassName = "io.github.lostatc.reversion.MainKt"
}

runtime {
    addOptions("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages")
    imageZip.set(file("$buildDir/image-zip/Reversion-$version.zip"))

    targetPlatform("windows", System.getenv("JDK_WIN_HOME"))
    targetPlatform("mac", System.getenv("JDK_MAC_HOME"))
    targetPlatform("linux", System.getenv("JDK_LINUX_HOME"))
}

// Add launcher scripts to the images.
tasks.getByName<RuntimeTask>("runtime") {
    doLast {
        imageDirAsFile.listFiles()?.forEach { imageDir ->
            copy {
                from("src/main/resources/bin/Reversion.vbs")
                from("src/main/resources/bin/Reversion")
                into(imageDir)
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xuse-experimental=kotlin.Experimental"
}

tasks.withType<Jar> {
    archiveBaseName.set("reversion")
    from(sourceSets["api"].output, sourceSets["main"].output)
}

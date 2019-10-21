plugins {
    kotlin("jvm")
    application
    id("org.openjfx.javafxplugin") version "0.0.7"
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

version = "0.1.0"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.2.1")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-javafx", version = "1.2.1")

    implementation(project(":api"))

    // Database access
    implementation(group = "org.jetbrains.exposed", name = "exposed", version = "0.13.6")
    implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.28.0")

    // CLI argument parsing
    implementation(group = "com.github.ajalt", name = "clikt", version = "1.6.0")

    // Serialization
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.5")

    // Miscellaneous I/O
    implementation(group = "commons-codec", name = "commons-codec", version = "1.12")
    implementation(group = "commons-io", name = "commons-io", version = "2.6")

    // Logging
    implementation(group = "ch.qos.logback", name = "logback-core", version = "1.2.3")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")

    // GUI
    implementation(group = "com.jfoenix", name = "jfoenix", version = "9.0.8")
    implementation(group = "org.kordamp.ikonli", name = "ikonli-javafx", version = "11.3.4")
    implementation(group = "org.kordamp.ikonli", name = "ikonli-material-pack", version = "11.3.4")

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

tasks.withType<Jar> {
    archiveBaseName.set("reversion")
}

javafx {
    version = "12.0.1"
    modules("javafx.fxml", "javafx.controls")
}

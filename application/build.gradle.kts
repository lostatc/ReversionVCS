plugins {
    kotlin("jvm")
    application
    id("org.openjfx.javafxplugin") version "0.0.7"
}

version = "0.1.0"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.2.1")
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-javafx", version = "1.2.1")
    implementation(project(":api"))

    implementation(group = "org.jetbrains.exposed", name = "exposed", version = "0.13.6")
    implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.21.0.1")
    implementation(group = "com.github.ajalt", name = "clikt", version = "1.6.0")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.5")
    implementation(group = "commons-codec", name = "commons-codec", version = "1.12")
    implementation(group = "commons-io", name = "commons-io", version = "2.6")
    implementation(group = "ch.qos.logback", name = "logback-core", version = "1.2.3")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")
    implementation(group = "com.jfoenix", name = "jfoenix", version = "9.0.8")
    implementation(group = "org.kordamp.ikonli", name = "ikonli-javafx", version = "11.3.4")
    implementation(group = "org.kordamp.ikonli", name = "ikonli-material-pack", version = "11.3.4")
    implementation(group = "com.github.serceman", name = "jnr-fuse", version = "0.5.3")

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.4.2")
}

application {
    applicationName = "reversion-gui"
    mainClassName = "io.github.lostatc.reversion.gui.MainKt"
}


javafx {
    version = "12.0.1"
    modules("javafx.fxml", "javafx.controls")
}

tasks {
    val startScripts = getByName<CreateStartScripts>("startScripts")

    register<CreateStartScripts>("cliScript") {
        applicationName = "reversion"
        mainClassName = "io.github.lostatc.reversion.cli.MainKt"
        outputDir = startScripts.outputDir
        classpath = startScripts.classpath
    }

    register<CreateStartScripts>("daemonScript") {
        applicationName = "reversiond"
        mainClassName = "io.github.lostatc.reversion.daemon.MainKt"
        outputDir = startScripts.outputDir
        classpath = startScripts.classpath
    }

    startScripts.dependsOn("cliScript")
    startScripts.dependsOn("daemonScript")
}

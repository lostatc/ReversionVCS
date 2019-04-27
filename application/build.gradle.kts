plugins {
    kotlin("jvm")
    application
}

version = "0.1.0"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":api"))

    implementation(group = "org.jetbrains.exposed", name = "exposed", version = "0.13.6")
    implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.21.0.1")
    implementation(group = "com.github.ajalt", name = "clikt", version = "1.6.0")
    implementation(group = "org.zeroturnaround", name = "zt-zip", version = "1.13")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.5")
    implementation(group = "commons-io", name = "commons-io", version = "2.6")
    implementation(group = "ch.qos.logback", name = "logback-core", version = "1.2.3")
    implementation(group = "ch.qos.logback", name = "logback-classic", version = "1.2.3")

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.4.2")
}

application {
    mainClassName = "io.github.lostatc.reversion.MainKt"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

plugins {
    kotlin("jvm") version "1.3.11"
    application
}

group = "io.github.lostatc"
version = "0.1.0"

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(group = "org.jetbrains.exposed", name = "exposed", version = "0.13.2")
    implementation(group = "org.xerial", name = "sqlite-jdbc", version = "3.21.0.1")
    implementation(group = "com.github.ajalt", name = "clikt", version = "1.6.0")
    implementation(group = "net.harawata", name = "appdirs", version = "1.0.3")
    implementation(group = "commons-io", name = "commons-io", version = "2.6")
    implementation(group = "commons-codec", name = "commons-codec", version = "1.12")
    implementation(group = "org.zeroturnaround", name = "zt-zip", version = "1.13")
    implementation(group = "com.google.code.gson", name = "gson", version = "2.8.5")

}

application {
    mainClassName = "io.github.lostatc.reversion.MainKt"
}

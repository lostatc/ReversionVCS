plugins {
    kotlin("jvm")
}

version = "0.1.0"

dependencies {
    api(group = "org.slf4j", name = "slf4j-api", version = "1.7.26")
    implementation(kotlin("stdlib-jdk8"))
    implementation(group = "commons-codec", name = "commons-codec", version = "1.12")
    implementation(group = "commons-io", name = "commons-io", version = "2.6")
}

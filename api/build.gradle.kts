plugins {
    kotlin("jvm")
}

version = "0.1.0"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation(group = "commons-codec", name = "commons-codec", version = "1.12")
    implementation(group = "commons-io", name = "commons-io", version = "2.6")
}

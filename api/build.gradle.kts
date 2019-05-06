plugins {
    kotlin("jvm")
}

version = "0.1.0"

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    api(group = "org.slf4j", name = "slf4j-api", version = "1.7.26")
    implementation(group = "commons-codec", name = "commons-codec", version = "1.12")
    implementation(group = "commons-io", name = "commons-io", version = "2.6")

    testImplementation(group = "org.junit.jupiter", name = "junit-jupiter", version = "5.4.2")
}

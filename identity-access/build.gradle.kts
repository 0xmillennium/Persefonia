plugins {
    `java-library`
}

dependencies {
    implementation(project(":shared-kernel"))

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

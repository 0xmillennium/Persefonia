plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.spring.boot.dependencies))

    implementation(project(":shared-kernel"))
    implementation(project(":content-publishing"))
    implementation(libs.spring.webmvc)

    compileOnly("jakarta.servlet:jakarta.servlet-api")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

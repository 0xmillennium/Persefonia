plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":shared-kernel"))
    implementation(libs.spring.webmvc)
}

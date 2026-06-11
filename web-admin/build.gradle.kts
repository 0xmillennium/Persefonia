plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":shared-kernel"))
    implementation(project(":content-publishing"))
    implementation(libs.spring.webmvc)
    implementation(libs.spring.security.core)
    implementation(libs.spring.security.web)
}

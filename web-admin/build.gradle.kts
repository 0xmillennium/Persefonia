plugins {
    `java-library`
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(project(":shared-kernel"))
    implementation(project(":taxonomy"))
    implementation(project(":content-publishing"))
    implementation(project(":profile-portfolio"))
    implementation(project(":media-library"))
    implementation(project(":communication"))
    implementation(project(":discovery"))
    implementation(project(":insights"))
    implementation(project(":audit"))
    implementation(libs.spring.webmvc)
    implementation(libs.spring.security.core)
    implementation(libs.spring.security.web)
}

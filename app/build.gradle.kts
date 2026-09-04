import org.gradle.api.tasks.Exec
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.jte)
    java
}

springBoot {
    buildInfo {
        excludes.set(setOf("group", "artifact", "time"))
        properties {
            name.set("persefonia")
        }
    }
}

val precompiledJteDirectory = layout.buildDirectory.dir("generated/jte-classes")

jte {
    precompile()
    targetDirectory.set(precompiledJteDirectory.map { it.asFile.toPath() })
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.spring.boot.dependencies))

    implementation(project(":shared-kernel"))
    implementation(project(":identity-access"))
    implementation(project(":taxonomy"))
    implementation(project(":content-publishing"))
    implementation(project(":profile-portfolio"))
    implementation(project(":media-library"))
    implementation(project(":communication"))
    implementation(project(":discovery"))
    implementation(project(":content-integrity"))
    implementation(project(":insights"))
    implementation(project(":audit"))
    implementation(project(":portability"))
    implementation(project(":platform-operations"))
    implementation(project(":web-public"))
    implementation(project(":web-admin"))

    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.data.jdbc)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.mail)
    implementation(libs.spring.boot.starter.oauth2.client)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jte.spring.boot4.starter)
    implementation(libs.commonmark)
    implementation(libs.jsoup)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    runtimeOnly(libs.micrometer.registry.prometheus)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
}

val frontendDirectory = rootProject.layout.projectDirectory.dir("frontend")
val generatedViteDirectory = layout.buildDirectory.dir("generated/vite")

val npmCi by tasks.registering(Exec::class) {
    workingDir(frontendDirectory)
    commandLine("npm", "ci")
    inputs.files(
        frontendDirectory.file("package.json"),
        frontendDirectory.file("package-lock.json")
    )
    outputs.dir(frontendDirectory.dir("node_modules"))
}

val npmRunTypecheck by tasks.registering(Exec::class) {
    dependsOn(npmCi)
    workingDir(frontendDirectory)
    commandLine("npm", "run", "typecheck")
    inputs.dir(frontendDirectory.dir("src"))
    inputs.files(
        frontendDirectory.file("package.json"),
        frontendDirectory.file("tsconfig.json")
    )
}

val viteBuild by tasks.registering(Exec::class) {
    dependsOn(npmRunTypecheck)
    workingDir(frontendDirectory)
    commandLine("npm", "run", "build")
    inputs.dir(frontendDirectory.dir("src"))
    inputs.files(
        frontendDirectory.file("package.json"),
        frontendDirectory.file("vite.config.ts")
    )
    outputs.dir(generatedViteDirectory)
}

tasks.processResources {
    dependsOn(viteBuild)
    from(generatedViteDirectory)
}

tasks.test {
    classpath += files(precompiledJteDirectory)
    maxHeapSize = "1g"
}

tasks.named<BootJar>("bootJar") {
    dependsOn("precompileJte")
    from(precompiledJteDirectory) {
        include("**/*.class")
        into("BOOT-INF/classes")
    }
}

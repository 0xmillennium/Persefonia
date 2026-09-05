import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
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

val integrationTestSourceSet = sourceSets.create("integrationTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output
}

val migrationTestSourceSet = sourceSets.create("migrationTest") {
    compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output + integrationTestSourceSet.output
    runtimeClasspath += sourceSets.main.get().output + sourceSets.test.get().output + integrationTestSourceSet.output
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

    add(integrationTestSourceSet.implementationConfigurationName, platform(libs.spring.boot.dependencies))
    add(integrationTestSourceSet.implementationConfigurationName, libs.spring.boot.starter.test)
    add(integrationTestSourceSet.implementationConfigurationName, libs.spring.boot.starter.webmvc.test)
    add(integrationTestSourceSet.implementationConfigurationName, libs.spring.security.test)
    add(integrationTestSourceSet.implementationConfigurationName, libs.archunit.junit5)
    add(integrationTestSourceSet.implementationConfigurationName, libs.testcontainers)
    add(integrationTestSourceSet.implementationConfigurationName, libs.testcontainers.junit.jupiter)
    add(integrationTestSourceSet.implementationConfigurationName, libs.testcontainers.postgresql)

    add(migrationTestSourceSet.implementationConfigurationName, platform(libs.spring.boot.dependencies))
    add(migrationTestSourceSet.implementationConfigurationName, libs.spring.boot.starter.test)
    add(migrationTestSourceSet.implementationConfigurationName, libs.testcontainers)
    add(migrationTestSourceSet.implementationConfigurationName, libs.testcontainers.junit.jupiter)
    add(migrationTestSourceSet.implementationConfigurationName, libs.testcontainers.postgresql)
    add(migrationTestSourceSet.implementationConfigurationName, libs.flyway.core)
    add(migrationTestSourceSet.implementationConfigurationName, libs.flyway.database.postgresql)
}

configurations.named(integrationTestSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named(integrationTestSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}
configurations.named(migrationTestSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named(migrationTestSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

val integrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs PostgreSQL-backed application integration tests."
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    classpath += files(precompiledJteDirectory)
    dependsOn("precompileJte")
    shouldRunAfter(tasks.test)
    systemProperty("spring.flyway.enabled", "false")
    maxParallelForks = 1
    maxHeapSize = "1g"
}

val migrationTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs PostgreSQL-backed schema and migration tests."
    testClassesDirs = migrationTestSourceSet.output.classesDirs
    classpath = migrationTestSourceSet.runtimeClasspath
    classpath += files(precompiledJteDirectory)
    dependsOn("precompileJte")
    dependsOn(tasks.named(integrationTestSourceSet.classesTaskName))
    shouldRunAfter(integrationTest)
    maxParallelForks = 1
    maxHeapSize = "1g"
}

tasks.withType<Test>().configureEach {
    System.getProperty("org.springframework.test.context.cache")?.let { value ->
        systemProperty("org.springframework.test.context.cache", value)
        systemProperty("logging.level.org.springframework.test.context.cache", value)
    }
    System.getProperty("spring.test.context.cache.maxSize")?.let { value ->
        systemProperty("spring.test.context.cache.maxSize", value)
    }
}

tasks.check {
    dependsOn(integrationTest, migrationTest)
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
    maxParallelForks = providers.gradleProperty("fastTestMaxParallelForks")
        .map(String::toInt)
        .getOrElse(1)
}

tasks.named<BootJar>("bootJar") {
    dependsOn("precompileJte")
    archiveFileName.set("persefonia.jar")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(precompiledJteDirectory) {
        include("**/*.class")
        into("BOOT-INF/classes")
    }
}

tasks.named("jar") {
    enabled = false
}

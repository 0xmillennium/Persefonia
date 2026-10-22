import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import org.springframework.boot.gradle.tasks.bundling.BootJar
import org.springframework.boot.gradle.tasks.run.BootRun

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
    compileArgs.set(arrayOf("--release", "21"))
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
    implementation(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.database.postgresql)
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

val jvmCheck by tasks.registering {
    group = "verification"
    description = "Runs application JVM, integration and migration checks in the producer workspace."
    dependsOn(tasks.test, integrationTest, migrationTest)
}

tasks.check {
    dependsOn(jvmCheck)
}

val frontendDirectory = rootProject.layout.projectDirectory.dir("frontend")
val generatedViteDirectory = layout.buildDirectory.dir("generated/vite")

val dependencyInputs = providers.exec {
    workingDir(frontendDirectory)
    commandLine("node", "scripts/verification/dependency-inputs.mjs")
}.standardOutput.asText.map { it.trim() }

val npmCi by tasks.registering(Exec::class) {
    workingDir(frontendDirectory)
    commandLine("npm", "ci")
    inputs.files(
        frontendDirectory.file("package.json"),
        frontendDirectory.file("package-lock.json")
    )
    inputs.property("nodePlatform", System.getProperty("os.name") + "-" + System.getProperty("os.arch"))
    outputs.dir(frontendDirectory.dir("node_modules"))
    inputs.file(frontendDirectory.file("scripts/verification/dependency-inputs.mjs"))
    inputs.property("effectiveDependencyInputs", dependencyInputs)
    // Gradle fingerprints the actual installed outputs, including the hidden package lock.
    // A bare node_modules directory or a separate success stamp cannot satisfy this task.
    val allowLocalDependencyReuse = !providers.environmentVariable("CI").isPresent
    outputs.upToDateWhen { allowLocalDependencyReuse }
}

val npmRunTypecheck by tasks.registering(Exec::class) {
    dependsOn(npmCi)
    workingDir(frontendDirectory)
    commandLine("npm", "run", "typecheck")
    inputs.dir(frontendDirectory.dir("src"))
    inputs.dir(frontendDirectory.dir("scripts"))
    inputs.dir(frontendDirectory.dir("e2e"))
    inputs.files(
        frontendDirectory.file("package.json"),
        frontendDirectory.file("tsconfig.json"),
        frontendDirectory.file("tsconfig.app.json"),
        frontendDirectory.file("tsconfig.tooling.json"),
        frontendDirectory.file("tsconfig.tests.json")
    )
}

val npmRunUnitTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs all frontend unit tests, including deterministic physics stress pipelines."
    dependsOn(npmCi)
    workingDir(frontendDirectory)
    commandLine("node", "scripts/verification/unit-run.mjs", "--comprehensive")
    inputs.dir(frontendDirectory.dir("src"))
    inputs.dir(frontendDirectory.dir("scripts"))
    inputs.dir(frontendDirectory.dir("e2e"))
    inputs.files(
        frontendDirectory.file("package.json"),
        frontendDirectory.file("vitest.config.ts")
    )
}

val frontendCheck by tasks.registering {
    group = "verification"
    description = "Runs independently reconciled full frontend unit and both 100-seed stress pipelines."
    dependsOn(npmRunUnitTest)
}

tasks.check {
    dependsOn(frontendCheck)
}

val viteBuild by tasks.registering(Exec::class) {
    dependsOn(npmRunTypecheck)
    workingDir(frontendDirectory)
    commandLine("npm", "run", "build")
    inputs.dir(frontendDirectory.dir("src"))
    inputs.dir(frontendDirectory.dir("public"))
    inputs.dir(frontendDirectory.dir("scripts"))
    inputs.files(
        frontendDirectory.file("package.json"),
        frontendDirectory.file("package-lock.json"),
        frontendDirectory.file("vite.config.ts")
    )
    inputs.files(frontendDirectory.asFileTree.matching {
        include("tsconfig*.json", ".npmrc")
    })
    outputs.dir(generatedViteDirectory)
    inputs.property("effectiveDependencyInputs", dependencyInputs)
    inputs.files(rootProject.layout.projectDirectory.asFileTree.matching {
        include("package.json", "package-lock.json", ".npmrc")
    })
    // Output fingerprints detect missing/changed generated assets. Test freshness is separate.
    outputs.cacheIf { false }
}

tasks.processResources {
    dependsOn(viteBuild)
    from(generatedViteDirectory)
}

tasks.register<Exec>("browserCheck") {
    description = "Runs explicit comprehensive local browser diagnostics (expensive); CI uses the native artifact lifecycle."
    dependsOn(tasks.named("bootJar"), npmCi)
    workingDir(rootProject.layout.projectDirectory)
    commandLine("bash", "scripts/browser/check.sh")
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

tasks.named<BootRun>("bootRun") {
    dependsOn("precompileJte")
    classpath += files(precompiledJteDirectory)
}

tasks.named("jar") {
    enabled = false
}

// Data only, consumed in this producer workspace after its full JVM tests. No compile/install dependencies.
val adminPreviewRuntime = sourceSets.test.get().runtimeClasspath + files(precompiledJteDirectory)
val adminPreviewClasspathFile = layout.buildDirectory.file("admin-preview/classpath.json")
tasks.register("writeAdminPreviewClasspath") {
    group = "verification"
    description = "Exports the already-built own-workspace test runtime for the guarded native admin preview."
    doLast {
        val destination = adminPreviewClasspathFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(groovy.json.JsonOutput.toJson(mapOf(
            "schema" to 1, "classpath" to adminPreviewRuntime.files.map { it.absolutePath }
        )) + "\n")
    }
}

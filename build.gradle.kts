import org.gradle.process.ExecOperations
import javax.inject.Inject

import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin

abstract class ProductionSnapshot @Inject constructor(private val processes: ExecOperations) : Action<Task> {
    lateinit var rootDirectory: java.io.File
    lateinit var destination: java.io.File
    lateinit var production: org.gradle.api.file.FileCollection
    var gradleVersion: String = org.gradle.util.GradleVersion.current().version
    var compilerMetadata: Map<String, String> = emptyMap()
    var resolvedTestRuntimes: Map<String, Map<String, String>> = emptyMap()
    var witness: Boolean = false
    var resolvedTests: Map<String, org.gradle.api.file.FileCollection> = emptyMap()

    companion object {
        fun metadata(value: org.gradle.jvm.toolchain.JavaInstallationMetadata) = mapOf(
            "languageVersion" to value.languageVersion.toString(), "vendor" to value.vendor,
            "runtimeVersion" to value.javaRuntimeVersion, "jvmVersion" to value.jvmVersion)
        fun runtime(task: Test) = metadata(task.javaLauncher.get().metadata) + mapOf(
            "maxParallelForks" to task.maxParallelForks.toString(),
            "maxHeapSize" to (task.maxHeapSize ?: "default"),
            "customJvmArguments" to task.jvmArgs.isNotEmpty().toString(),
            "assertions" to task.enableAssertions.toString(),
            "hasFilters" to (task.filter.includePatterns.isNotEmpty() || task.filter.excludePatterns.isNotEmpty()).toString())
    }

    override fun execute(task: Task) {
        val arguments = mutableListOf<Any>("python3", "-B", "scripts/ci/production-manifest.py",
            destination, "--task", task.path, "--classpath", production.asPath,
            "--gradle-version", gradleVersion, "--compiler-metadata", groovy.json.JsonOutput.toJson(compilerMetadata))
        if (witness) {
            arguments.addAll(listOf("--executed-classpath", (task as Test).classpath.asPath,
                "--test-runtime", groovy.json.JsonOutput.toJson(runtime(task))))
        }
        if (resolvedTestRuntimes.isNotEmpty()) arguments.addAll(listOf("--resolved-test-runtimes",
            groovy.json.JsonOutput.toJson(resolvedTestRuntimes)))
        if (resolvedTests.isNotEmpty()) {
            val encoded = groovy.json.JsonOutput.toJson(resolvedTests.mapValues { it.value.asPath })
            arguments.addAll(listOf("--resolved-test-classpaths", encoded))
        }
        processes.exec {
            workingDir(rootDirectory)
            commandLine(arguments)
        }
    }
}

/** Monotonic task phases complement the existing command/receipt timing owner. */
class VerificationPhaseClock(
    private val destination: java.io.File,
    private val clock: java.io.File,
    private val finish: Boolean,
    private val attempt: String,
) : Action<Task>, java.io.Serializable {
    companion object { private val lock = Any() }
    override fun execute(task: Task) {
        val now = System.nanoTime()
        clock.parentFile.mkdirs()
        val seconds = if (finish) (now - clock.readText().trim().toLong()) / 1_000_000_000.0 else null
        if (!finish) clock.writeText(now.toString())
        val row = groovy.json.JsonOutput.toJson(mapOf("phase" to task.path,
            "event" to if (finish) "finished" else "started", "seconds" to seconds,
            "exitCode" to if (finish) 0 else null, "attempt" to attempt,
            "outcome" to if (finish) "passed" else "running"))
        synchronized(lock) {
            destination.parentFile.mkdirs()
            destination.appendText(row + "\n")
        }
    }
}

allprojects {
    group = "dev.persefonia"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withType<JavaPlugin> {
        extensions.configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(25))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.release.set(21)
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
}

val automationCheck by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies repository automation contracts."
    dependsOn(":automation-tests:check")
}

val applicationCheck by tasks.registering {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies application and domain modules."
    dependsOn(subprojects.filter { it.path != ":automation-tests" }.map { "${it.path}:check" })
}

tasks.register("check") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies automation and application modules."
    dependsOn(automationCheck, applicationCheck)
}

// CI ownership is explicit; applicationCheck and app.check retain their complete local meaning.
tasks.register("applicationJvmCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies all application JVM modules without the independent frontend test lane."
    dependsOn(":app:jvmCheck")
    dependsOn(subprojects.filter { it.path != ":automation-tests" && it.path != ":app" }
        .map { "${it.path}:check" })
}

tasks.register("frontendCheck") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Verifies the complete frontend discovery, execution and stress contract."
    dependsOn(":app:frontendCheck")
}

tasks.register("qualificationInputs") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Builds deterministic production comparison manifests without running test suites."
}

// A manifest is captured before every actual Test task and independently for comparison.
// It contains hashes, never an imported executable classpath or another runner's classes.
subprojects {
    plugins.withType<JavaPlugin> {
        if (path != ":automation-tests") {
            val production = extensions.getByType<org.gradle.api.tasks.SourceSetContainer>()
                .named("main").get().runtimeClasspath
            val templateOutput = if (path == ":app") files(layout.buildDirectory.dir("generated/jte-classes")) else files()
            val completeProduction = production + templateOutput
            val repositoryDirectory = rootProject.layout.projectDirectory.asFile
            val snapshot by tasks.registering {
                group = LifecycleBasePlugin.VERIFICATION_GROUP
                dependsOn(tasks.named("classes"), production.buildDependencies)
                if (project.path == ":app") dependsOn("precompileJte")
                val action = objects.newInstance(ProductionSnapshot::class.java).apply {
                    rootDirectory = repositoryDirectory
                    destination = layout.buildDirectory.file("qualification/production.json").get().asFile
                    this.production = completeProduction
                    compilerMetadata = ProductionSnapshot.metadata(project.tasks.named<JavaCompile>("compileJava").get().javaCompiler.get().metadata)
                    resolvedTests = project.tasks.withType<Test>().associate { it.name to it.classpath }
                    resolvedTestRuntimes = project.tasks.withType<Test>().associate { it.name to ProductionSnapshot.runtime(it) }
                }
                doLast(action)
            }
            tasks.withType<Test>().configureEach {
                outputs.upToDateWhen { false }
                outputs.cacheIf { false }
                val action = objects.newInstance(ProductionSnapshot::class.java).apply {
                    rootDirectory = repositoryDirectory
                    destination = layout.buildDirectory.file("qualification/${name}.json").get().asFile
                    this.production = completeProduction
                    compilerMetadata = ProductionSnapshot.metadata(project.tasks.named<JavaCompile>("compileJava").get().javaCompiler.get().metadata)
                    witness = true
                }
                doFirst(action)
            }
            rootProject.tasks.named("qualificationInputs") { dependsOn(snapshot) }
        }
    }
}

subprojects {
    tasks.configureEach {
        if (project.path != ":automation-tests" && (this is Test || this is JavaCompile ||
            name in setOf("processResources", "npmCi", "npmRunTypecheck", "viteBuild", "precompileJte", "bootJar", "npmRunUnitTest"))) {
            val phaseFile = rootProject.layout.buildDirectory.file("ci/task-timings.jsonl").get().asFile
            val clockFile = rootProject.layout.buildDirectory.file("ci/task-clocks/" +
                java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8)).get().asFile
            val attempt = providers.environmentVariable("GITHUB_RUN_ATTEMPT").getOrElse("1")
            doFirst(VerificationPhaseClock(phaseFile, clockFile, false, attempt))
            doLast(VerificationPhaseClock(phaseFile, clockFile, true, attempt))
        }
    }
}

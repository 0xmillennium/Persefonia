import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin

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

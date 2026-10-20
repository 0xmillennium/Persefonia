plugins {
    java
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("org.snakeyaml:snakeyaml-engine:2.10")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks.test {
    // Contracts inspect executable repository files, their absence, and git's tracked
    // inventory. Compiled test classes alone cannot describe that changing input set.
    outputs.upToDateWhen { false }
    outputs.cacheIf { false }
}

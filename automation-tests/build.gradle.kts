plugins {
    java
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.assertj:assertj-core:3.27.6")
    testImplementation("org.snakeyaml:snakeyaml-engine:2.10")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

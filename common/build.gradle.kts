plugins {
    id("java-library")
}

// Per-version properties (versions/<mc>/gradle.properties) live on the root node.
val rootNode = stonecutter.node.sibling("")!!.project
val javaVersion = rootNode.property("java.version").toString().toInt()

group = property("mod.group").toString()
version = "${property("mod.version")}+${stonecutter.current.version}"

base {
    archivesName = "safe-zone-common"
}

dependencies {
    api("com.google.code.gson:gson:${property("deps.gson")}")
    api("org.slf4j:slf4j-api:${property("deps.slf4j")}")

    testImplementation(platform("org.junit:junit-bom:${property("deps.junit_bom")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    withSourcesJar()
}

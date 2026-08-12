plugins {
    id("java-library")
}

val requiredJava: JavaVersion =
    if (stonecutter.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

group = property("mod.group").toString()
version = "${property("mod.version")}+${stonecutter.current.version}"

base {
    archivesName = "safe-zone-common"
}

dependencies {
    api("com.google.code.gson:gson:2.13.2")
    api("org.slf4j:slf4j-api:2.0.17")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion.toInt())
    }
    withSourcesJar()
}

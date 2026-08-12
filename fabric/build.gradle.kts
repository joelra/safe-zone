plugins {
    // Applies the correct Fabric Loom variant for the active Minecraft version
    // (obfuscated pre-26.1 vs. unobfuscated 26.1+).
    id("dev.kikugie.loom-back-compat")
    id("maven-publish")
}

val requiredJava: JavaVersion =
    if (stonecutter.current.parsed >= "26.1") JavaVersion.VERSION_25 else JavaVersion.VERSION_21

group = property("mod.group").toString()
version = "${property("mod.version")}+${stonecutter.current.version}"

base {
    archivesName = "SafeZone-Fabric"
}

// Per-version properties (versions/<mc>/gradle.properties) live on the root node.
val rootNode = stonecutter.node.sibling("")!!.project

// The shared `common` module is pure Java (no Minecraft/version-specific code), so we
// compile its source straight into the loader jar instead of taking a project dependency
// (loom + sibling-project deps produce a compileJava cycle). The `common` branch still
// builds and runs its own unit tests independently.
sourceSets.named("main") {
    java.srcDir(rootProject.file("common/src/main/java"))
}

dependencies {
    minecraft("com.mojang:minecraft:${stonecutter.current.version}")
    // No-op on unobfuscated 26.1+, applies Mojang mappings on obfuscated versions.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${rootNode.property("fabric.loader.version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${rootNode.property("fabric.api.version")}")

    // Transitive dependencies of the common module (provided at runtime by Minecraft / the loader).
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.slf4j:slf4j-api:2.0.17")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

loom {
    runConfigs.configureEach {
        runDir = rootProject.file("run/fabric").relativeTo(projectDir).invariantSeparatorsPath
    }
}

fabricApi {
    configureDataGeneration()
}

tasks.processResources {
    val expansions = mapOf(
        "version" to project.version.toString(),
        "minecraft" to rootNode.property("minecraft.dependency").toString(),
        "fabric_loader" to rootNode.property("fabric.loader.version").toString(),
        "fabric_api" to rootNode.property("fabric.api.version").toString(),
        "java_min" to requiredJava.majorVersion,
    )
    inputs.properties(expansions)
    filesMatching("fabric.mod.json") { expand(expansions) }

    val mixinJava = "JAVA_${requiredJava.majorVersion}"
    inputs.property("mixinJava", mixinJava)
    filesMatching("*.mixins.json") { expand("java" to mixinJava) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = requiredJava.majorVersion.toInt()
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion.toInt())
    }
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_SafeZone-Fabric" }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

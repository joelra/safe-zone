plugins {
    // Applies the correct Fabric Loom variant for the active Minecraft version
    // (obfuscated pre-26.1 vs. unobfuscated 26.1+).
    id("dev.kikugie.loom-back-compat")
    id("maven-publish")
}

// Per-version properties (versions/<mc>/gradle.properties) live on the root node.
val rootNode = stonecutter.node.sibling("")!!.project
val javaVersion = rootNode.property("java.version").toString().toInt()

group = property("mod.group").toString()
version = "${property("mod.version")}+${stonecutter.current.version}"

base {
    archivesName = "SafeZone-Fabric"
}

stonecutter {
    // ContainerInput was named ClickType before 26.1 (same package, straight rename) —
    // an identifier swap, so it's handled as a replacement instead of //? comment blocks.
    replacements.string(stonecutter.current.parsed < "26.1") {
        replace("ContainerInput", "ClickType")
    }
}

// The shared `common` module is pure Java (no Minecraft/version-specific code), so we
// compile its source straight into the loader jar instead of taking a project dependency
// (loom + sibling-project deps produce a compileJava cycle). The `common` branch still
// builds and runs its own unit tests independently. NOTE: this source is NOT processed
// by Stonecutter — common must stay free of //? comments and replacement targets.
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
    implementation("com.google.code.gson:gson:${property("deps.gson")}")
    implementation("org.slf4j:slf4j-api:${property("deps.slf4j")}")

    testImplementation(platform("org.junit:junit-bom:${property("deps.junit_bom")}"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

fabricApi {
    configureDataGeneration()
    // In-game tests (vanilla GameTest framework). Run on demand per version with
    // `:fabric:<mc>:runGameTest`; deliberately NOT attached to `build`/CI — see below.
    configureTests {
        createSourceSet = true
        modId = "safe-zone-test"
        enableGameTests = true
        eula = true
    }
}

loom {
    mods {
        // Explicit dev-mod registration: without this, runClient/runServer load
        // no mod at all. (configureTests above already registers `safe-zone-test`,
        // which also makes the suite available to vanilla `/test` in dev runs.)
        create("safe-zone") {
            sourceSet(sourceSets.main.get())
        }
    }

    runConfigs.configureEach {
        // Shared between versions — only one version's server/client runs at a time.
        runDirectory.set(rootProject.file("run/fabric"))
    }

    runs {
        // Interactive dedicated server WITH the safe-zone-test mod loaded, for
        // watching tests execute live via vanilla `/test` commands. (Plain runServer
        // omits the test mod; runGameTest is headless and exits after the suite.
        // Singleplayer can never see the tests — the mod is environment:server.)
        create("testServer") {
            server()
            configName = "Test Server (interactive /test)"
            source(sourceSets["gametest"])
        }
    }
}

tasks.processResources {
    val expansions = mapOf(
        "version" to project.version.toString(),
        "minecraft" to rootNode.property("minecraft.dependency").toString(),
        "fabric_loader" to rootNode.property("fabric.loader.version").toString(),
        "fabric_api" to rootNode.property("fabric.api.version").toString(),
        "java_min" to javaVersion.toString(),
    )
    inputs.properties(expansions)
    filesMatching("fabric.mod.json") { expand(expansions) }

    val mixinJava = "JAVA_$javaVersion"
    inputs.property("mixinJava", mixinJava)
    filesMatching("*.mixins.json") { expand("java" to mixinJava) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

// Keep in-game tests on-demand (they boot a whole server): run them explicitly with
// `:fabric:<mc>:runGameTest` instead of as part of every `build`/`check`.
tasks.named("check") {
    setDependsOn(dependsOn.filterNot { it.toString().contains("runGameTest", ignoreCase = true) })
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    withSourcesJar()
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

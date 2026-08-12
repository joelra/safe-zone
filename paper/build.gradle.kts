import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("java")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

// Per-version properties (versions/<mc>/gradle.properties) live on the root node.
val rootNode = stonecutter.node.sibling("")!!.project
val javaVersion = rootNode.property("java.version").toString().toInt()

group = property("mod.group").toString()
version = "${property("mod.version")}+${stonecutter.current.version}"

base {
    archivesName = "SafeZone-Paper"
}

val paperApi = "io.papermc.paper:paper-api:${rootNode.property("paper.api.version")}"

sourceSets {
    val axiomStubs by creating {
        java { srcDir("src/axiomStubs/java") }
    }
    named("main") {
        compileClasspath += axiomStubs.output
        // The shared `common` module is pure Java; compile its source straight into the
        // plugin jar instead of taking a sibling-project dependency (which produces a
        // task cycle under Stonecutter's node layout). The `common` branch still runs
        // its own unit tests independently.
        java.srcDir(rootProject.file("common/src/main/java"))
    }
}

configurations.all {
    // WorldEdit declares {strictly} version constraints for several Mojang-bundled libraries.
    // These conflict with the versions that Paper API (and the common module) require at
    // build time. Since worldedit-bukkit is compile-only, override the strict constraints
    // so the two compile-only dependencies can coexist during compilation.
    resolutionStrategy.eachDependency {
        if (requested.group == "org.apache.logging.log4j" && requested.name == "log4j-bom") {
            useVersion("2.24.1")
            because("WorldEdit {strictly 2.22.1} conflicts with FAWE 2.24.1; overridden for compile-only use — Paper provides Log4j at runtime")
        } else if (requested.group == "it.unimi.dsi" && requested.name == "fastutil") {
            useVersion("8.5.15")
            because("Paper API requires fastutil 8.5.15; WorldEdit/FAWE strict constraint overridden for compile-only use")
        } else if (requested.group == "com.google.guava" && requested.name == "guava") {
            useVersion("33.3.1-jre")
            because("Paper API requires guava 33.3.1-jre; WorldEdit/FAWE strict constraint overridden for compile-only use")
        } else if (requested.group == "com.google.code.gson" && requested.name == "gson") {
            useVersion(property("deps.gson").toString())
            because("common module requires gson ${property("deps.gson")}; WorldEdit/FAWE strict constraint overridden for compile-only use")
        }
    }
}

dependencies {
    // Transitive dependencies of the common module (Paper bundles gson/slf4j at runtime).
    implementation("com.google.code.gson:gson:${property("deps.gson")}")
    implementation("org.slf4j:slf4j-api:${property("deps.slf4j")}")
    compileOnly(paperApi)
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.9")
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Bukkit:2.15.0")
    // axiomStubs only needs Paper API — avoid pulling in WorldEdit's strict
    // fastutil version constraint which conflicts with Paper's requirement.
    "axiomStubsCompileOnly"(paperApi)
}

tasks.processResources {
    val expansions = mapOf(
        "version" to project.version.toString(),
        "apiVersion" to rootNode.property("paper.api_level").toString(),
    )
    inputs.properties(expansions)
    filesMatching("plugin.yml") { expand(expansions) }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = javaVersion
    options.compilerArgs.add("-Xlint:deprecation")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(javaVersion)
    }
    withSourcesJar()
}

tasks.named<RunServer>("runServer") {
    minecraftVersion(stonecutter.current.version)
    runDirectory.set(rootProject.layout.projectDirectory.dir("run/paper"))
    downloadPlugins {
        // Axiom Paper Plugin (per-version Modrinth build).
        modrinth("axiom-paper-plugin", rootNode.property("axiom.version_id").toString())
        // FastAsyncWorldEdit — no 26.2 build released yet; add a github(...) entry when available.
        // LuckPerms — for testing per-player permission scenarios (e.g. safezone.axiom without safezone.command.admin).
        modrinth("luckperms", "OrIs0S6b")
    }
}

tasks.register<RunServer>("runServerCore") {
    minecraftVersion(stonecutter.current.version)
    runDirectory.set(rootProject.layout.projectDirectory.dir("run/paper-core"))
    pluginJars(tasks.named<Jar>("jar").flatMap { it.archiveFile })
}

tasks.jar {
    from(rootProject.file("LICENSE")) {
        rename { "${it}_SafeZone-Paper" }
    }
}

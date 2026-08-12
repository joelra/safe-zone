plugins {
    id("dev.kikugie.stonecutter")
}

// The Minecraft version authored against / active by default.
// `./gradlew build` builds every version+branch; build a single node with
// e.g. `./gradlew ":fabric:26.2:build"` or `./gradlew ":paper:26.2:build"`.
// Switch the working tree with `./gradlew "Set active project to <version>"`.
stonecutter active "26.2" /* [SC] DO NOT EDIT */

tasks.register<Delete>("cleanRunFabric") {
    group = "build"
    description = "Delete the Fabric runtime directory"
    delete(layout.projectDirectory.dir("run/fabric"))
}

tasks.register<Delete>("cleanRunPaper") {
    group = "build"
    description = "Delete the Paper runtime directories"
    delete(layout.projectDirectory.dir("run/paper"))
    delete(layout.projectDirectory.dir("run/paper-core"))
}

tasks.register("cleanRun") {
    group = "build"
    description = "Delete all local runtime directories"
    dependsOn("cleanRunFabric", "cleanRunPaper")
}

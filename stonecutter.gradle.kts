plugins {
    id("dev.kikugie.stonecutter")
}

// The Minecraft version authored against / active by default.
// `./gradlew build` builds every version+branch; build a single node with
// e.g. `./gradlew ":fabric:26.2:build"` or `./gradlew ":paper:26.2:build"`.
// Switch the working tree with `./gradlew "Set active project to <version>"`.
stonecutter active "26.2" /* [SC] DO NOT EDIT */

// Convenience run tasks that dispatch to whichever version is currently active,
// so you don't have to spell out node paths like :fabric:26.2:runServer.
for (node in stonecutter.tree.nodes) {
    if (node.metadata != stonecutter.current) continue
    when (node.branch.id) {
        "fabric" -> mapOf(
            "runActiveFabricServer" to "runServer",
            "runActiveFabricClient" to "runClient",
            "runActiveFabricDatagen" to "runDatagen",
        )
        "paper" -> mapOf("runActivePaperServer" to "runServer")
        else -> emptyMap()
    }.forEach { (alias, target) ->
        node.project.tasks.register(alias) {
            group = node.branch.id
            description = "Runs $target for the active version (${node.metadata.version})"
            dependsOn(target)
        }
    }
}

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

# CLAUDE.md

## Build and run

Use the Gradle wrapper from the repo root.

- `.\gradlew.bat build` — builds and tests **every** Minecraft version for both loaders
- `.\gradlew.bat check`
- `.\gradlew.bat test`

Run tasks — `runActive*` aliases target the active version; node paths address a specific one (swap `26.2` for `26.1.2` or `1.21.11`):

- `.\gradlew.bat runActiveFabricServer` (or `":fabric:26.2:runServer"`)
- `.\gradlew.bat runActiveFabricClient` (or `":fabric:26.2:runClient"`)
- `.\gradlew.bat runActiveFabricDatagen` (or `":fabric:26.2:runDatagen"`)
- `.\gradlew.bat runActivePaperServer` (or `":paper:26.2:runServer"`)

Runtime-directory cleanup:

- `.\gradlew.bat cleanRunFabric`
- `.\gradlew.bat cleanRunPaper`
- `.\gradlew.bat cleanRun`

CI installs Java 21 and 25 and runs `build`; the Gradle toolchain picks Java 25 for 26.1+ and Java 21 for 1.21.11. There is no dedicated lint or formatting task.

## Multi-version (Stonecutter)

This is a **multi-version** project built with [Stonecutter](https://stonecutter.kikugie.dev/) 0.9 + `loom-back-compat`. One source tree targets Minecraft `1.21.11`, `26.1.2`, and `26.2`.

- Supported versions and the branch layout (`common`, `fabric`, `paper` branches) are declared in `settings.gradle.kts`.
- Shared properties live in `gradle.properties`; per-version coordinates in `versions\<mc>\gradle.properties` (read from a branch via `stonecutter.node.sibling("").project.property(...)`).
- Version-specific source uses two mechanisms: straight identifier renames are `replacements.string(...)` in the branch build script (e.g. `ContainerInput` ↔ `ClickType` in `fabric/build.gradle.kts`); structural differences are `//? if <predicate> { ... //?} else { ... }` comment blocks (e.g. `ColorCollection` vs named stained-glass fields).
- The `common` module is compiled raw into the loader jars, bypassing Stonecutter processing — **never** put `//?` comments or replacement-target identifiers in `common` source.
- `26.2` is the `vcsVersion`. **Run `.\gradlew.bat "Reset active project"` before committing** so the shared source is in the canonical state — CI enforces this (resets, then fails on any diff).
- The `common` module is pure Java (no Minecraft imports); its source is compiled directly into the loader jars, since a sibling-project dependency triggers a loom/Stonecutter task cycle.
- Build scripts are Kotlin DSL (`*.build.gradle.kts`); generated `*/versions/` node dirs are gitignored (the authored root `versions/` is tracked).

## Project shape

Safe Zone is a **server-side** land-claim project for **Minecraft 1.21.11, 26.1, and 26.2** with:

- `common` for shared models, persistence helpers, and shared services
- `fabric` for the Fabric mod runtime
- `paper` for the Paper plugin runtime

`README.md` is the user-facing behavior reference. Keep it aligned with shipped functionality, not older plans or speculative features.

## Architecture guidance

Prefer the current **shared core, thin adapters** pattern.

- Put shared business logic in `common\src\main\java\com\simpleforapanda\safezone\`
- Keep loader bootstrap, commands, listeners, visualization, and platform APIs in `fabric\` or `paper\`
- Reuse existing shared services before adding platform-specific duplicates

Important shared-core pieces:

- `port\PathLayoutPort`
- `manager\CommonClaimService`
- `manager\CommonNotificationService`
- `manager\CommonAuditService`

Important runtime composition pieces:

- Fabric: `runtime\FabricRuntime`, `runtime\FabricServices`
- Paper: `paper\runtime\PaperRuntime`

Fabric now has runtime/service composition similar to Paper. Prefer injected services and runtime-owned wiring for new work instead of introducing new singleton-heavy flows. If a Fabric mixin or static callback truly needs a bridge, keep it minimal and route back into runtime-owned services.

## Important code areas

- `common\src\main\java\com\simpleforapanda\safezone\` — shared models, rules, ports, and services
- `fabric\src\main\java\com\simpleforapanda\safezone\SafeZone.java` — Fabric entrypoint
- `fabric\src\main\java\com\simpleforapanda\safezone\runtime\` — Fabric runtime and service container
- `fabric\src\main\java\com\simpleforapanda\safezone\command\` — Fabric `/claim` and `/sz`
- `fabric\src\main\java\com\simpleforapanda\safezone\listener\ProtectionListener.java` — Fabric protections
- `paper\src\main\java\com\simpleforapanda\safezone\paper\SafeZonePaperPlugin.java` — Paper entrypoint
- `paper\src\main\java\com\simpleforapanda\safezone\paper\runtime\` — Paper runtime, path layout, config, and stores
- `paper\src\main\java\com\simpleforapanda\safezone\paper\listener\PaperClaimVisualizationService.java` — Paper claim preview and visualization

## Guardrails

- For Fabric questions, prefer the versioned docs for this target: `https://docs.fabricmc.net/26.2/develop/`
- Use **Mojang Official Mappings**
- Mod ID: `safe-zone`
- Package root: `com.simpleforapanda.safezone`
- Keep the gameplay flow server-compatible; do not require a custom client mod for core use
- The wand is a configurable vanilla item from `config.json`; default is `minecraft:golden_hoe`
- Wand interactions must suppress normal vanilla behavior when acting as the claim wand
- Players can receive the wand once on first join when starter-kit grants are enabled
- Claims are **Overworld-only**, **full-height columns**, default **64x64 max**, default **3 claims per player**
- Persist UUIDs as **strings**
- Fabric data/config lives under `<world>\safe-zone\`
- Paper config lives in `plugins\SafeZone\config.json`, runtime JSON in `plugins\SafeZone\data\`, and logs in `plugins\SafeZone\logs\`
- Build from the repo root; runtime jars come from `fabric\versions\<mc>\build\libs\` and `paper\versions\<mc>\build\libs\`
- Keep install paths straight in docs: Fabric uses `mods\`, Paper uses `plugins\`
- Follow the multi-version branch layout from `settings.gradle.kts`; do not describe this as a Fabric-only or single-version project
- Do not document protections or automation that are not implemented

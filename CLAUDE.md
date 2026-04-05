# CLAUDE.md

## Build and run

Use the Gradle wrapper from the repo root.

- `.\gradlew.bat build`
- `.\gradlew.bat check`
- `.\gradlew.bat test`
- `.\gradlew.bat runServer`
- `.\gradlew.bat runClient`
- `.\gradlew.bat runDatagen`
- `.\gradlew.bat runPaperServer`
- `.\gradlew.bat cleanRunFabric`
- `.\gradlew.bat cleanRunPaper`
- `.\gradlew.bat cleanRun`

Module-local tasks also exist:

- `.\gradlew.bat :fabric:runServer`
- `.\gradlew.bat :fabric:runClient`
- `.\gradlew.bat :fabric:runDatagen`
- `.\gradlew.bat :paper:runServer`

CI runs `build` on Java 25. The project targets Java 21. There is no dedicated lint or formatting task.

## Project shape

Safe Zone is a **server-side** land-claim project for **Minecraft 1.21.11** with:

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

- For Fabric questions, prefer the versioned docs for this target: `https://docs.fabricmc.net/1.21.11/develop/`
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
- Build from the repo root; runtime jars come from `fabric\build\libs\` and `paper\build\libs\`
- Keep install paths straight in docs: Fabric uses `mods\`, Paper uses `plugins\`
- Follow the multi-project layout from `settings.gradle`; do not describe this as a Fabric-only project
- Do not document protections or automation that are not implemented

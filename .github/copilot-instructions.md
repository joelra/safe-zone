# Copilot Instructions for `safe-zone`

## Build, run, and cleaning commands

Use the Gradle wrapper from the repository root.

- `.\gradlew.bat build` — builds and tests every Minecraft version for both loaders
- `.\gradlew.bat check`
- `.\gradlew.bat test`

Run tasks are per Minecraft version (Stonecutter node paths — swap `26.2` for `26.1.2` or `1.21.11`):

- `.\gradlew.bat ":fabric:26.2:runServer"`
- `.\gradlew.bat ":fabric:26.2:runClient"`
- `.\gradlew.bat ":fabric:26.2:runDatagen"`
- `.\gradlew.bat ":paper:26.2:runServer"`

This is a **multi-version** project built with [Stonecutter](https://stonecutter.kikugie.dev/) 0.9 + `loom-back-compat`, targeting Minecraft `1.21.11`, `26.1.2`, and `26.2` from one source tree. Supported versions live in `settings.gradle.kts`; per-version coordinates in `versions\<mc>\gradle.properties`; version-specific source is guarded with Stonecutter `//?` comments. `26.2` is the `vcsVersion` — run `.\gradlew.bat "Set active project to 26.2"` before committing. There is no dedicated lint or format task. CI installs Java 21 and 25 and runs `build`; the toolchain picks Java 25 for 26.1+ and Java 21 for 1.21.11.

## Repository focus

This repo is a **server-side** land-claim project for **Minecraft 1.21.11, 26.1, and 26.2** with:

- `common` shared code
- `fabric` runtime
- `paper` runtime

`README.md` is the user-facing source of truth for shipped behavior. Keep agent guidance aligned with the code and README, not outdated plans.

## Architecture best practices

Prefer the current **ports-and-adapters** direction:

- Put shared business rules and persistence behavior in `common`
- Keep loader bootstraps, listeners, commands, visualization, and platform APIs in `fabric` or `paper`
- Reuse shared services before introducing parallel Fabric/Paper implementations

Current shared services and contracts:

- `port\PathLayoutPort`
- `manager\CommonClaimService`
- `manager\CommonNotificationService`
- `manager\CommonAuditService`

Current runtime composition:

- Fabric: `runtime\FabricRuntime`, `runtime\FabricServices`
- Paper: `paper\runtime\PaperRuntime`

When adding new behavior:

- Prefer runtime-owned service wiring over new global singletons
- Keep any static bridge limited to mixin or callback boundaries that cannot be instance-wired cleanly
- Avoid abstracting platform player/world/event APIs into artificial cross-loader wrappers unless there is a real reuse payoff

## Important code areas

- `common\src\main\java\com\simpleforapanda\safezone\` — shared models, ports, and services
- `fabric\src\main\java\com\simpleforapanda\safezone\SafeZone.java` — Fabric bootstrap
- `fabric\src\main\java\com\simpleforapanda\safezone\runtime\` — Fabric runtime and services
- `fabric\src\main\java\com\simpleforapanda\safezone\command\` — Fabric `/claim` and `/sz`
- `fabric\src\main\java\com\simpleforapanda\safezone\listener\ProtectionListener.java` — Fabric protections
- `paper\src\main\java\com\simpleforapanda\safezone\paper\SafeZonePaperPlugin.java` — Paper bootstrap
- `paper\src\main\java\com\simpleforapanda\safezone\paper\runtime\` — Paper runtime, config, stores, and path layout
- `paper\src\main\java\com\simpleforapanda\safezone\paper\listener\PaperClaimVisualizationService.java` — Paper claim preview and visualization

Persistence is platform-specific:

- Fabric: `<world>\safe-zone\`
- Paper: `plugins\SafeZone\`, with runtime data in `plugins\SafeZone\data\` and logs in `plugins\SafeZone\logs\`

## Key conventions

- For Fabric implementation questions, prefer the versioned docs for this target: `https://docs.fabricmc.net/26.2/develop/`
- Use **Mojang official mappings**, not Yarn
- Mod ID/resource namespace: `safe-zone`
- Java package root: `com.simpleforapanda.safezone`
- Keep the core flow server-compatible; do not require a custom client mod for normal use
- The wand is a configurable vanilla item from `config.json`; default is **Golden Hoe**
- Wand interactions must suppress normal vanilla behavior while claim logic is active
- Players receive the configured wand once on first join when starter-kit grants are enabled
- Claims are **Overworld-only**, **full-height columns**, default **64x64 max**, default **3 claims per player**
- Key gameplay config fields: `claimWandItemId`, `starterKitEnabled`, `dropStarterKitWhenInventoryFull`, `defaultMaxClaims`, `maxClaimWidth`, `maxClaimDepth`, `claimGapEnforced`, `claimGapMinDistance`, `claimExpiryDays`, `notificationsEnabled`, `notificationRetentionDays`, `wandRemoveConfirmSeconds`, `commandRemoveConfirmSeconds`
- Persist UUIDs as **strings**
- Build outputs come from `fabric\versions\<mc>\build\libs\` and `paper\versions\<mc>\build\libs\` (one jar per Minecraft version); release automation should target runtime jars unless explicitly asked otherwise
- Keep install paths straight in docs: Fabric jars go in `mods\`, Paper jars go in `plugins\`
- Follow the multi-project structure from `settings.gradle`; do not describe the repo as Fabric-only
- Document only protections, commands, and automation that are actually implemented

# Copilot Instructions for `safe-zone`

## Build commands

Use the Gradle wrapper from the repository root.

- `.\gradlew.bat build`
- `.\gradlew.bat check`
- `.\gradlew.bat test`
- `.\gradlew.bat runServer`

There is no dedicated lint/format task. CI runs `build` on Java 25; the project targets Java 21.

## Current implementation focus

This repo is a **server-only** Fabric land-claim mod for Minecraft 1.21.11. `README.md` is the current behavior reference; keep AI guidance aligned with the code and README.

- `src\main\java\com\simpleforapanda\safezone\SafeZone.java` boots the mod and starter-kit flow
- `...\command\PlayerCommand.java` handles `/claim` and `/claims`
- `...\command\AdminCommand.java` handles `/sz`, `/safezone`, and inspect mode
- `...\item\ClaimWandHandler.java` and `...\item\ModItems.java` implement the configured claim-wand rules
- `...\listener\ProtectionListener.java` contains the protection hooks
- `...\manager\ClaimManager.java` owns claim persistence and limits
- `...\screen\TrustMenu.java` is the vanilla trust UI

Persistence uses plain JSON/log files under `<world>/safe-zone/`.

## Key conventions

- For Fabric implementation questions, prefer the versioned docs for this target: `https://docs.fabricmc.net/1.21.11/develop/`.
- Use **Mojang official mappings**, not Yarn.
- Keep identifiers consistent with the repository: mod ID/resource namespace is `safe-zone`, and the Java package root is `com.simpleforapanda.safezone`.
- The default claim wand is a **vanilla Golden Hoe** detected through interaction hooks, but the actual wand item comes from the `gameplay` section of `config.json`, not a registered custom item.
- Players receive the configured claim wand **once on first join** as a starter kit when starter-kit grants are enabled.
- Wand handling must suppress normal vanilla item behavior while performing claim actions.
- `/sz inspect` is the current admin inspection workflow; it uses **Shift + right-click with an empty hand**.
- Claims are **Overworld-only**, **full-height columns**, default to **64x64 max**, and default to **3 claims per player**.
- Gameplay tuning is stored in `<world>/safe-zone/config.json` under the `gameplay` section.
- Key gameplay config fields are `claimWandItemId`, `starterKitEnabled`, `dropStarterKitWhenInventoryFull`, `defaultMaxClaims`, `maxClaimWidth`, `maxClaimDepth`, `claimGapEnforced`, `claimGapMinDistance`, `claimExpiryDays`, `notificationsEnabled`, `wandRemoveConfirmSeconds`, and `commandRemoveConfirmSeconds`.
- Persist UUIDs as **strings** in saved data.
- There is currently **no `src\client` source set**.
- Document only the protections and automation that are actually implemented.
